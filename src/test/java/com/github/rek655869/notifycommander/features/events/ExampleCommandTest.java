package com.github.rek655869.notifycommander.features.events;

import com.github.rek655869.notifycommander.dispatcher.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExampleCommandTest {

    private ExampleCommand command;

    @BeforeEach
    void setUp() {
        command = new ExampleCommand();
    }

    @Test
    @DisplayName("getEventClass должен возвращать ExampleEvent.class")
    void getEventClass_ShouldReturnCorrectClass() {
        assertThat(command.getEventClass()).isEqualTo(ExampleEvent.class);
    }

    @Nested
    @DisplayName("Тестирование метода canExecute()")
    class CanExecuteTests {

        @Test
        @DisplayName("Должен возвращать true, если сообщение начинается со слэша '\\'")
        void canExecute_ShouldReturnTrue_WhenMessageStartsWithBackslash() {
            ExampleEvent event = mock(ExampleEvent.class);
            when(event.getMessage()).thenReturn("\\start");

            boolean result = command.canExecute(event);

            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"hello", "/start", "  \\start", "command\\"})
        @DisplayName("Должен возвращать false, если сообщение не начинается со слэша '\\'")
        void canExecute_ShouldReturnFalse_WhenMessageDoesNotStartWithBackslash(String message) {
            ExampleEvent event = mock(ExampleEvent.class);
            when(event.getMessage()).thenReturn(message);

            boolean result = command.canExecute(event);

            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Должен возвращать false, если сообщение null или пустое")
        void canExecute_ShouldReturnFalse_WhenMessageIsNullOrEmpty(String message) {
            ExampleEvent event = mock(ExampleEvent.class);
            when(event.getMessage()).thenReturn(message);

            boolean result = command.canExecute(event);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Тестирование выполнения команды через dispatch()")
    class DispatchTests {

        @Test
        @DisplayName("dispatch должен обработать событие, если оно подпадает под условие")
        void dispatch_ShouldProcessEvent_WhenValid() {
            ExampleEvent event = mock(ExampleEvent.class);
            when(event.getMessage()).thenReturn("\\help");

            command.dispatch(event);

            assertThat(command.canExecute(event)).isTrue();
        }

        @Test
        @DisplayName("dispatch не должен обрабатывать чужой Event")
        void dispatch_ShouldIgnore_WhenOtherEventClass() {
            Event unhandledEvent = new Event() {};

            command.dispatch(unhandledEvent);
        }
    }
}