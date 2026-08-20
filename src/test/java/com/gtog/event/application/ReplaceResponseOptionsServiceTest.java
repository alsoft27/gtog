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
import com.gtog.event.domain.model.ResponseOptionEdit;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsCommand;
import com.gtog.event.domain.port.out.EventRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplaceResponseOptionsServiceTest {

	@Mock
	private EventRepositoryPort eventRepositoryPort;

	@Test
	void replacesTheResponseOptionsOfAnExistingEventAndSavesIt() {
		Event event = anEvent("event-1");
		when(eventRepositoryPort.findById("event-1")).thenReturn(Optional.of(event));
		when(eventRepositoryPort.save(any())).thenReturn(event);

		ReplaceResponseOptionsCommand command = new ReplaceResponseOptionsCommand("event-1", List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), true, false, null);

		ReplaceResponseOptionsService service = new ReplaceResponseOptionsService(eventRepositoryPort);
		Event result = service.replaceResponseOptions(command);

		assertThat(result).isSameAs(event);
		assertThat(event.isAllowComment()).isTrue();
		assertThat(event.isAllowResponseChange()).isFalse();
		verify(eventRepositoryPort).save(event);
	}

	@Test
	void throwsWhenTheEventDoesNotExist() {
		when(eventRepositoryPort.findById("missing")).thenReturn(Optional.empty());

		ReplaceResponseOptionsCommand command = new ReplaceResponseOptionsCommand("missing", List.of(
				new ResponseOptionEdit(null, "Asisto", true),
				new ResponseOptionEdit(null, "No asisto", false)), false, true, null);

		ReplaceResponseOptionsService service = new ReplaceResponseOptionsService(eventRepositoryPort);

		assertThatThrownBy(() -> service.replaceResponseOptions(command)).isInstanceOf(EventNotFoundException.class);
	}

	private Event anEvent(String id) {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
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
				.responseOptions(responseOptions)
				.allowComment(false)
				.allowResponseChange(true)
				.version(0L)
				.build();
	}
}
