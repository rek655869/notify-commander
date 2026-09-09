package com.github.rek655869.notifycommander.dispatcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventDispatcherTest {

    static class TestEvent implements Event {
    }

    static class OtherTestEvent implements Event {
    }

    static class ChildTestEvent extends TestEvent {
    }

    private final Event testEvent = new TestEvent();

    @Mock
    private Command<TestEvent> testCommand1;

    @Mock
    private Command<TestEvent> testCommand2;

    @Mock
    private Command<OtherTestEvent> otherTestCommand;

    @Test
    @DisplayName("Должен выполнять команду для базового класса, если передано событие-наследник")
    void shouldDispatchParentCommandToChildEvent() {
        Event childEvent = new ChildTestEvent();

        doReturn(TestEvent.class).when(testCommand1).getEventClass();

        List<Command<?>> commands = List.of(testCommand1);

        EventDispatcher dispatcher = new EventDispatcher(commands);
        dispatcher.handle(childEvent);

        verify(testCommand1, times(1)).dispatch(childEvent);
    }

    @Test
    @DisplayName("Должен успешно инициализироваться с пустым списком команд")
    void shouldInitializeWithEmptyCommandMap() {
        List<Command<?>> commands = List.of();

        EventDispatcher dispatcher = new EventDispatcher(commands);
        dispatcher.handle(testEvent);

        verify(testCommand1, never()).dispatch(any());
    }

    @Test
    @DisplayName("Должен выбрасывать ошибку, если команда упала")
    void shouldThrowErrorIfCommandFail() {
        doReturn(testEvent.getClass()).when(testCommand1).getEventClass();
        doReturn(testEvent.getClass()).when(testCommand2).getEventClass();
        doThrow(new RuntimeException())
                .when(testCommand1).dispatch(testEvent);

        List<Command<?>> commands = List.of(testCommand1, testCommand2);

        EventDispatcher dispatcher = new EventDispatcher(commands);

        assertThatThrownBy(() -> dispatcher.handle(testEvent))
                .isInstanceOf(RuntimeException.class);
        verify(testCommand1, times(1)).dispatch(testEvent);
        verify(testCommand2, never()).dispatch(any());

    }

    @Test
    @DisplayName("Должен выбросить ошибку, если в событии передано null")
    void shouldThrowErrorIfNullEvent() {
        doReturn(testEvent.getClass()).when(testCommand1).getEventClass();

        List<Command<?>> commands = List.of(testCommand1);

        EventDispatcher dispatcher = new EventDispatcher(commands);

        assertThatThrownBy(() -> dispatcher.handle(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Должен выбросить ошибку, если у команды не задано обрабатываемое событие")
    void shouldThrowErrorIfNullCommandEvent() {
        doReturn(null).when(testCommand1).getEventClass();

        List<Command<?>> commands = List.of(testCommand1);

        assertThatThrownBy(() -> new EventDispatcher(commands))
                .isInstanceOf(NullPointerException.class);
    }

}
