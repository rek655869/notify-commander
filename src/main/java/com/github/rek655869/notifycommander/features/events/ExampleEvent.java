package com.github.rek655869.notifycommander.features.events;

import com.github.rek655869.notifycommander.dispatcher.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class ExampleEvent implements Event {
    private String message;
}
