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
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.port.out.EventRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

	@Mock
	private EventRepositoryPort eventRepositoryPort;

	@Test
	void getEventByIdReturnsTheEventWhenItExists() {
		Event event = anEvent("event-1", "host-1");
		when(eventRepositoryPort.findById("event-1")).thenReturn(Optional.of(event));

		EventQueryService service = new EventQueryService(eventRepositoryPort);

		assertThat(service.getEventById("event-1")).isSameAs(event);
	}

	@Test
	void getEventByIdThrowsWhenTheEventDoesNotExist() {
		when(eventRepositoryPort.findById("missing")).thenReturn(Optional.empty());

		EventQueryService service = new EventQueryService(eventRepositoryPort);

		assertThatThrownBy(() -> service.getEventById("missing")).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void listEventsByHostReturnsTheEventsFromTheRepository() {
		Event first = anEvent("event-1", "host-1");
		Event second = anEvent("event-2", "host-1");
		when(eventRepositoryPort.findByHostId("host-1")).thenReturn(List.of(first, second));

		EventQueryService service = new EventQueryService(eventRepositoryPort);

		assertThat(service.listEventsByHost("host-1")).containsExactly(first, second);
	}

	@Test
	void listEventsByHostReturnsAnEmptyListWhenTheHostHasNoEvents() {
		when(eventRepositoryPort.findByHostId("host-without-events")).thenReturn(List.of());

		EventQueryService service = new EventQueryService(eventRepositoryPort);

		assertThat(service.listEventsByHost("host-without-events")).isEmpty();
	}

	private Event anEvent(String id, String hostId) {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
		return Event.reconstitute(id, hostId, "Cumpleaños", "Descripción", LocalDateTime.of(2026, 9, 1, 20, 0),
				LocalDateTime.of(2026, 9, 1, 23, 0), "Europe/Madrid", Modality.IN_PERSON, EventStatus.DRAFT,
				responseOptions, false, true, null, 0L);
	}
}
