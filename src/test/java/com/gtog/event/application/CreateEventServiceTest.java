package com.gtog.event.application;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.model.Venue;
import com.gtog.event.domain.port.in.CreateEventCommand;
import com.gtog.event.domain.port.out.EventRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventServiceTest {

	@Mock
	private EventRepositoryPort eventRepositoryPort;

	@Test
	void createsADraftEventAndDelegatesPersistenceToTheRepositoryPort() {
		Venue venue = new Venue("Sala Apolo", "Carrer Nou de la Rambla, 113, Barcelona", 41.3767, 2.1662,
				"ChIJT7Xj1uOipBIRdKY0X_0V7Xk", null);
		CreateEventCommand command = new CreateEventCommand(
				"host-1",
				"Cumpleaños",
				"Descripción",
				LocalDateTime.of(2026, 9, 1, 20, 0),
				LocalDateTime.of(2026, 9, 1, 23, 0),
				"Europe/Madrid",
				Modality.IN_PERSON,
				null,
				null,
				null,
				null,
				venue,
				null);
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
		Event savedEvent = Event.reconstituteBuilder()
				.id("event-1")
				.hostId(command.hostId())
				.title(command.title())
				.description(command.description())
				.startsAt(command.startsAt())
				.endsAt(command.endsAt())
				.timeZone(command.timeZone())
				.modality(command.modality())
				.status(EventStatus.DRAFT)
				.responseOptions(responseOptions)
				.allowComment(false)
				.allowResponseChange(true)
				.version(0L)
				.build();
		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		when(eventRepositoryPort.save(eventCaptor.capture())).thenReturn(savedEvent);

		CreateEventService service = new CreateEventService(eventRepositoryPort);
		Event result = service.createEvent(command);

		assertThat(result).isSameAs(savedEvent);
		Event eventPassedToRepository = eventCaptor.getValue();
		assertThat(eventPassedToRepository.getHostId()).isEqualTo(command.hostId());
		assertThat(eventPassedToRepository.getTitle()).isEqualTo(command.title());
		assertThat(eventPassedToRepository.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(eventPassedToRepository.getVersion()).isNull();
	}
}
