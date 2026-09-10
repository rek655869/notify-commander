package com.github.rek655869.notifycommander.dispatcher;

/**
 * Интерфейс для публикации событий {@link Event}.
 */
public interface EventPublisher {

    /**
     * Публикует указанное событие для последующей обработки.
     *
     * @param event событие для публикации; не должно быть {@code null}
     * @return {@code true}, если событие успешно поставлено в очередь или принято к обработке;
     *         {@code false}, если при отправке произошел сбой
     */
    boolean publish(Event event);
}