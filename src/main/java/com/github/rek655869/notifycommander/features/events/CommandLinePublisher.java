package com.github.rek655869.notifycommander.features.events;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import com.github.rek655869.notifycommander.features.events.in_memory.InMemoryEventPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommandLinePublisher implements CommandLineRunner {
    private final InMemoryEventPublisher publisher;

    @Override
    public void run(String... args) throws Exception {
        var firstEvent = new ExampleEvent("\\first event");
        publisher.publish(firstEvent);
        Thread.sleep(500);

        var secondEvent = new ExampleEvent("\\second event");
        publisher.publish(secondEvent);
        Thread.sleep(500);
    }

}
