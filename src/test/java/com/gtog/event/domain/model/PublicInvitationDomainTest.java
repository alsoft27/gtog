package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;

class PublicInvitationDomainTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private static Guest join(String name, String email, String phone) {
		return Guest.joinPublicInvitation(name, email, phone, token(99), value -> value.replace(" ", ""), NOW);
	}
	private static Event event(EventStatus status, LocalDateTime deadline, Guest... guests) {
		return Event.reconstituteBuilder().id("event").hostId("host").title("Evento").status(status)
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("Europe/Madrid")
				.responseDeadline(deadline).allowResponseChange(false).responseOptions(List.of()).guests(List.of(guests))
				.publicInvitation(new PublicInvitation(true, status == EventStatus.DRAFT ? null : token(100))).build();
	}
	@Test void aPublicGuestMayJoinWithOnlyAStrippedNameAndTheInjectedInstant() {
		Guest guest = join("  Ana  ", null, null);
		assertThat(guest.getName()).isEqualTo("Ana"); assertThat(guest.getEmail()).isNull(); assertThat(guest.getPhoneE164()).isNull();
		assertThat(guest.getSource()).isEqualTo(GuestSource.PUBLIC_LINK); assertThat(guest.getJoinedAt()).isEqualTo(NOW);
		assertThat(guest.getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT); assertThat(guest.getDeliveryChannel()).isNull();
	}
	@Test void hostGuestsStillRequireAtLeastOneContact() {
		assertThatThrownBy(() -> Guest.create("Host guest", null, null, token(1), value -> value)).isInstanceOf(MissingGuestContactException.class);
		var exception = catchThrowableOfType(() -> GuestBatch.prepare(event(EventStatus.DRAFT, null),
				List.of(new GuestInput("Host guest", null, null)), 100, value -> value, () -> token(1)), GuestBatchRejectedException.class);
		assertThat(exception.getErrors()).containsExactly(new GuestBatchError(0, GuestErrorCode.NO_CONTACT));
	}
	@Test void publicContactsUseTheSameNormalization() {
		Guest guest = join("Ana", " ANA@EXAMPLE.COM ", "+34 612 345 678");
		assertThat(guest.getEmail()).isEqualTo("ana@example.com"); assertThat(guest.getPhoneE164()).isEqualTo("+34612345678");
	}
	@ParameterizedTest @ValueSource(strings = { "", " ", "Ana\n", "A\tB", "A\u0000B" })
	void blankNamesAndControlCharactersAreRejected(String name) {
		assertThatThrownBy(() -> join(name, null, null)).isInstanceOf(PublicJoinValidationException.class);
	}
	@Test void aNameWith101CharactersIsRejectedWithoutTruncation() {
		assertThat(join("a".repeat(100), null, null).getName()).hasSize(100);
		var error = catchThrowableOfType(() -> join("a".repeat(101), null, null), PublicJoinValidationException.class);
		assertThat(error.getErrors()).containsExactly(new PublicJoinFieldError("name", PublicJoinFieldError.Code.NAME_TOO_LONG));
	}
	@Test void invalidEmailAndPhoneAreBothReportedWithoutValues() {
		var error = catchThrowableOfType(() -> join("Ana", "private@@example.com", "bad-phone"), PublicJoinValidationException.class);
		assertThat(error.getErrors()).containsExactly(new PublicJoinFieldError("email", PublicJoinFieldError.Code.INVALID_EMAIL),
				new PublicJoinFieldError("phone", PublicJoinFieldError.Code.INVALID_PHONE));
		assertThat(error.getMessage()).doesNotContain("private@@example.com", "bad-phone");
	}
	@Test void editingAPublicGuestKeepsItsOriginJoinedAtAndDeliveryChannel() {
		Guest original = Guest.reconstitute("guest", "Ana", "ana@example.com", null, token(1), InvitationStatus.OPENED,
				DeliveryChannel.WHATSAPP, NOW, NOW, 0, null, GuestSource.PUBLIC_LINK, NOW);
		Guest changed = original.editContact("Ana nueva", null, null, value -> value);
		assertThat(changed.getSource()).isEqualTo(GuestSource.PUBLIC_LINK); assertThat(changed.getJoinedAt()).isEqualTo(NOW);
		assertThat(changed.getDeliveryChannel()).isEqualTo(DeliveryChannel.WHATSAPP); assertThat(changed.getToken()).isEqualTo(original.getToken());
	}
	@Test void oneConflictingContactDiscardsBothWithoutChangingIdentityOrJoinedAt() {
		Guest existing = Guest.create("Host guest", "same@example.com", null, token(1), value -> value);
		Guest candidate = join("Ana", " SAME@EXAMPLE.COM ", "+34612345678");
		Guest changed = event(EventStatus.PUBLISHED, null, existing).discardConflictingPublicContact(candidate);
		assertThat(changed.getEmail()).isNull(); assertThat(changed.getPhoneE164()).isNull();
		assertThat(changed.getId()).isEqualTo(candidate.getId()); assertThat(changed.getToken()).isEqualTo(candidate.getToken());
		assertThat(changed.getJoinedAt()).isEqualTo(NOW);
	}
	@Test void theDeadlineIsInclusiveAndTheEndIsExclusiveUsingTheResponsePolicy() {
		Event event = event(EventStatus.PUBLISHED, LocalDateTime.of(2027, 1, 1, 11, 0));
		var limits = new PublicJoinLimits(25, 500);
		assertThat(event.canJoinPublicInvitation(NOW.minusNanos(1), limits)).isTrue();
		assertThat(event.canJoinPublicInvitation(NOW, limits)).isTrue();
		assertThat(event.canJoinPublicInvitation(NOW.plusNanos(1), limits)).isFalse();
		Event withoutDeadline = event(EventStatus.PUBLISHED, null);
		assertThat(withoutDeadline.canJoinPublicInvitation(withoutDeadline.endsAtInstant().minusNanos(1), limits)).isTrue();
		assertThat(withoutDeadline.canJoinPublicInvitation(withoutDeadline.endsAtInstant(), limits)).isFalse();
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "CANCELLED", "FINISHED" })
	void onlyPublishedEventsAdmitAPublicGuest(EventStatus status) {
		assertThat(event(status, null).canJoinPublicInvitation(NOW, new PublicJoinLimits(25, 500))).isFalse();
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "CANCELLED", "FINISHED" })
	void closedStatesRejectChangingTheLink(EventStatus status) {
		assertThatThrownBy(() -> event(status, null).ensurePublicInvitationEditable()).isInstanceOf(PublicInvitationNotEditableException.class);
	}
	@Test void aPublicGuestParticipatesInTheNormalContactUniquenessInvariant() {
		Guest publicGuest = join("Ana", "same@example.com", null);
		assertThatThrownBy(() -> event(EventStatus.PUBLISHED, null, publicGuest).addGuest(
				Guest.create("Host guest", "same@example.com", null, token(3), value -> value))).isInstanceOf(DuplicateGuestEmailException.class);
	}
	@Test void thePublicCredentialRejectsInvalidValuesAndRedactsItsDiagnosticRepresentation() {
		assertThatThrownBy(() -> new PublicInvitation(true, "not-a-token")).isInstanceOf(InvalidPublicInvitationTokenException.class);
		assertThat(new PublicInvitation(true, token(123)).toString()).doesNotContain(token(123));
	}
}
