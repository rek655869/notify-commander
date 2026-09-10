package com.github.rek655869.notifycommander.dispatcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис асинхронного выполнения и повторной обработки событий. В случае
 * возникновения исключений сервис планирует повторную попытку выполнения с
 * фиксированной задержкой в 1 секунду. Максимальное количество попыток
 * ограничено константой {@link #MAX_RETRIES}.
 * <p>
 * Поддерживает две стратегии повторов:
 * <ul>
 * <li>Через {@link EventWrapper} (состояние попыток хранится в обертке).</li>
 * <li>Через обратный вызов {@link Consumer} (состояние управляется внешним
 * сервисом).</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventExecutionService {

    private static final int MAX_RETRIES = 3;
    private final EventDispatcher dispatcher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * Выполняет обработку обернутого события {@link EventWrapper} с поддержкой
     * отложенного повтора при ошибке.
     *
     * @param wrappedEvent обертка события, содержащая сам объект {@link Event} и
     *                     счетчик попыток
     */
    public void executeWithRetry(EventWrapper wrappedEvent) {
        try {
            dispatcher.handle(wrappedEvent.getEvent());
        } catch (Exception e) {
            log.error("Ошибка при обработке события: {}", wrappedEvent.getEvent(), e);
            if (wrappedEvent.getRetries() < MAX_RETRIES) {
                wrappedEvent.setRetries(wrappedEvent.getRetries() + 1);

                scheduler.schedule(() -> executeWithRetry(wrappedEvent), 1, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Универсальный метод обработки событий с внешним управлением повторными
     * попытками.
     *
     * @param event          событие для обработки
     * @param currentAttempt текущий номер попытки обработки
     * @param retryCallback  callback-функция, которая будет вызвана с задержкой в 1
     *                       секунду, если текущая попытка завершится ошибкой и
     *                       {@code currentAttempt < MAX_RETRIES}
     */
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

    /**
     * Выполняет корректное завершение пула потоков при уничтожении бина.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Остановка ScheduledExecutorService в EventExecutionService...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Планировщик не завершился за отведенное время, принудительная остановка...");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Прервано ожидание завершения работы планировщика", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}