package com.gtog.event.application;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.InvitationCalendarData;
import com.gtog.event.domain.model.InvitationCalendarNotAvailableException;
import com.gtog.event.domain.model.InvitationNotFoundException;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.port.out.InvitationCalendarRendererPort;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.URL;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DownloadInvitationCalendarServiceTest {
	@Mock private EventRepositoryPort events;
	@Mock private GuestResponseRepositoryPort responses;
	@Mock private InvitationCalendarRendererPort renderer;
	private DownloadInvitationCalendarService service;

	@BeforeEach
	void setUp() { service = new DownloadInvitationCalendarService(events, responses, renderer, Clock.fixed(NOW, ZoneOffset.UTC)); }

	@Test
	void unknownTokenReturnsTheSameConstantExceptionAsThePublicPage() {
		assertThatThrownBy(() -> service.downloadCalendar("unknown-private-token"))
				.isInstanceOf(InvitationNotFoundException.class).hasMessage("Invitation not found");
		verifyNoInteractions(responses, renderer);
	}

	@Test
	void draftReturnsNotFoundBeforeLookingUpResponsesOrRendering() {
		when(events.findByGuestToken("private-token")).thenReturn(Optional.of(responseEvent(EventStatus.DRAFT, true, true, null)));
		assertThatThrownBy(() -> service.downloadCalendar("private-token")).isInstanceOf(InvitationNotFoundException.class)
				.hasMessage("Invitation not found");
		verifyNoInteractions(responses, renderer);
	}

	@Test
	void cancelledCannotBeDownloadedEvenThoughItsPublicPageExists() {
		var event = responseEvent(EventStatus.CANCELLED, true, true, null);
		String token = event.getGuests().getFirst().getToken();
		when(events.findByGuestToken(token)).thenReturn(Optional.of(event));
		assertThatThrownBy(() -> service.downloadCalendar(token)).isInstanceOf(InvitationCalendarNotAvailableException.class)
				.hasMessageNotContaining(token);
		verifyNoInteractions(responses, renderer);
	}

	@Test
	void finishedStillDownloadsWithoutGivingTheRendererAnyOnlineSecret() {
		var event = responseEvent(EventStatus.FINISHED, true, true, null, LinkVisibility.ALWAYS);
		String token = event.getGuests().getFirst().getToken();
		when(events.findByGuestToken(token)).thenReturn(Optional.of(event));
		when(renderer.render(any())).thenReturn(new byte[]{1});
		assertThat(service.downloadCalendar(token)).containsExactly((byte) 1);
		var data = org.mockito.ArgumentCaptor.forClass(InvitationCalendarData.class);
		verify(renderer).render(data.capture());
		assertThat(data.getValue().onlineUrl()).isNull();
	}

	@Test
	void passesTheEventIdAndInjectedTimeAndOnlyTheFilteredUrlWithoutSavingTheEvent() {
		var event = responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.ALWAYS);
		String token = event.getGuests().getFirst().getToken();
		when(events.findByGuestToken(token)).thenReturn(Optional.of(event));
		when(renderer.render(any())).thenReturn(new byte[]{1});
		service.downloadCalendar(token);
		var data = org.mockito.ArgumentCaptor.forClass(InvitationCalendarData.class);
		verify(renderer).render(data.capture());
		assertThat(data.getValue().eventId()).isEqualTo("event-id");
		assertThat(data.getValue().generatedAt()).isEqualTo(NOW);
		assertThat(data.getValue().onlineUrl()).isEqualTo(URL);
		verify(events, never()).save(any());
	}
}
