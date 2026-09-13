package com.github.rek655869.notifycommander.features.events.in_memory;

import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

@Service
@ConditionalOnProperty(name = "app.dispatcher.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryEventQueue {
    private final LinkedBlockingQueue<EventWrapper> queue = new LinkedBlockingQueue<>(100);

    public boolean offer(EventWrapper wrapper) {
        return queue.offer(wrapper);
    }

    public EventWrapper take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}
