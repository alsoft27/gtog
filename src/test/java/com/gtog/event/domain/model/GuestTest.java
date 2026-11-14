package com.gtog.event.domain.model;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void rejectsMissingName(String name) {
		assertThatThrownBy(() -> Guest.create(name, "ana@example.com", null, token(1), phone -> phone))
				.isInstanceOf(BlankGuestNameException.class);
	}

	@Test
	void requiresAtLeastOneNonBlankContact() {
		assertThatThrownBy(() -> Guest.create("Ana", "  ", null, token(1), phone -> phone))
				.isInstanceOf(MissingGuestContactException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"invalid", "ana@", "ana@example", "a b@example.com"})
	void rejectsInvalidEmailEvenWhenPhoneIsPresent(String email) {
		assertThatThrownBy(() -> Guest.create("Ana", email, "+34612345678", token(1), phone -> phone))
				.isInstanceOf(InvalidGuestEmailException.class);
	}

	@Test
	void normalizesContactsAndAppliesInitialInvitationState() {
		Guest guest = Guest.create(" Ana ", " ANA@Example.COM ", " +34 612 345 678 ", token(1), phone -> "+34612345678");
		assertThat(guest.getId()).isNotBlank();
		assertThat(guest.getName()).isEqualTo("Ana");
		assertThat(guest.getEmail()).isEqualTo("ana@example.com");
		assertThat(guest.getPhoneE164()).isEqualTo("+34612345678");
		assertThat(guest.getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(guest.getDeliveryChannel()).isNull();
		assertThat(guest.getSentAt()).isNull();
		assertThat(guest.getOpenedAt()).isNull();
	}

	@Test
	void acceptsPhoneAsTheOnlyContact() {
		Guest guest = Guest.create("Ana", null, "+34612345678", token(1), phone -> phone);
		assertThat(guest.getEmail()).isNull();
		assertThat(guest.getPhoneE164()).isEqualTo("+34612345678");
	}

	@Test
	void rejectsPhoneWhenNormalizationFailsEvenWhenEmailIsPresent() {
		assertThatThrownBy(() -> Guest.create("Ana", "ana@example.com", "invalid", token(1), phone -> {
			throw new InvalidGuestPhoneException();
		})).isInstanceOf(InvalidGuestPhoneException.class);
	}

	@Test
	void doesNotStoreNonE164OutputFromThePhonePort() {
		assertThatThrownBy(() -> Guest.create("Ana", null, "612345678", token(1), phone -> "612345678"))
				.isInstanceOf(InvalidGuestPhoneException.class);
	}

	@Test
	void editingPreservesIdentityTokenAndDeliveryHistory() {
		Instant time = Instant.parse("2026-11-14T10:00:00Z");
		Guest original = Guest.reconstitute("guest-id", "Ana", "ana@example.com", null, token(1),
				InvitationStatus.OPENED, DeliveryChannel.EMAIL, time, time);
		Guest edited = original.editContact("Ana Maria", null, "+34612345678", phone -> phone);
		assertThat(edited.getId()).isEqualTo(original.getId());
		assertThat(edited.getToken()).isEqualTo(original.getToken());
		assertThat(edited.getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(edited.getDeliveryChannel()).isEqualTo(DeliveryChannel.EMAIL);
		assertThat(edited.getSentAt()).isEqualTo(time);
		assertThat(edited.getOpenedAt()).isEqualTo(time);
		assertThat(edited.getEmail()).isNull();
		assertThat(original.getName()).isEqualTo("Ana");
	}
}
