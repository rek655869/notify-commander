package com.github.rek655869.notifycommander.dispatcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommandTest {

    static class TestEvent implements Event {
    }

    static class OtherEvent implements Event {
    }

    static class ChildTestEvent extends TestEvent {
    }

    @Mock
    private Command<TestEvent> mockCommand;

    @Nested
    @DisplayName("Тестирование логики метода dispatch()")
    class DispatchMethodTests {

        @Test
        @DisplayName("Должен успешно вызвать execute(), когда событие подходит по типу и canExecute() возвращает true")
        void shouldExecuteWhenEventIsInstanceAndCanExecuteIsTrue() {
            TestEvent event = new TestEvent();

            when(mockCommand.getEventClass()).thenReturn(TestEvent.class);
            when(mockCommand.canExecute(event)).thenReturn(true);
            doCallRealMethod().when(mockCommand).dispatch(event);

            mockCommand.dispatch(event);

            verify(mockCommand).canExecute(event);
            verify(mockCommand).execute(event);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать события-наследники (ChildEvent)")
        void shouldExecuteWhenEventIsSubclass() {
            ChildTestEvent childEvent = new ChildTestEvent();

            when(mockCommand.getEventClass()).thenReturn(TestEvent.class);
            when(mockCommand.canExecute(childEvent)).thenReturn(true);
            doCallRealMethod().when(mockCommand).dispatch(childEvent);

            mockCommand.dispatch(childEvent);

            verify(mockCommand).canExecute(childEvent);
            verify(mockCommand).execute(childEvent);
        }

        @Test
        @DisplayName("Не должен вызывать execute(), если canExecute() возвращает false")
        void shouldNotExecuteWhenCanExecuteIsFalse() {
            TestEvent event = new TestEvent();

            when(mockCommand.getEventClass()).thenReturn(TestEvent.class);
            when(mockCommand.canExecute(event)).thenReturn(false);
            doCallRealMethod().when(mockCommand).dispatch(event);

            mockCommand.dispatch(event);

            verify(mockCommand).canExecute(event);
            verify(mockCommand, never()).execute(any());
        }

        @Test
        @DisplayName("Не должен проверять canExecute() и вызывать execute(), если передан несопоставимый тип события")
        void shouldIgnoreEventWhenTypeDoesNotMatch() {
            OtherEvent otherEvent = new OtherEvent();

            doReturn(TestEvent.class).when(mockCommand).getEventClass();
            doCallRealMethod().when(mockCommand).dispatch(otherEvent);

            mockCommand.dispatch(otherEvent);

            verify(mockCommand, never()).canExecute(any());
            verify(mockCommand, never()).execute(any());
        }
    }

}