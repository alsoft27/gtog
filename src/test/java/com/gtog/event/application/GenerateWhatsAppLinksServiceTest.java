package com.gtog.event.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.GenerateWhatsAppLinksCommand;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerateWhatsAppLinksServiceTest {
	private final EventRepositoryPort events = mock(EventRepositoryPort.class);
	private final GuestResponseRepositoryPort responses = mock(GuestResponseRepositoryPort.class);
	private final GenerateWhatsAppLinksService service = new GenerateWhatsAppLinksService(events, responses,
			new WhatsAppInvitationComposer("https://gtog.example.com"), new WhatsAppLinkPolicy(100));
	private final GenerateWhatsAppLinksCommand all = new GenerateWhatsAppLinksCommand("host-1", "Anfitrión", "event-id", null);
	@Test void unknownEventsDoNotReadResponses() {
		assertThatThrownBy(() -> service.generate(all)).isInstanceOf(EventNotFoundException.class); verifyNoInteractions(responses);
	}
	@Test void ownershipIsCheckedBeforeReadingResponses() {
		when(events.findById(all.eventId())).thenReturn(Optional.of(event(EventStatus.PUBLISHED, null)));
		assertThatThrownBy(() -> service.generate(new GenerateWhatsAppLinksCommand("other-host", "Host", all.eventId(), null)))
				.isInstanceOf(EventNotFoundException.class); verifyNoInteractions(responses);
	}
	@Test void generationUsesOneBatchReadAndNeverSavesTheEvent() {
		var first = guest(101, InvitationStatus.NOT_SENT, null); var second = guest(202, InvitationStatus.SENT, null);
		var event = event(EventStatus.PUBLISHED, null, first, second);
		when(events.findById(all.eventId())).thenReturn(Optional.of(event));
		when(responses.findByEventIds(Set.of(event.getId()))).thenReturn(List.of());
		assertThat(service.generate(all)).extracting(WhatsAppLinkResult::guestId).containsExactly(first.getId(), second.getId());
		verify(events).findById(event.getId()); verifyNoMoreInteractions(events);
		verify(responses).findByEventIds(Set.of(event.getId())); verifyNoMoreInteractions(responses);
	}
}
