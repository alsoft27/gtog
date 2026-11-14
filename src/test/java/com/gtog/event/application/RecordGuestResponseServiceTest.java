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
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.port.in.RecordGuestResponseCommand;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RecordGuestResponseServiceTest {
	@Mock private EventRepositoryPort events;
	@Mock private GuestResponseRepositoryPort responses;
	private RecordGuestResponseService service;
	private final RecordGuestResponseCommand command = new RecordGuestResponseCommand("secret-token", "yes-id", null, null);

	@BeforeEach
	void setUp() { service = new RecordGuestResponseService(events, responses, Clock.fixed(NOW, ZoneOffset.UTC)); }

	@Test
	void unknownTokenDoesNotWrite() {
		assertThatThrownBy(() -> service.recordResponse(command)).isInstanceOf(InvitationNotFoundException.class);
		verifyNoInteractions(responses);
	}

	@Test
	void draftIsIndistinguishableFromUnknownAndDoesNotWrite() {
		when(events.findByGuestToken(command.token())).thenReturn(Optional.of(responseEvent(EventStatus.DRAFT, true, false, null)));
		assertThatThrownBy(() -> service.recordResponse(command)).isInstanceOf(InvitationNotFoundException.class).hasMessage("Invitation not found");
		verifyNoInteractions(responses);
	}

	@Test
	void insertsWithoutCheckingExistenceWhenChangesAreDisabled() {
		Event event = responseEvent(EventStatus.PUBLISHED, true, false, null);
		var cmd = commandFor(event);
		when(events.findByGuestToken(cmd.token())).thenReturn(Optional.of(event));
		when(responses.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
		assertThat(service.recordResponse(cmd).myResponse().answeredAt()).isEqualTo(NOW);
		verify(responses).insert(any());
		verify(responses, never()).find(any(), any());
		verify(responses, never()).upsert(any());
		verify(events, never()).save(any());
	}

	@Test
	void upsertsWithoutCheckingExistenceWhenChangesAreEnabled() {
		Event event = responseEvent(EventStatus.PUBLISHED, true, true, null);
		var cmd = commandFor(event);
		when(events.findByGuestToken(cmd.token())).thenReturn(Optional.of(event));
		when(responses.upsert(any())).thenAnswer(invocation -> invocation.getArgument(0));
		assertThat(service.recordResponse(cmd).canChangeResponse()).isTrue();
		verify(responses).upsert(any());
		verify(responses, never()).find(any(), any());
		verify(responses, never()).insert(any());
	}

	private RecordGuestResponseCommand commandFor(Event event) {
		return new RecordGuestResponseCommand(event.getGuests().getFirst().getToken(), "yes-id", null, null);
	}
}
