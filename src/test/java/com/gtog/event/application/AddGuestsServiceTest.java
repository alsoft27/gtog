package com.gtog.event.application;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddGuestsServiceTest {
	private final EventRepositoryPort events = mock(EventRepositoryPort.class);
	private final EventGuestRepositoryPort guests = mock(EventGuestRepositoryPort.class);
	private final PhoneNormalizerPort phones = mock(PhoneNormalizerPort.class);
	private final GuestTokenGeneratorPort tokens = mock(GuestTokenGeneratorPort.class);
	private final AddGuestsService service = new AddGuestsService(events, guests, phones, tokens, new GuestLimits(100, 500));
	@Test void checksOwnershipBeforeListValidation() {
		when(events.findById("event")).thenReturn(Optional.of(event("event", EventStatus.DRAFT, 0L)));
		assertThatThrownBy(() -> service.add("other", "event", null)).isInstanceOf(EventNotFoundException.class);
		verifyNoInteractions(guests, phones, tokens);
	}
	@Test void checksStateBeforeListValidation() {
		when(events.findById("event")).thenReturn(Optional.of(event("event", EventStatus.CANCELLED, 0L)));
		assertThatThrownBy(() -> service.add("host-1", "event", null)).isInstanceOf(EventGuestsNotEditableException.class);
		verifyNoInteractions(guests, phones, tokens);
	}
	@Test void invalidInputsNeverReachTheWritePort() {
		when(events.findById("event")).thenReturn(Optional.of(event("event", EventStatus.DRAFT, 0L)));
		assertThatThrownBy(() -> service.add("host-1", "event", List.of(new GuestInput("", null, null)))).isInstanceOf(GuestBatchRejectedException.class);
		verifyNoInteractions(guests, tokens);
	}
	@Test void writesThePreparedBatchOnceWithoutSavingTheWholeEvent() {
		when(events.findById("event")).thenReturn(Optional.of(event("event", EventStatus.DRAFT, 0L)));
		when(tokens.generate()).thenReturn(token(1), token(2));
		var result = service.add("host-1", "event", List.of(new GuestInput("A", "a@example.com", null), new GuestInput("B", "b@example.com", null)));
		verify(guests).addGuests("event", "host-1", result); verify(events, never()).save(any());
		assertThat(result).extracting(Guest::getName).containsExactly("A", "B");
	}
}
