package com.gtog.event.application;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gtog.event.domain.model.DuplicateGuestTokenException;
import com.gtog.event.domain.model.EventConcurrentModificationException;
import com.gtog.event.domain.model.EventGuestsNotEditableException;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestResponseDeletionConfirmationRequiredException;
import com.gtog.event.domain.port.in.AddGuestCommand;
import com.gtog.event.domain.port.in.DeleteGuestCommand;
import com.gtog.event.domain.port.in.UpdateGuestCommand;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseCleanupPort;
import com.gtog.event.domain.port.out.GuestTokenGeneratorPort;
import com.gtog.event.domain.port.out.PhoneNormalizerPort;

import static com.gtog.event.GuestTestFixtures.event;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

	@Mock private EventRepositoryPort events;
	@Mock private EventGuestRepositoryPort guests;
	@Mock private GuestTokenGeneratorPort tokens;
	@Mock private PhoneNormalizerPort phones;
	@Mock private GuestResponseCleanupPort responses;
	private GuestService service;

	@BeforeEach
	void setUp() {
		service = new GuestService(events, guests, tokens, phones, responses);
	}

	@Test
	void addsThroughThePartialWritePortWithoutSavingTheEvent() {
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.DRAFT, 7L)));
		when(tokens.generate()).thenReturn(token(1));
		Guest added = service.add(new AddGuestCommand("host-1", "event-1", "Ana", "ana@example.com", null));
		verify(guests).addGuest("event-1", "host-1", 7L, added);
		verify(events, never()).save(any());
	}

	@Test
	void updatesOnlyTheExistingGuestsContacts() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.PUBLISHED, 7L, first)));
		Guest edited = service.update(new UpdateGuestCommand("host-1", "event-1", first.getId(), "Changed", first.getEmail(), null));
		assertThat(edited.getToken()).isEqualTo(first.getToken());
		verify(guests).updateGuest("event-1", "host-1", 7L, edited);
		verify(events, never()).save(any());
		verifyNoInteractions(tokens);
	}

	@Test
	void deletesAnUnansweredGuestWithoutRequiringConfirmation() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.DRAFT, 7L, first)));
		service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), false));
		verify(guests).removeGuest("event-1", "host-1", 7L, first.getId());
		verify(responses).deleteResponse("event-1", first.getToken());
		verify(events, never()).save(any());
	}

	@Test
	void requiresExplicitConfirmationWhenTheResponsePortReportsAResponse() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.PUBLISHED, 7L, first)));
		when(responses.hasResponse("event-1", first.getToken())).thenReturn(true);
		assertThatThrownBy(() -> service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), false)))
				.isInstanceOf(GuestResponseDeletionConfirmationRequiredException.class);
		verifyNoInteractions(guests);
		verify(responses, never()).deleteResponse(anyString(), anyString());
	}

	@Test
	void removesTheGuestBeforeDeletingTheResponseWhenConfirmed() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.PUBLISHED, 7L, first)));
		when(responses.hasResponse("event-1", first.getToken())).thenReturn(true);
		service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), true));
		var order = inOrder(responses, guests);
		order.verify(guests).removeGuest("event-1", "host-1", 7L, first.getId());
		order.verify(responses).deleteResponse("event-1", first.getToken());
	}

	@Test
	void leavesTheGuestRemovedIfTheCascadeFails() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.PUBLISHED, 7L, first)));
		when(responses.hasResponse("event-1", first.getToken())).thenReturn(true);
		doThrow(new IllegalStateException("failed")).when(responses).deleteResponse("event-1", first.getToken());
		assertThatThrownBy(() -> service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), true)))
				.isInstanceOf(IllegalStateException.class);
		var order = inOrder(guests, responses);
		order.verify(guests).removeGuest("event-1", "host-1", 7L, first.getId());
		order.verify(responses).deleteResponse("event-1", first.getToken());
	}

	@Test
	void doesNotDeleteTheResponseIfRemovingTheGuestFails() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.PUBLISHED, 7L, first)));
		when(responses.hasResponse("event-1", first.getToken())).thenReturn(true);
		doThrow(new EventConcurrentModificationException()).when(guests).removeGuest("event-1", "host-1", 7L, first.getId());
		assertThatThrownBy(() -> service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), true)))
				.isInstanceOf(EventConcurrentModificationException.class);
		verify(responses, never()).deleteResponse(anyString(), anyString());
	}

	@Test
	void checksOwnershipForEveryUseCaseBeforeProcessingGuestData() {
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.DRAFT, 7L)));
		assertThatThrownBy(() -> service.add(new AddGuestCommand("other-host", "event-1", null, null, null)))
				.isInstanceOf(EventNotFoundException.class);
		assertThatThrownBy(() -> service.update(new UpdateGuestCommand("other-host", "event-1", "missing", null, null, null)))
				.isInstanceOf(EventNotFoundException.class);
		assertThatThrownBy(() -> service.delete(new DeleteGuestCommand("other-host", "event-1", "missing", false)))
				.isInstanceOf(EventNotFoundException.class);
		assertThatThrownBy(() -> service.list("other-host", "event-1")).isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(guests, tokens, phones, responses);
	}

	@Test
	void returnsNotFoundForAnUnknownEvent() {
		when(events.findById("missing")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.list("host-1", "missing")).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void allowsListingGuestsOfAClosedEvent() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.CANCELLED, 7L, first)));
		assertThat(service.list("host-1", "event-1")).containsExactly(first);
	}

	@Test
	void rejectsAForcedGeneratorCollisionWithinTheEvent() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.DRAFT, 7L, first)));
		when(tokens.generate()).thenReturn(first.getToken());
		assertThatThrownBy(() -> service.add(new AddGuestCommand("host-1", "event-1", "Other", "other@example.com", null)))
				.isInstanceOf(DuplicateGuestTokenException.class);
		verifyNoInteractions(guests);
	}

	@Test
	void propagatesConcurrentModificationWithoutFallingBackToFullSave() {
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.DRAFT, 7L)));
		when(tokens.generate()).thenReturn(token(1));
		doThrow(new EventConcurrentModificationException()).when(guests).addGuest(eq("event-1"), eq("host-1"), eq(7L), any());
		assertThatThrownBy(() -> service.add(new AddGuestCommand("host-1", "event-1", "Ana", "ana@example.com", null)))
				.isInstanceOf(EventConcurrentModificationException.class);
		verify(events, never()).save(any());
	}

	@Test
	void rejectsClosedEventsBeforeConsultingTheCascadePort() {
		Guest first = guest(1);
		when(events.findById("event-1")).thenReturn(Optional.of(event("event-1", EventStatus.CANCELLED, 7L, first)));
		assertThatThrownBy(() -> service.delete(new DeleteGuestCommand("host-1", "event-1", first.getId(), true)))
				.isInstanceOf(EventGuestsNotEditableException.class);
		verifyNoInteractions(responses, guests);
	}
}
