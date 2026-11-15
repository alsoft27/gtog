package com.gtog.event.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventAttendanceSummary;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.EventSummary;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;

import static com.gtog.event.AttendanceTestFixtures.response;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

	@Mock
	private EventRepositoryPort eventRepositoryPort;

	@Mock
	private GuestResponseRepositoryPort responses;

	@Test
	void getEventByIdReturnsTheEventWhenItExists() {
		Event event = anEvent("event-1", "host-1");
		when(eventRepositoryPort.findById("event-1")).thenReturn(Optional.of(event));

		EventQueryService service = new EventQueryService(eventRepositoryPort, responses);

		assertThat(service.getEventById("host-1", "event-1")).isSameAs(event);
	}

	@Test
	void getEventByIdThrowsWhenTheEventDoesNotExist() {
		when(eventRepositoryPort.findById("missing")).thenReturn(Optional.empty());

		EventQueryService service = new EventQueryService(eventRepositoryPort, responses);

		assertThatThrownBy(() -> service.getEventById("host-1", "missing")).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void listEventsByHostPreservesEventOrderAndAddsZeroTotalsWhenThereAreNoGuests() {
		Event first = anEvent("event-1", "host-1");
		Event second = anEvent("event-2", "host-1");
		when(eventRepositoryPort.findByHostId("host-1")).thenReturn(List.of(first, second));
		when(responses.findByEventIds(Set.of("event-1", "event-2"))).thenReturn(List.of());

		EventQueryService service = new EventQueryService(eventRepositoryPort, responses);

		assertThat(service.listEventsByHost("host-1")).containsExactly(
				new EventSummary(first, new EventAttendanceSummary.Totals(0, 0, 0, 0)),
				new EventSummary(second, new EventAttendanceSummary.Totals(0, 0, 0, 0)));
	}

	@Test
	void listEventsByHostReturnsAnEmptyListWhenTheHostHasNoEvents() {
		when(eventRepositoryPort.findByHostId("host-without-events")).thenReturn(List.of());

		EventQueryService service = new EventQueryService(eventRepositoryPort, responses);

		assertThat(service.listEventsByHost("host-without-events")).isEmpty();
		verifyNoInteractions(responses);
	}

	@Test
	void listsTotalsForEveryEventWithOneBatchReadAndIgnoresOrphanResponses() {
		var first = com.gtog.event.AttendanceTestFixtures.event(EventStatus.PUBLISHED);
		var second = com.gtog.event.GuestTestFixtures.event("second-event", EventStatus.PUBLISHED, 0L,
				com.gtog.event.GuestTestFixtures.guest(10), com.gtog.event.GuestTestFixtures.guest(11));
		var replies = List.of(response(first, 1, "yes-id"), response(first, 2, "no-id"),
				response(second, 0, second.getResponseOptions().getFirst().id()),
				new GuestResponse(first.getId(), "orphan-token", "yes-id", null, null, NOW, NOW));
		when(eventRepositoryPort.findByHostId("host-1")).thenReturn(List.of(first, second));
		when(responses.findByEventIds(Set.of(first.getId(), second.getId()))).thenReturn(replies);

		var result = new EventQueryService(eventRepositoryPort, responses).listEventsByHost("host-1");

		assertThat(result).extracting(EventSummary::totals).containsExactly(
				new EventAttendanceSummary.Totals(3, 2, 1, 1), new EventAttendanceSummary.Totals(2, 1, 1, 1));
		verify(eventRepositoryPort).findByHostId("host-1");
		verify(responses).findByEventIds(Set.of(first.getId(), second.getId()));
		verifyNoMoreInteractions(eventRepositoryPort, responses);
	}

	@Test
	void countsAnUnknownOptionAsAnsweredButNotAttendingInTheListing() {
		var event = com.gtog.event.AttendanceTestFixtures.event(EventStatus.PUBLISHED);
		when(eventRepositoryPort.findByHostId("host-1")).thenReturn(List.of(event));
		when(responses.findByEventIds(Set.of(event.getId()))).thenReturn(List.of(response(event, 0, "removed-option")));

		var result = new EventQueryService(eventRepositoryPort, responses).listEventsByHost("host-1");

		assertThat(result).extracting(EventSummary::totals)
				.containsExactly(new EventAttendanceSummary.Totals(3, 1, 2, 0));
	}

	private Event anEvent(String id, String hostId) {
		List<ResponseOption> responseOptions = List.of(ResponseOption.create("Asisto", true),
				ResponseOption.create("No asisto", false));
		return Event.reconstituteBuilder()
				.id(id)
				.hostId(hostId)
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
