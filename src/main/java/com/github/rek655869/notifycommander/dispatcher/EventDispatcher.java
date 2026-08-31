package com.github.rek655869.notifycommander.dispatcher;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class EventDispatcher {

    private final Map<Class<? extends Event>, List<Command<?>>> commandMap;

    public EventDispatcher(List<Command<?>> commands) {
        this.commandMap = commands.stream().collect(
                Collectors.groupingBy(Command::getEventClass));
    }

    public <T extends Event> void handle(T event) {
        List<Command<?>> commands = commandMap.get(event.getClass());

        for (Command<?> command : commands) {
            command.dispatch(event);
        }
    }
}
