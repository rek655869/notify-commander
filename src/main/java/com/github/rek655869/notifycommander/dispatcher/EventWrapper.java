package com.github.rek655869.notifycommander.dispatcher;

import java.time.Duration;
import lombok.Data;

/**
 * Контейнер-обертка для {@link Event}, хранящий метаданные выполнения и состояние повторных попыток.
 */
@Data
public class EventWrapper {

    private final Event event;
    private int retries = 1;
    private final Duration delay = Duration.ofMillis(500);
}