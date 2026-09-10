package com.github.rek655869.notifycommander.features.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.rek655869.notifycommander.dispatcher.Event;
import com.github.rek655869.notifycommander.dispatcher.EventDispatcher;

@ExtendWith(MockitoExtension.class)
class EventQueueProcessorTest {

    @Mock
    private EventDispatcher dispatcher;

    @InjectMocks
    private EventQueueProcessor eventQueueProcessor;

    @Nested
    @DisplayName("Метод publish(EventWrapper event)")
    class PublishTest {
        @Test
        @DisplayName("Должен успешно добавлять событие в очередь и возвращать true")
        void shouldSuccessfullyAddEventToQueue() {
            Event event = mock(Event.class);

            boolean result = eventQueueProcessor.publish(event);

            assertThat(result).isTrue();

            Object queue = ReflectionTestUtils.getField(eventQueueProcessor, "queue");

            assertThat(queue)
                    .asInstanceOf(COLLECTION)
                    .extracting("event")
                    .containsExactly(event);
        }

        @Test
        @DisplayName("Должен возвращать false и не переполнять очередь при достижении лимита в 100 элементов")
        void shouldReturnFalseWhenQueueIsFull() {
            for (int i = 0; i < 100; i++) {
                eventQueueProcessor.publish(mock(Event.class));
            }
            Event extraEvent = mock(Event.class);

            boolean result = eventQueueProcessor.publish(extraEvent);

            assertThat(result).isFalse();

            Object queue = ReflectionTestUtils.getField(eventQueueProcessor, "queue");

            assertThat(queue)
                    .asInstanceOf(COLLECTION)
                    .hasSize(100)
                    .doesNotContain(extraEvent);
        }
    }

