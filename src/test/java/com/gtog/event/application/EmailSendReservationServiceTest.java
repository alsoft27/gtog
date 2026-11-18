package com.gtog.event.application;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSendReservationServiceTest {
	@Mock GuestEmailSendReservationPort guests;
	@Mock HostSendQuotaPort hosts;
	private final EmailSendPolicy policy = new EmailSendPolicy(3, Duration.ofHours(1), 100, 20);
	private final Event event = com.gtog.event.InvitationTestFixtures.event(EventStatus.PUBLISHED, null);
	private final Guest guest = guest(101);
	private final GuestEmailReservation ticket = new GuestEmailReservation("event-id", "host-1", guest.getId(), guest.getEmail(), 0, null, NOW, 1);
	private final HostSendQuotaReservation host = new HostSendQuotaReservation("host-1", NOW);
	private EmailSendReservationService service() { return new EmailSendReservationService(guests, hosts, policy, Clock.fixed(NOW, ZoneOffset.UTC)); }
	private void reserveGuest() {
		when(guests.reserve(event.getId(), event.getHostId(), guest.getId(), guest.getEmail(), NOW, policy))
				.thenReturn(new GuestEmailReservationResult(ticket, null));
	}
	@Test void aSkippedGuestConsumesNoQuota() {
		assertThat(service().reserve(event, guest, true).reason()).isEqualTo(EmailSendSkipReason.ALREADY_ANSWERED);
		verifyNoInteractions(guests, hosts);
	}
	@Test void aThrottledGuestDoesNotReserveHostQuota() {
		when(guests.reserve(event.getId(), event.getHostId(), guest.getId(), guest.getEmail(), NOW, policy))
				.thenReturn(new GuestEmailReservationResult(null, EmailSendSkipReason.THROTTLED));
		assertThat(service().reserve(event, guest, false).reason()).isEqualTo(EmailSendSkipReason.THROTTLED);
		verifyNoInteractions(hosts);
	}
	@Test void aDailyLimitReleasesTheGuestReservation() {
		reserveGuest(); when(hosts.reserve("host-1", NOW, 100)).thenReturn(Optional.empty()); when(guests.release(ticket)).thenReturn(true);
		assertThat(service().reserve(event, guest, false).reason()).isEqualTo(EmailSendSkipReason.DAILY_LIMIT);
		var order = inOrder(guests, hosts);
		order.verify(guests).reserve(event.getId(), event.getHostId(), guest.getId(), guest.getEmail(), NOW, policy);
		order.verify(hosts).reserve("host-1", NOW, 100); order.verify(guests).release(ticket);
	}
	@Test void compensationReleasesTheHostBeforeTheGuest() {
		when(hosts.release(host)).thenReturn(true); when(guests.release(ticket)).thenReturn(true);
		assertThat(service().compensate(new EmailSendReservation(ticket, host))).isTrue();
		var order = inOrder(hosts, guests); order.verify(hosts).release(host); order.verify(guests).release(ticket);
	}
	@Test void aFailedHostCompensationKeepsTheGuestReserved() {
		when(hosts.release(host)).thenReturn(false);
		assertThat(service().compensate(new EmailSendReservation(ticket, host))).isFalse(); verifyNoInteractions(guests);
	}
	@Test void aFailedGuestCompensationIsReportedWithoutAnAutomaticRetry() {
		when(hosts.release(host)).thenReturn(true); when(guests.release(ticket)).thenReturn(false);
		assertThat(service().compensate(new EmailSendReservation(ticket, host))).isFalse(); verify(guests).release(ticket);
		verifyNoMoreInteractions(guests);
	}
	@Test void anUncertainHostReservationDoesNotReleaseTheGuest() {
		reserveGuest(); when(hosts.reserve("host-1", NOW, 100)).thenThrow(new IllegalStateException("uncertain"));
		assertThatThrownBy(() -> service().reserve(event, guest, false)).isInstanceOf(IllegalStateException.class);
		verify(guests, never()).release(any());
	}
}
