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
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.port.out.EventGuestRepositoryPort;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static com.gtog.event.InvitationTestFixtures.guest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GetInvitationServiceTest {

	@Mock private EventRepositoryPort events;
	@Mock private GuestResponseRepositoryPort responses;
	@Mock private EventGuestRepositoryPort guests;
	private GetInvitationService service;

	@BeforeEach
	void setUp() {
		service = new GetInvitationService(events, guests, Clock.fixed(NOW, ZoneOffset.UTC), responses);
	}

	@Test
	void unknownTokensHaveAConstantExceptionWithoutTheToken() {
		when(events.findByGuestToken("unknown-secret")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getInvitation("unknown-secret"))
				.isInstanceOf(InvitationNotFoundException.class).hasMessage("Invitation not found");
		verifyNoInteractions(guests);
	}

	@Test
	void draftEventsHaveTheSameExceptionAndDoNotWrite() {
		Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(event(EventStatus.DRAFT, null, guest)));
		assertThatThrownBy(() -> service.getInvitation(guest.getToken()))
				.isInstanceOf(InvitationNotFoundException.class).hasMessage("Invitation not found");
		verifyNoInteractions(guests);
	}

	@Test
	void skipsTheWriteWhenTheLoadedGuestIsAlreadyOpened() {
		Guest guest = guest(101, InvitationStatus.OPENED, NOW.minusSeconds(30));
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest)));
		assertThat(service.getInvitation(guest.getToken()).guestName()).isEqualTo(guest.getName());
		verifyNoInteractions(guests);
		verify(events, times(1)).findByGuestToken(guest.getToken());
	}

	@Test
	void passesTheInjectedClockToTheFirstOpeningAndDoesNotReloadOnSuccess() {
		Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
		Event published = event(EventStatus.PUBLISHED, null, guest);
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(published));
		when(guests.markGuestOpened("event-id", guest.getToken(), NOW)).thenReturn(Optional.of(published));
		service.getInvitation(guest.getToken());
		verify(guests).markGuestOpened("event-id", guest.getToken(), NOW);
		verify(events, times(1)).findByGuestToken(guest.getToken());
	}

	@Test
	void reloadsAfterAnOpeningRaceAndUsesTheWinningState() {
		Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
		when(events.findByGuestToken(guest.getToken())).thenReturn(
				Optional.of(event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, guest)),
				Optional.of(event(EventStatus.CANCELLED, LinkVisibility.ALWAYS, guest)));
		when(guests.markGuestOpened("event-id", guest.getToken(), NOW)).thenReturn(Optional.empty());
		var view = service.getInvitation(guest.getToken());
		assertThat(view.status()).isEqualTo(EventStatus.CANCELLED);
		assertThat(view.onlineAccess()).isNull();
		verify(events, times(2)).findByGuestToken(guest.getToken());
	}

	@Test
	void returnsNotFoundIfTheInvitationWasDeletedDuringTheOpeningRace() {
		Guest guest = guest(101, InvitationStatus.SENT, null);
		when(events.findByGuestToken(guest.getToken())).thenReturn(
				Optional.of(event(EventStatus.PUBLISHED, null, guest)), Optional.empty());
		when(guests.markGuestOpened("event-id", guest.getToken(), NOW)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getInvitation(guest.getToken())).isInstanceOf(InvitationNotFoundException.class);
	}

	@Test
	void aConcurrentOpeningDoesNotCauseAnotherWrite() {
		Guest guest = guest(101, InvitationStatus.SENT, null);
		Guest opened = guest(101, InvitationStatus.OPENED, NOW.minusSeconds(1));
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest)),
				Optional.of(event(EventStatus.PUBLISHED, null, opened)));
		when(guests.markGuestOpened("event-id", guest.getToken(), NOW)).thenReturn(Optional.empty());
		service.getInvitation(guest.getToken());
		verify(guests, times(1)).markGuestOpened("event-id", guest.getToken(), NOW);
	}

	@Test
	void hidesOnConfirmationUntilR8EvenIfTheInvitationWasOpened() {
		Guest guest = guest(101, InvitationStatus.OPENED, NOW);
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(event(EventStatus.PUBLISHED, LinkVisibility.ON_CONFIRMATION, guest)));
		var view = service.getInvitation(guest.getToken());
		assertThat(view.onlineAccess()).isNull();
		assertThat(view.onlineAccessInfo().platform()).isEqualTo("Zoom");
	}

	@Test
	void finishedEventsRemainPublicAndDoNotWriteOrExposeAccess() {
		Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
		when(events.findByGuestToken(guest.getToken())).thenReturn(Optional.of(event(EventStatus.FINISHED, LinkVisibility.ALWAYS, guest)));
		var view = service.getInvitation(guest.getToken());
		assertThat(view.status()).isEqualTo(EventStatus.FINISHED);
		assertThat(view.onlineAccess()).isNull();
		verifyNoInteractions(guests);
	}
}
