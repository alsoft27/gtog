package com.gtog.event.application;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineCommand;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.EventResponseDeadlineRepositoryPort;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeResponseDeadlineServiceTest {

	@Mock private EventRepositoryPort events;
	@Mock private EventResponseDeadlineRepositoryPort deadlines;
	private ChangeResponseDeadlineService service;

	@BeforeEach
	void setUp() {
		service = new ChangeResponseDeadlineService(events, deadlines, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void persistsOnlyTheDeadlineAndReturnsTheUpdatedDocument() {
		Event event = event(EventStatus.PUBLISHED, null);
		when(events.findById("event-id")).thenReturn(Optional.of(event));
		when(deadlines.updateDeadline("event-id", "host-1", null)).thenReturn(Optional.of(event));
		assertThat(service.changeResponseDeadline(new ChangeResponseDeadlineCommand("host-1", "event-id", null))).isSameAs(event);
		verify(events, never()).save(any());
	}

	@Test
	void checksOwnershipBeforeAttemptingThePartialWrite() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null)));
		assertThatThrownBy(() -> service.changeResponseDeadline(new ChangeResponseDeadlineCommand("other", "event-id", null)))
				.isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(deadlines);
	}

	@Test
	void returnsNotFoundForAnUnknownEvent() {
		when(events.findById("missing")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.changeResponseDeadline(new ChangeResponseDeadlineCommand("host-1", "missing", null)))
				.isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(deadlines);
	}

	@Test
	void missingMatchAfterValidationIsAConflictWithoutFallingBackToSave() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null)));
		when(deadlines.updateDeadline("event-id", "host-1", null)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.changeResponseDeadline(new ChangeResponseDeadlineCommand("host-1", "event-id", null)))
				.isInstanceOf(EventConcurrentModificationException.class);
		verify(events, never()).save(any());
	}
}