    @Test
    @DisplayName("Должен успешного извлекать событие из очереди и передавать его в EventDispatcher")
    void shouldExtractEventFromQueueAndPassToDispatcher() {
        Event event = mock(Event.class);

        eventQueueProcessor.process();

        eventQueueProcessor.publish(event);

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(dispatcher).handle(event));
    }

    @Nested
    @DisplayName("Обработка ошибок и механизм Retries")
    class RetryLogicTest {

        @BeforeEach
        void setUp() {
            eventQueueProcessor.process();
        }

        @AfterEach
        void tearDown() {
            eventQueueProcessor.stopProcess();
        }

        @Test
        @DisplayName("Должен выполнять повторную попытку при ошибке, увеличивая счетчик retries и соблюдая паузу")
        void shouldRetryOnFailureWithPauseAndIncrementRetries() {
            Event event = mock(Event.class);

            doThrow(new RuntimeException("Temporary failure"))
                    .doNothing()
                    .when(dispatcher).handle(event);

            long startTime = System.currentTimeMillis();

            eventQueueProcessor.publish(event);

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> verify(dispatcher, times(1)).handle(event));

            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> {
                        verify(dispatcher, times(2)).handle(event);
                    });

            long elapsedTime = System.currentTimeMillis() - startTime;
            assertThat(elapsedTime)
                    .as("Пауза между попытками должна составлять ~1000 мс, но прошло всего %d мс", elapsedTime)
                    .isGreaterThanOrEqualTo(900L);
        }

        @Test
        @DisplayName("Должен прекращать попытки и не возвращать событие в очередь при превышении MAX_RETRIES")
        void shouldStopRetryingWhenMaxRetriesExceeded() {
            Event event = mock(Event.class);

            doThrow(new RuntimeException("Simulated error"))
                    .when(dispatcher).handle(event);

            eventQueueProcessor.publish(event);

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        verify(dispatcher, times(3)).handle(event);
                    });
        }

        @Test
        @DisplayName("Должен восстанавливать статус прерывания потока при InterruptedException во время паузы перед retry")
        void shouldRestoreInterruptStatusWhenInterruptedDuringRetrySleep() throws InterruptedException {
            Event event = mock(Event.class);

            CountDownLatch inSleepLatch = new CountDownLatch(1);
            AtomicReference<Thread> virtualThreadRef = new AtomicReference<>();

            doAnswer(invocation -> {
                // Запоминаем виртуальный поток, выполняющий обработку
                virtualThreadRef.set(Thread.currentThread());
                inSleepLatch.countDown();
                throw new RuntimeException("Simulated error");
            }).when(dispatcher).handle(event);

            eventQueueProcessor.publish(event);

            // Ждём, пока событие начнет обрабатываться и упадет в ошибку
            assertThat(inSleepLatch.await(2, TimeUnit.SECONDS)).isTrue();

            Thread.sleep(100);

            Thread virtualThread = virtualThreadRef.get();
            assertThat(virtualThread).isNotNull();

            // Прерываем именно тот виртуальный поток, который ушел в sleep
            virtualThread.interrupt();

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> {
                        verify(dispatcher, times(1)).handle(event);
                    });
        }
    }

    @Nested
    @DisplayName("Жизненный цикл и завершение работы (@PreDestroy)")
    class LifecycleAndShutdownTest {

        @BeforeEach
        void setUp() {
            eventQueueProcessor.process();
        }

        @Test
        @DisplayName("Должен корректно останавливать worker-поток и менять флаг running при вызове stopProcess()")
        void shouldStopWorkerThreadAndChangeRunningFlagOnStopProcess() {

            Thread workerThread = (Thread) ReflectionTestUtils.getField(eventQueueProcessor, "workerThread");
            assertThat(workerThread).isNotNull();
            assertThat(workerThread.isAlive()).isTrue();

            eventQueueProcessor.stopProcess();

            Boolean running = (Boolean) ReflectionTestUtils.getField(eventQueueProcessor, "running");
            assertThat(running).isFalse();

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(workerThread.getState()).isEqualTo(Thread.State.TERMINATED));
        }

        @Test
        @DisplayName("Должен прекращать обработку новых элементов из очереди после вызова stopProcess()")
        void shouldNotProcessNewEventsAfterStopProcess() {
            Event event = mock(Event.class);

            Thread workerThread = (Thread) ReflectionTestUtils.getField(eventQueueProcessor, "workerThread");

            eventQueueProcessor.stopProcess();

            if (workerThread != null) {
                await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(workerThread.getState()).isEqualTo(Thread.State.TERMINATED));
            }

            eventQueueProcessor.publish(event);

            await().during(Duration.ofMillis(500))
                    .atMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> verify(dispatcher, never()).handle(event));
        }

        @Test
        @DisplayName("Должен завершать цикл while в process() при установке running = false")
        void shouldExitProcessLoopWhenRunningIsFalse() {
            Thread workerThread = (Thread) ReflectionTestUtils.getField(eventQueueProcessor, "workerThread");
            assertThat(workerThread).isNotNull();
            assertThat(workerThread.isAlive()).isTrue();

            ReflectionTestUtils.setField(eventQueueProcessor, "running", false);

            eventQueueProcessor.publish(mock(Event.class));

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(workerThread.getState()).isEqualTo(Thread.State.TERMINATED));
        }

        @Test
        @DisplayName("Должен обрабатывать InterruptedException и выходить из цикла при прерывании потока")
        void shouldExitProcessLoopOnInterruptedException() {
            Thread workerThread = (Thread) ReflectionTestUtils.getField(eventQueueProcessor, "workerThread");
            assertThat(workerThread).isNotNull();

            workerThread.interrupt();

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(workerThread.getState()).isEqualTo(Thread.State.TERMINATED));
        }
    }

    @Nested
    @DisplayName("Проверка интеграции Spring (app.dispatcher.type)")
    class SpringIntegrationTest {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(EventDispatcher.class, () -> mock(EventDispatcher.class))
                .withConfiguration(AutoConfigurations.of(EventQueueProcessor.class));

        @Test
        @DisplayName("Должен создавать бин EventQueueProcessor при значении app.dispatcher.type=memory или без него")
        void shouldCreateBeanWhenPropertyIsMemoryOrMissing() {
            // Без свойства (matchIfMissing = true)
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(EventQueueProcessor.class);
            });

            // С явным выполнением условия
            contextRunner.withPropertyValues("app.dispatcher.type=memory")
                    .run(context -> {
                        assertThat(context).hasSingleBean(EventQueueProcessor.class);
                    });
        }

        @Test
        @DisplayName("Не должен создавать бин EventQueueProcessor при значении app.dispatcher.type, отличном от memory")
        void shouldNotCreateBeanWhenPropertyIsOtherType() {
            contextRunner.withPropertyValues("app.dispatcher.type=kafka")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(EventQueueProcessor.class);
                    });
        }
    }
}