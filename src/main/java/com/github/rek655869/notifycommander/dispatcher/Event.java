package com.github.rek655869.notifycommander.dispatcher;

import java.io.Serializable;

/**
 * Служит базовам типом для абстракции событий. 
 * Наследует {@link Serializable} для обеспечения возможности сериализации.
 */
public interface Event extends Serializable {

}
