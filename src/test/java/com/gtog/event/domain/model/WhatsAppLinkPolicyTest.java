package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;

class WhatsAppLinkPolicyTest {
	private final WhatsAppLinkPolicy policy = new WhatsAppLinkPolicy(100);
	private Guest guest(String phone) { return Guest.reconstitute("guest", "Ana", "ana@example.com", phone, token(101), InvitationStatus.NOT_SENT, null, null, null); }
	@ParameterizedTest @NullAndEmptySource @ValueSource(strings = { " " })
	void absentPhonesAreSkipped(String phone) { assertThat(policy.skipReason(guest(phone), false)).isEqualTo(WhatsAppLinkSkipReason.NO_PHONE); }
	@ParameterizedTest @ValueSource(strings = { "612123456", "+012345678", " +34612345678", "invalid", "+1234567890123456" })
	void invalidStoredPhonesAreSkippedWithoutNormalization(String phone) { assertThat(policy.skipReason(guest(phone), false)).isEqualTo(WhatsAppLinkSkipReason.INVALID_PHONE); }
	@Test void answeredGuestsAreSkipped() { assertThat(policy.skipReason(guest("+34612345678"), true)).isEqualTo(WhatsAppLinkSkipReason.ALREADY_ANSWERED); }
	@Test void anUnansweredGuestWithAValidPhoneIsEligible() { assertThat(policy.skipReason(guest("+34612345678"), false)).isNull(); }
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "CANCELLED", "FINISHED" })
	void onlyPublishedEventsAllowGeneration(EventStatus state) {
		assertThatThrownBy(() -> policy.ensurePublished(com.gtog.event.GuestTestFixtures.event("event", state, null)))
				.isInstanceOf(EventInvitationSendingNotAllowedException.class);
	}
	@ParameterizedTest @ValueSource(ints = { 1999, 2000, 2001 })
	void theConservativeFullUrlLimitIsInclusive(int length) { assertThat(policy.acceptsUrl("a".repeat(length))).isEqualTo(length <= 2000); }
	@ParameterizedTest @ValueSource(ints = { -1, 0 })
	void configurationRequiresAPositiveMaximum(int maximum) { assertThatIllegalArgumentException().isThrownBy(() -> new WhatsAppLinkPolicy(maximum)); }
}
