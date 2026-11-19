package com.gtog.event.application;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.ConfirmWhatsAppSentCommand;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmWhatsAppSentServiceTest {
	private final EventRepositoryPort events = mock(EventRepositoryPort.class);
	private final GuestWhatsAppConfirmationPort confirmations = mock(GuestWhatsAppConfirmationPort.class);
	private final ConfirmWhatsAppSentService service = new ConfirmWhatsAppSentService(events, confirmations, new WhatsAppLinkPolicy(100), Clock.fixed(NOW, ZoneOffset.UTC));
	private final ConfirmWhatsAppSentCommand command = new ConfirmWhatsAppSentCommand("host-1", "event-id", List.of("guest-101"));

	@Test void theInjectedClockIsPassedToTheAtomicPortWithoutSavingTheEvent() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null))));
		when(confirmations.confirm("event-id", "host-1", "guest-101", NOW)).thenReturn(true);
		assertThat(service.confirm(command)).containsExactly(new WhatsAppConfirmationResult("guest-101", WhatsAppConfirmationStatus.MARKED));
		verify(confirmations).confirm("event-id", "host-1", "guest-101", NOW); verify(events, never()).save(any());
	}
	@Test void missingSelectionIsRejectedBeforeAnyWrite() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null))));
		assertThatThrownBy(() -> service.confirm(new ConfirmWhatsAppSentCommand("host-1", "event-id", null))).isInstanceOf(InvalidInvitationBatchException.class);
		verifyNoInteractions(confirmations);
	}
	@Test void aGuestRemovedAfterSelectionIsReportedWithoutAborting() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null))), Optional.of(event(EventStatus.PUBLISHED, null)));
		assertThat(service.confirm(command)).containsExactly(new WhatsAppConfirmationResult("guest-101", WhatsAppConfirmationStatus.NOT_FOUND));
	}
	@Test void aCancellationDuringTheBatchIsAConflict() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null))), Optional.of(event(EventStatus.CANCELLED, null, guest(101, InvitationStatus.NOT_SENT, null))));
		assertThatThrownBy(() -> service.confirm(command)).isInstanceOf(EventInvitationSendingNotAllowedException.class);
	}
}
