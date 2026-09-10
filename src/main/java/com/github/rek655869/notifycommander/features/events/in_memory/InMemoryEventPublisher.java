package com.github.rek655869.notifycommander.features.events.in_memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.github.rek655869.notifycommander.dispatcher.Event;
import com.github.rek655869.notifycommander.dispatcher.EventPublisher;
import com.github.rek655869.notifycommander.dispatcher.EventWrapper;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "app.dispatcher.type", havingValue = "memory", matchIfMissing = true)
@RequiredArgsConstructor
public class InMemoryEventPublisher implements EventPublisher {

    private final InMemoryEventQueue queue;

    @Override
    public boolean publish(Event event) {
        return queue.offer(new EventWrapper(event));
    }

}
