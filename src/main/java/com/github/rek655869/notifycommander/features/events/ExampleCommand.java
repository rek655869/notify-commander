package com.github.rek655869.notifycommander.features.events;

import org.springframework.stereotype.Component;

import com.github.rek655869.notifycommander.dispatcher.Command;

@Component
public class ExampleCommand implements Command<ExampleEvent>{

    @Override
    public Class<ExampleEvent> getEventClass() {
        return ExampleEvent.class;
    }

    @Override
    public boolean canExecute(ExampleEvent event) {
        return event.getMessage() != null && event.getMessage().startsWith("\\");
    }

    @Override
    public void execute(ExampleEvent event) {
        System.out.println("example command");
    }
    
}
