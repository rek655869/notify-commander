package com.github.rek655869.notifycommander.dispatcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventExecutionService {

    private static final int MAX_RETRIES = 3;
    private final EventDispatcher dispatcher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Для In-Memory очереди (с поддержкой EventWrapper)
    public void executeWithRetry(EventWrapper wrappedEvent) {
        try {
            dispatcher.handle(wrappedEvent.getEvent());
        } catch (Exception e) {
            log.error("Ошибка при обработке события: {}", wrappedEvent.getEvent(), e);
            if (wrappedEvent.getRetries() < MAX_RETRIES) {
                wrappedEvent.setRetries(wrappedEvent.getRetries() + 1);
                
                // Используем ScheduledExecutorService вместо Thread.sleep
                scheduler.schedule(() -> executeWithRetry(wrappedEvent), 1, TimeUnit.SECONDS);
            }
        }
    }

    // Универсальный метод для брокеров без EventWrapper
    public void executeWithRetry(Event event, int currentAttempt, Consumer<Event> retryCallback) {
        try {
            dispatcher.handle(event);
        } catch (Exception e) {
            log.error("Ошибка при обработке события: {}", event, e);
            if (currentAttempt < MAX_RETRIES) {
                scheduler.schedule(() -> retryCallback.accept(event), 1, TimeUnit.SECONDS);
            }
        }
    }
}