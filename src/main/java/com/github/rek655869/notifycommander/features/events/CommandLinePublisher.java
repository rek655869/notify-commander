package com.github.rek655869.notifycommander.features.events;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommandLinePublisher implements CommandLineRunner {
    private final EventQueueProcessor queueProcessor;

    @Override
    public void run(String... args) throws Exception {
        var firstEvent = new ExampleEvent("\\first event");
        queueProcessor.publish(firstEvent);
        Thread.sleep(500);

        var secondEvent = new ExampleEvent("\\second event");
        queueProcessor.publish(secondEvent);
        Thread.sleep(500);
    }

}
