package com.github.rek655869.notifycommander.features.events.in_memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

@ExtendWith(MockitoExtension.class)
public class InMemoryEventConsumerTest {

    @Mock
    private InMemoryEventQueue queue;

    @Mock
    private EventExecutionService executionService;

    @Mock
    private EventWrapper eventWrapper1;

    @Mock
    private EventWrapper eventWrapper2;

    private InMemoryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InMemoryEventConsumer(queue, executionService);
    }

    @AfterEach
    void tearDown() {
        consumer.stop();
    }

    // Вспомогательный метод для получения приватного поля workerThread через
    // Reflection
    private Thread getWorkerThread(InMemoryEventConsumer consumer) throws Exception {
        Field field = InMemoryEventConsumer.class.getDeclaredField("workerThread");
        field.setAccessible(true);
        return (Thread) field.get(consumer);
    }

    @Test
    @DisplayName("Должен успешно запускать рабочий поток при старте")
    void shouldStartWorkerThreadOnStart() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return null;
        }).when(queue).take();

        consumer.start();

        Thread workerThread = getWorkerThread(consumer);

        assertThat(workerThread).isNotNull();
        assertThat(workerThread.getName()).isEqualTo("event-queue-loop");
        assertThat(workerThread.isAlive()).isTrue();
    }

    @Nested
    @DisplayName("Тестирование основного цикла обработки событий")
    class LifecycleTests {
        @Test
        @DisplayName("Должен извлекать событие и передавать его в виртуальном потоке")
        void shouldTakeEventAndExecuteInVirtualThread() throws Exception {
            AtomicBoolean isVirtual = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            doAnswer(invocation -> {
                isVirtual.set(Thread.currentThread().isVirtual());
                latch.countDown();
                return null;
            }).when(executionService).executeWithRetry(eventWrapper1);

            doAnswer(invocation -> eventWrapper1)
                    .doAnswer(invocation -> {
                        Thread.sleep(1000);
                        return null;
                    }).when(queue).take();

            consumer.start();

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            verify(executionService, timeout(1000).times(1))
                    .executeWithRetry(eventWrapper1);
            assertThat(isVirtual.get()).isTrue();
        }

        @Test
        @DisplayName("Должен последовательно обрабатывать N событий из очереди")
        void shouldProcessSequenceOfEvents() throws Exception {
            CountDownLatch latch = new CountDownLatch(2);

            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(executionService).executeWithRetry(any());

            doAnswer(invocation -> eventWrapper1)
                    .doAnswer(invocation -> eventWrapper2)
                    .doAnswer(invocation -> {
                        Thread.sleep(1000);
                        return null;
                    }).when(queue).take();

            consumer.start();

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            verify(executionService, timeout(1000)).executeWithRetry(eventWrapper1);
            verify(executionService, timeout(1000)).executeWithRetry(eventWrapper2);
        }

        @Test
        @DisplayName("Должен продолжать работу, если queue.take вернул null")
        void shouldContinueLoopIfQueueReturnsNull() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);

            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(executionService).executeWithRetry(eventWrapper1);

            doAnswer(invocation -> null)
                    .doAnswer(invocation -> eventWrapper1)
                    .doAnswer(invocation -> {
                        Thread.sleep(1000);
                        return null;
                    }).when(queue).take();

            consumer.start();

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            verify(executionService, timeout(1000)).executeWithRetry(eventWrapper1);

            Thread workerThread = getWorkerThread(consumer);
            assertThat(workerThread.isAlive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Тестирование обработки ошибок и исключений")
    class ErrorTests {
        @Test
        @DisplayName("Должен продолжать главный цикл, если executionService выбросил RuntimeException")
        void shouldKeepLoopRunningIfExecutionServiceFails() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);

            // Первая задача падает с ошибкой, вторая выполняется успешно
            doThrow(new RuntimeException("Ошибка выполнения"))
                    .doAnswer(invocation -> {
                        latch.countDown();
                        return null;
                    }).when(executionService).executeWithRetry(any());

            doAnswer(invocation -> eventWrapper1)
                    .doAnswer(invocation -> eventWrapper2)
                    .doAnswer(invocation -> {
                        Thread.sleep(1000);
                        return null;
                    }).when(queue).take();

            consumer.start();

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            verify(executionService, timeout(1000)).executeWithRetry(eventWrapper1);
            verify(executionService, timeout(1000)).executeWithRetry(eventWrapper2);

            Thread workerThread = getWorkerThread(consumer);
            assertThat(workerThread.isAlive()).isTrue();
        }

        @Test
        @DisplayName("Должен продолжать главный цикл при необрабатываемом RuntimeException из queue.take")
        void shouldContinueLoopOnRuntimeExceptionFromQueue() throws Exception {
            EventWrapper mockEvent = mock(EventWrapper.class);

            doThrow(new IllegalStateException("Фатальный сбой очереди"))
                    .doReturn(mockEvent)
                    .when(queue).take();

            consumer.start();

            Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> verify(executionService, atLeastOnce()).executeWithRetry(mockEvent));

            Thread workerThread = getWorkerThread(consumer);
            assertThat(workerThread).isNotNull();
            assertThat(workerThread.isAlive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Тестирование завершения работы")
    class DestroyTests {
        @Test
        @DisplayName("Должен корректно останавливать поток при заблокированном take")
        void shouldStopGracefullyWhenBlockedOnTake() throws Exception {
            CountDownLatch takeStartedLatch = new CountDownLatch(1);

            doAnswer(invocation -> {
                takeStartedLatch.countDown();
                Thread.sleep(10000); // Симулируем долгую блокировку
                return null;
            }).when(queue).take();

            consumer.start();
            assertThat(takeStartedLatch.await(2, TimeUnit.SECONDS)).isTrue();

            consumer.stop();

            Thread workerThread = getWorkerThread(consumer);
            Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .until(() -> !workerThread.isAlive());

            assertThat(workerThread.isAlive()).isFalse();
        }

        @Test
        @DisplayName("Должен успешно вызывать stop до вызова start без исключений")
        void shouldNotThrowExceptionWhenStopCalledBeforeStart() {
            assertThatCode(() -> consumer.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Должен корректно обрабатывать stop, если рабочий поток уже завершился")
        void shouldHandleStopWhenWorkerThreadIsAlreadyDead() throws Exception {
            doThrow(new InterruptedException()).when(queue).take();

            consumer.start();

            Thread workerThread = getWorkerThread(consumer);
            Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .until(() -> !workerThread.isAlive());

            assertThatCode(() -> consumer.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Должен завершать цикл при прямом прерывании потока извне")
        void shouldTerminateOnExternalThreadInterrupt() throws Exception {
            CountDownLatch takeCalledLatch = new CountDownLatch(1);

            doAnswer(invocation -> {
                takeCalledLatch.countDown();
                Thread.sleep(10000);
                return null;
            }).when(queue).take();

            consumer.start();

            assertThat(takeCalledLatch.await(2, TimeUnit.SECONDS)).isTrue();

            Thread workerThread = getWorkerThread(consumer);
            workerThread.interrupt();

            Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .until(() -> !workerThread.isAlive());

            assertThat(workerThread.isAlive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Тесты Spring-конфигурации")
    class SpringContextTests {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(InMemoryEventConsumer.class)
                .withBean(InMemoryEventQueue.class, () -> queue)
                .withBean(EventExecutionService.class, () -> executionService);

        @Test
        @DisplayName("Должен создавать компонент при app.dispatcher.type = memory")
        void shouldCreateBeanWhenPropertyIsMemory() {
            contextRunner
                    .withPropertyValues("app.dispatcher.type=memory")
                    .run(context -> assertThat(context).hasSingleBean(InMemoryEventConsumer.class));
        }

        @Test
        @DisplayName("Должен создавать компонент, если свойство app.dispatcher.type отсутствует")
        void shouldCreateBeanWhenPropertyIsMissing() {
            contextRunner
                    .run(context -> assertThat(context).hasSingleBean(InMemoryEventConsumer.class));
        }

        @Test
        @DisplayName("Не должен создавать компонент при app.dispatcher.type = rabbit")
        void shouldNotCreateBeanWhenPropertyIsRabbit() {
            contextRunner
                    .withPropertyValues("app.dispatcher.type=rabbit")
                    .run(context -> assertThat(context).doesNotHaveBean(InMemoryEventConsumer.class));
        }
    }
}