package com.gtog.event.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.gtog.event.domain.model.EventNotFoundException;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import static com.gtog.event.AttendanceTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class GetEventDashboardServiceTest {
	@Mock private EventRepositoryPort events;
	@Mock private GuestResponseRepositoryPort responses;

	@ParameterizedTest
	@EnumSource(EventStatus.class)
	void readsTheOwnersDashboardInEveryStateWithOneResponseReadAndNoWrites(EventStatus state) {
		when(events.findById("event-id")).thenReturn(Optional.of(event(state)));
		when(responses.findByEventIds(Set.of("event-id"))).thenReturn(List.of());
		var summary = new GetEventDashboardService(events, responses).getDashboard("host-1", "event-id");
		assertThat(summary.totals().guests()).isEqualTo(3);
		verify(responses, times(1)).findByEventIds(Set.of("event-id"));
		verifyNoMoreInteractions(responses);
		verify(events, never()).save(any());
	}

	@Test
	void unknownEventIs404BeforeReadingResponses() {
		assertThatThrownBy(() -> new GetEventDashboardService(events, responses).getDashboard("host-1", "missing"))
				.isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(responses);
	}

	@Test
	void anotherHostsEventIs404BeforeReadingResponses() {
		when(events.findById("event-id")).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));
		assertThatThrownBy(() -> new GetEventDashboardService(events, responses).getDashboard("host-2", "event-id"))
				.isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(responses);
		verify(events, never()).save(any());
	}
}
