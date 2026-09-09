package com.github.rek655869.notifycommander.dispatcher;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class EventDispatcher {

    private final List<Command<?>> commands;

    public EventDispatcher(List<Command<?>> commands) {
        this.commands = commands;
        for (Command<?> command : commands) {
            if (command.getEventClass() == null) {
                throw new NullPointerException("Command event class cannot be null");
            }
        }
    }

    public <T extends Event> void handle(T event) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null");
        }
        for (Command<?> command : commands) {
            command.dispatch(event);
        }
    }
}
