package com.github.rek655869.notifycommander.features.events;

import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.github.rek655869.notifycommander.dispatcher.Event;
import com.github.rek655869.notifycommander.dispatcher.EventDispatcher;
import com.github.rek655869.notifycommander.dispatcher.EventPublisher;
import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "app.dispatcher.type", havingValue = "memory", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class EventQueueProcessor implements EventPublisher {

    private final EventDispatcher dispatcher;
    private final LinkedBlockingQueue<EventWrapper> queue = new LinkedBlockingQueue<>(100);
    private volatile boolean running = true;
    private Thread workerThread;
    private static final int MAX_RETRIES = 3;

    @Override
    public boolean publish(Event event) {
        return queue.offer(new EventWrapper(event));
    }

    private boolean publishWithWrapper(EventWrapper eventWrapper) {
        return queue.offer(eventWrapper);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void process() {
        workerThread = Thread.ofPlatform()
                .name("event-queue-loop")
                .daemon(false)
                .start(() -> {
                    log.info("Поток-слушатель очереди успешно стартовал и ожидает событий.");
                    while (running && !Thread.currentThread().isInterrupted()) {
                        try {
                            EventWrapper wrappedEvent = queue.take();
                            log.debug("Получено событие из очереди: {}", wrappedEvent.getEvent());
                            Thread.ofVirtual().start(() -> handle(wrappedEvent));
                        } catch (InterruptedException e) {
                            log.info("Поток обработки событий прерван.");
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
    }

    private void handle(EventWrapper wrappedEvent) {
        try {
            dispatcher.handle(wrappedEvent.getEvent());
        } catch (Exception e) {
            log.error("Непредвиденная ошибка во время обработки события: {}", wrappedEvent.getEvent(), e);
            if (wrappedEvent.getRetries() < MAX_RETRIES) {
                try {
                    Thread.sleep(1000);
                    wrappedEvent.setRetries(wrappedEvent.getRetries() + 1);
                    publishWithWrapper(wrappedEvent);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @PreDestroy
    public void stopProcess() {
        this.running = false;
        if (workerThread != null)
            workerThread.interrupt();
    }
}
