package com.github.rek655869.notifycommander.dispatcher;

import jakarta.validation.constraints.NotNull;

/**
 * Обобщенный интерфейс команды для обработки конкретного типа событий
 * {@link Event}.
 *
 * @param <T> тип обрабатываемого события, наследующийся от {@link Event}
 */
public interface Command<T extends Event> {

    /**
     * Возвращает класс события, обрабатываемого данной командой.
     * Используется диспетчером для сопоставления входящего объекта {@link Event} с
     * нужным обработчиком.
     *
     * @return Класс обрабатываемого события, не может быть {@code null}
     */
    Class<T> getEventClass();

    /**
     * Проверяет условия выполнения команды для конкретного экземляра
     * события.
     * Вызывается перед {@link #execute(Event)} для дополнительной фильтрации.
     *
     * @param event экземпляр события для проверки
     * @return {@code true}, если команда готова обработать данное событие; иначе
     *         {@code false}
     */
    boolean canExecute(@NotNull T event);

    /**
     * Выполняет целевое действие, связанное с входящим событием.
     *
     * @param event событие, содержащее данные для выполнения команды
     */
    void execute(@NotNull T event);

    /**
     * Сопоставляет событие с типом команды, выполняетприведение типов
     * и запускает обработку при успешной проверке условий.
     * <p>
     * Метод по умолчанию (default) реализует следующий порядок обработки:
     * <ol>
     * <li>Проверка совместимости типов через {@code Class.isInstance(Object)}</li>
     * <li>Приведение события к целевому типу {@code T}</li>
     * <li>Проверка условий через {@link #canExecute(Event)}</li>
     * <li>Вызов основной логики через {@link #execute(Event)}</li>
     * </ol>
     *
     * @param event входящее событие
     */
    default void dispatch(@NotNull Event event) {
        if (getEventClass().isInstance(event)) {
            T typedEvent = getEventClass().cast(event);
            if (canExecute(typedEvent)) {
                execute(typedEvent);
            }
        }
    }
}