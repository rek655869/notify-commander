package com.github.rek655869.notifycommander.features.events;

import com.github.rek655869.notifycommander.dispatcher.Event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExampleEvent implements Event {
    private static final long serialVersionUID = 1L;
    private String message;
}
