package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.gtog.event.GuestTestFixtures.event;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventGuestsTest {

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED"})
	void allowsAddingEditingAndRemovingGuestsInOpenEvents(EventStatus status) {
		Event event = event("event-1", status, 0L);
		Guest first = guest(1);
		event.addGuest(first);
		Guest replacement = first.editContact("Changed", first.getEmail(), null, phone -> phone);
		event.updateGuest(first.getId(), replacement);
		assertThat(event.getGuest(first.getId()).getName()).isEqualTo("Changed");
		event.removeGuest(first.getId());
		assertThat(event.getGuests()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"FINISHED", "CANCELLED"})
	void rejectsAllGuestMutationsInClosedEvents(EventStatus status) {
		Guest first = guest(1);
		Event event = event("event-1", status, 0L, first);
		assertThatThrownBy(() -> event.addGuest(guest(2))).isInstanceOf(EventGuestsNotEditableException.class);
		assertThatThrownBy(() -> event.updateGuest(first.getId(), first)).isInstanceOf(EventGuestsNotEditableException.class);
		assertThatThrownBy(() -> event.removeGuest(first.getId())).isInstanceOf(EventGuestsNotEditableException.class);
		assertThat(event.getGuests()).containsExactly(first);
	}

	@Test
	void rejectsDuplicateNormalizedEmailInTheSameEvent() {
		Event event = event("event-1", EventStatus.DRAFT, 0L, guest(1));
		Guest duplicate = Guest.create("Other", " GUEST1@EXAMPLE.COM ", null, token(2), phone -> phone);
		assertThatThrownBy(() -> event.addGuest(duplicate)).isInstanceOf(DuplicateGuestEmailException.class);
	}

	@Test
	void rejectsDuplicateNormalizedPhoneInTheSameEvent() {
		Guest first = Guest.create("Ana", null, "+34612345678", token(1), phone -> phone);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first);
		Guest duplicate = Guest.create("Other", null, "+34 612 345 678", token(2), phone -> "+34612345678");
		assertThatThrownBy(() -> event.addGuest(duplicate)).isInstanceOf(DuplicateGuestPhoneException.class);
	}

	@Test
	void doesNotAllowTwoGuestsInTheSameEventToHaveTheSameToken() {
		Guest first = guest(1);
		Guest duplicate = Guest.create("Other", "other@example.com", null, first.getToken(), phone -> phone);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first);
		assertThatThrownBy(() -> event.addGuest(duplicate)).isInstanceOf(DuplicateGuestTokenException.class);
		assertThat(event.getGuests()).containsExactly(first);
	}

	@Test
	void cannotAddTheSameGuestTwice() {
		Guest first = guest(1);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first);
		assertThatThrownBy(() -> event.addGuest(first)).isInstanceOf(DuplicateGuestTokenException.class);
	}

	@Test
	void allowsTheSameContactInDifferentEvents() {
		Event first = event("event-1", EventStatus.DRAFT, 0L);
		Event second = event("event-2", EventStatus.DRAFT, 0L);
		first.addGuest(guest(1));
		second.addGuest(Guest.create("Other", "guest1@example.com", null, token(2), phone -> phone));
		assertThat(second.getGuests()).hasSize(1);
	}

	@Test
	void editingExcludesTheGuestItselfButRejectsOtherGuestsContacts() {
		Guest first = guest(1);
		Guest second = guest(2);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first, second);
		event.updateGuest(first.getId(), first.editContact("Changed", first.getEmail(), null, phone -> phone));
		assertThatThrownBy(() -> event.updateGuest(first.getId(),
				first.editContact("Changed", second.getEmail(), null, phone -> phone)))
				.isInstanceOf(DuplicateGuestEmailException.class);
	}

	@Test
	void editingRejectsAnotherGuestsPhone() {
		Guest first = guest(1);
		Guest second = Guest.create("Second", null, "+34612345678", token(2), phone -> phone);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first, second);
		assertThatThrownBy(() -> event.updateGuest(first.getId(),
				first.editContact("Changed", null, second.getPhoneE164(), phone -> phone)))
				.isInstanceOf(DuplicateGuestPhoneException.class);
	}

	@Test
	void rejectsUnknownGuestsAndDoesNotRemoveOtherGuests() {
		Guest first = guest(1);
		Event event = event("event-1", EventStatus.DRAFT, 0L, first);
		assertThatThrownBy(() -> event.getGuest("missing")).isInstanceOf(GuestNotFoundException.class);
		assertThatThrownBy(() -> event.updateGuest("missing", first)).isInstanceOf(GuestNotFoundException.class);
		assertThatThrownBy(() -> event.removeGuest("missing")).isInstanceOf(GuestNotFoundException.class);
		assertThat(event.getGuests()).containsExactly(first);
	}

	@Test
	void guestCollectionCannotBeModifiedOutsideTheAggregate() {
		Event event = event("event-1", EventStatus.DRAFT, 0L);
		assertThatThrownBy(() -> event.getGuests().add(guest(1))).isInstanceOf(UnsupportedOperationException.class);
	}
}
