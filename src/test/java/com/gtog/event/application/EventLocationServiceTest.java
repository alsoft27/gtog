package com.gtog.event.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.model.Venue;
import com.gtog.event.domain.port.in.ReplaceOnlineAccessCommand;
import com.gtog.event.domain.port.in.ReplaceVenueCommand;
import com.gtog.event.domain.port.out.EventRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventLocationServiceTest {

	@Mock
	private EventRepositoryPort eventRepositoryPort;

	@Test
	void replacesTheVenueOfAnExistingInPersonEventAndSavesIt() {
		Event event = anInPersonEvent("event-1");
		when(eventRepositoryPort.findById("event-1")).thenReturn(Optional.of(event));
		when(eventRepositoryPort.save(any())).thenReturn(event);
		Venue newVenue = new Venue("Otra sala", "Otra direccion", 0.0, 0.0, "other-place-id", null);

		EventLocationService service = new EventLocationService(eventRepositoryPort);
		Event result = service.replaceVenue(new ReplaceVenueCommand("event-1", newVenue));

		assertThat(result).isSameAs(event);
		assertThat(event.getVenue()).isEqualTo(newVenue);
		verify(eventRepositoryPort).save(event);
	}

	@Test
	void replaceVenueThrowsWhenTheEventDoesNotExist() {
		when(eventRepositoryPort.findById("missing")).thenReturn(Optional.empty());

		EventLocationService service = new EventLocationService(eventRepositoryPort);
		Venue venue = new Venue("Sala", "Direccion", 0.0, 0.0, "place-id", null);

		assertThatThrownBy(() -> service.replaceVenue(new ReplaceVenueCommand("missing", venue)))
				.isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void replacesTheOnlineAccessOfAnExistingOnlineEventAndSavesIt() {
		Event event = anOnlineEvent("event-2");
		when(eventRepositoryPort.findById("event-2")).thenReturn(Optional.of(event));
		when(eventRepositoryPort.save(any())).thenReturn(event);
		OnlineAccess newOnlineAccess = new OnlineAccess("Teams", "https://teams.microsoft.com/x", null, null, null,
				LinkVisibility.ALWAYS, null);

		EventLocationService service = new EventLocationService(eventRepositoryPort);
		Event result = service.replaceOnlineAccess(new ReplaceOnlineAccessCommand("event-2", newOnlineAccess));

		assertThat(result).isSameAs(event);
		assertThat(event.getOnlineAccess()).isEqualTo(newOnlineAccess);
		verify(eventRepositoryPort).save(event);
	}

	@Test
	void replaceOnlineAccessThrowsWhenTheEventDoesNotExist() {
		when(eventRepositoryPort.findById("missing")).thenReturn(Optional.empty());

		EventLocationService service = new EventLocationService(eventRepositoryPort);
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null);

		assertThatThrownBy(
				() -> service.replaceOnlineAccess(new ReplaceOnlineAccessCommand("missing", onlineAccess)))
				.isInstanceOf(EventNotFoundException.class);
	}

	private Event anInPersonEvent(String id) {
		Venue venue = new Venue("Sala Apolo", "Carrer Nou de la Rambla, 113, Barcelona", 41.3767, 2.1662,
				"ChIJT7Xj1uOipBIRdKY0X_0V7Xk", null);
		return Event.reconstituteBuilder()
				.id(id)
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(LocalDateTime.of(2026, 9, 1, 20, 0))
				.endsAt(LocalDateTime.of(2026, 9, 1, 23, 0))
				.timeZone("Europe/Madrid")
				.modality(Modality.IN_PERSON)
				.status(EventStatus.DRAFT)
				.responseOptions(List.of(ResponseOption.create("Asisto", true), ResponseOption.create("No asisto", false)))
				.allowComment(false)
				.allowResponseChange(true)
				.venue(venue)
				.version(0L)
				.build();
	}

	private Event anOnlineEvent(String id) {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null);
		return Event.reconstituteBuilder()
				.id(id)
				.hostId("host-1")
				.title("Cumpleaños")
				.description("Descripción")
				.startsAt(LocalDateTime.of(2026, 9, 1, 20, 0))
				.endsAt(LocalDateTime.of(2026, 9, 1, 23, 0))
				.timeZone("Europe/Madrid")
				.modality(Modality.ONLINE)
				.status(EventStatus.DRAFT)
				.responseOptions(List.of(ResponseOption.create("Asisto", true), ResponseOption.create("No asisto", false)))
				.allowComment(false)
				.allowResponseChange(true)
				.onlineAccess(onlineAccess)
				.version(0L)
				.build();
	}
}
