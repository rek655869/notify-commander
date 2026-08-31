package com.github.rek655869.notifycommander.dispatcher;

import jakarta.validation.constraints.NotNull;

public interface Command<T extends Event> {

    /** Возвращает класс события, с которыми работает команда для сопоставления с DTO */
    Class<T> getEventClass();

    /** Определяет может ли команда обработать событие */
    boolean canExecute(@NotNull T event);

    /** Выполняет действие, связанное с событием */
    void execute(@NotNull T event);

    /** Проверяет тип события и выполняет команду, если событие подходит по типу и условиям. */
    default void dispatch(@NotNull Event event) {
        if (getEventClass().isInstance(event)) {
            T typedEvent = getEventClass().cast(event);
            if (canExecute(typedEvent)) {
                execute(typedEvent);
            }
        }
    }

    @Override
    String toString();
} 
   