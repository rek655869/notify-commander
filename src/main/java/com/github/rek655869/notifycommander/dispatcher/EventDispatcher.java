package com.github.rek655869.notifycommander.dispatcher;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Диспетчер событий, отвечающий за маршрутизацию событий {@link Event} к соответствующим 
 * командам {@link Command}.
 */
@Service
public class EventDispatcher {

    private final List<Command<?>> commands;

    /**
     * При создании компонента проверяется корректность команд:
     * ни одна команда не должна возвращать {@code null} из метода {@link Command#getEventClass()}.
     *
     * @param commands список бинов команд, внедряемый Spring-контейнером
     * @throws NullPointerException если список {@code commands} содержит команду с
     *                              незаданным классом события
     */
    public EventDispatcher(List<Command<?>> commands) {
        this.commands = commands;
        for (Command<?> command : commands) {
            if (command.getEventClass() == null) {
                throw new NullPointerException("Command event class cannot be null");
            }
        }
    }

    /**
     * Передает событие методу {@link Command#dispatch(Event)} каждой команды.
     * Команды самостоятельно определяют, подходит ли им данный тип события
     * и выполнены ли условия для его обработки.
     *
     * @param <T> конкретный тип события, наследующийся от {@link Event}
     * @param event входящее событие для обработки; не может быть {@code null}
     * @throws NullPointerException если переданное событие равно {@code null}
     */
    public <T extends Event> void handle(T event) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null");
        }
        for (Command<?> command : commands) {
            command.dispatch(event);
        }
    }
}