package com.gtog.event.domain.model;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class InvitationBatchSelectionTest {
	private final Guest first = guest(101);
	private final Guest second = guest(102);
	private final Event event = event("event", EventStatus.PUBLISHED, null, first, second);
	@Test void omittedIdsSelectAllGuestsInRegistrationOrder() {
		assertThat(InvitationBatchSelection.select(event, null, 20)).containsExactly(first, second);
	}
	@Test void explicitIdsPreserveTheRequestedOrder() {
		assertThat(InvitationBatchSelection.select(event, List.of(second.getId(), first.getId()), 20)).containsExactly(second, first);
	}
	@Test void anEmptySelectionIsRejected() {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, List.of(), 20)).isInstanceOf(InvalidInvitationBatchException.class);
	}
	@Test void duplicateIdsAreRejected() {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, List.of(first.getId(), first.getId()), 20)).isInstanceOf(InvalidInvitationBatchException.class);
	}
	@ParameterizedTest @NullAndEmptySource @ValueSource(strings = { " " })
	void invalidIdsAreRejected(String id) {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, Arrays.asList(id), 20)).isInstanceOf(InvalidInvitationBatchException.class);
	}
	@Test void anUnknownGuestIsRejected() {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, List.of("foreign"), 20)).isInstanceOf(GuestNotFoundException.class);
	}
	@Test void implicitSelectionCannotExceedTheMaximum() {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, null, 1)).isInstanceOf(InvalidInvitationBatchException.class);
	}
	@Test void explicitSelectionCannotExceedTheMaximum() {
		assertThatThrownBy(() -> InvitationBatchSelection.select(event, List.of(first.getId(), second.getId()), 1)).isInstanceOf(InvalidInvitationBatchException.class);
	}
}
