package com.github.rek655869.notifycommander.features.events.in_memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "app.dispatcher.type", havingValue = "memory", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class InMemoryEventConsumer {

    private final InMemoryEventQueue queue;
    private final EventExecutionService executionService;
    private volatile boolean running = true;
    private Thread workerThread;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        workerThread = Thread.ofPlatform()
                .name("event-queue-loop")
                .start(() -> {
                    log.info("Поток-слушатель очереди стартовал.");
                    while (running && !Thread.currentThread().isInterrupted()) {
                        try {
                            EventWrapper wrappedEvent = queue.take();
                            Thread.ofVirtual().start(
                                    () -> executionService.executeWithRetry(wrappedEvent));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            log.error("Непредвиденная ошибка при получении события из очереди", e);
                        }
                    }
                });
    }

    @PreDestroy
    public void stop() {
        this.running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}