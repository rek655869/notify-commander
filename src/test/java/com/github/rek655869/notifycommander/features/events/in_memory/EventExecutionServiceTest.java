package com.github.rek655869.notifycommander.features.events.in_memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rek655869.notifycommander.dispatcher.Event;
import com.github.rek655869.notifycommander.dispatcher.EventDispatcher;
import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

@ExtendWith(MockitoExtension.class)
public class EventExecutionServiceTest {

    @Mock
    private EventDispatcher dispatcher;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private EventWrapper wrappedEvent;

    @Mock
    private Event event;

    private EventExecutionService eventExecutionService;

    @BeforeEach
    void setUp() throws Exception {
        eventExecutionService = new EventExecutionService(dispatcher);

        // Подменяем внутренний приватный scheduler на мок для изоляции и быстрой работы
        // тестов
        Field schedulerField = EventExecutionService.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(eventExecutionService, scheduler);
    }

    @Test
    @DisplayName("Должен успешно обрабатывать событие с первой попытки")
    void shouldExecuteSuccessfullyOnFirstTry() {
        doReturn(event).when(wrappedEvent).getEvent();

        eventExecutionService.executeWithRetry(wrappedEvent);

        verify(dispatcher, times(1)).handle(event);
        verify(wrappedEvent, never()).setRetries(anyInt());
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }
    
    @Nested
    @DisplayName("Тестирование повторной обработки")
    class RetryLogicTest {
        @Test
        @DisplayName("Должен увеличивать счетчик попыток и планировать повтор при первой ошибке")
        void shouldScheduleRetryOnFirstError() {
            doReturn(event).when(wrappedEvent).getEvent();
            doReturn(0).when(wrappedEvent).getRetries();
            doThrow(new RuntimeException("Ошибка обработки")).when(dispatcher).handle(event);

            eventExecutionService.executeWithRetry(wrappedEvent);

            verify(wrappedEvent, times(1)).setRetries(1);
            verify(scheduler, times(1)).schedule(any(Runnable.class), eq(1L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Должен инкрементировать retries до 3 и планировать повтор при retries = 2")
        void shouldScheduleRetryWhenRetriesIsTwo() {
            doReturn(event).when(wrappedEvent).getEvent();
            doReturn(2).when(wrappedEvent).getRetries();
            doThrow(new RuntimeException("Ошибка обработки")).when(dispatcher).handle(event);

            eventExecutionService.executeWithRetry(wrappedEvent);

            verify(wrappedEvent, times(1)).setRetries(3);
            verify(scheduler, times(1)).schedule(any(Runnable.class), eq(1L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Должен игнорировать повтор и не изменять retries при достижении лимита (retries = 3)")
        void shouldNotRetryWhenMaxRetriesReached() {
            doReturn(event).when(wrappedEvent).getEvent();
            doReturn(3).when(wrappedEvent).getRetries();
            doThrow(new RuntimeException("Ошибка обработки")).when(dispatcher).handle(event);

            eventExecutionService.executeWithRetry(wrappedEvent);

            verify(wrappedEvent, never()).setRetries(anyInt());
            verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        }

        @Test
        @DisplayName("Должен игнорировать повтор при превышении лимита попыток (retries = 4)")
        void shouldNotRetryWhenRetriesExceedMax() {
            doReturn(event).when(wrappedEvent).getEvent();
            doReturn(4).when(wrappedEvent).getRetries();
            doThrow(new RuntimeException("Ошибка обработки")).when(dispatcher).handle(event);

            eventExecutionService.executeWithRetry(wrappedEvent);

            verify(wrappedEvent, never()).setRetries(anyInt());
            verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        }

        @Test
        @DisplayName("Должен успешно выполнять обработку при повторной попытке")
        void shouldExecuteSuccessfullyOnRetry() {
            doReturn(event).when(wrappedEvent).getEvent();
            doReturn(0).when(wrappedEvent).getRetries();

            doThrow(new RuntimeException("Ошибка"))
                    .doNothing()
                    .when(dispatcher).handle(event);

            doAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return null;
            }).when(scheduler).schedule(any(Runnable.class), eq(1L), eq(TimeUnit.SECONDS));

            eventExecutionService.executeWithRetry(wrappedEvent);

            verify(wrappedEvent).setRetries(1);
            verify(dispatcher, times(2)).handle(event);
        }
    }

    @Nested
    @DisplayName("Тестирование авершения работы бина")
    class DestroyBeanTest {
        @Test
        @DisplayName("Должен штатно завершать работу планировщика при остановке")
        void shouldShutdownGracefully() throws InterruptedException {
            doReturn(true).when(scheduler).awaitTermination(5, TimeUnit.SECONDS);

            eventExecutionService.shutdown();

            verify(scheduler, times(1)).shutdown();
            verify(scheduler, times(1)).awaitTermination(5, TimeUnit.SECONDS);
            verify(scheduler, never()).shutdownNow();
        }

        @Test
        @DisplayName("Должен принудительно останавливать планировщик при превышении таймаута ожидания")
        void shouldForceShutdownOnTimeout() throws InterruptedException {
            doReturn(false).when(scheduler).awaitTermination(5, TimeUnit.SECONDS);

            eventExecutionService.shutdown();

            verify(scheduler, times(1)).shutdown();
            verify(scheduler, times(1)).awaitTermination(5, TimeUnit.SECONDS);
            verify(scheduler, times(1)).shutdownNow();
        }

        @Test
        @DisplayName("Должен принудительно останавливать планировщик и восстанавливать флаг прерывания при InterruptedException")
        void shouldHandleInterruptedExceptionOnShutdown() throws InterruptedException {
            doThrow(new InterruptedException()).when(scheduler).awaitTermination(5, TimeUnit.SECONDS);

            eventExecutionService.shutdown();

            verify(scheduler, times(1)).shutdown();
            verify(scheduler, times(1)).shutdownNow();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Должен пробрасывать Error и не планировать повторную попытку")
    void shouldPropagateErrorAndNotScheduleRetry() {
        doReturn(event).when(wrappedEvent).getEvent();
        doThrow(new OutOfMemoryError("OOM")).when(dispatcher).handle(event);

        assertThatThrownBy(() -> eventExecutionService.executeWithRetry(wrappedEvent))
                .isInstanceOf(Error.class);

        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        verify(wrappedEvent, never()).setRetries(anyInt());
    }

    @Test
    @DisplayName("Должен выбрасывать NullPointerException при передаче null")
    void shouldThrowNullPointerExceptionWhenWrappedEventIsNull() {
        assertThatThrownBy(() -> eventExecutionService.executeWithRetry(null))
                .isInstanceOf(NullPointerException.class);
    }
}