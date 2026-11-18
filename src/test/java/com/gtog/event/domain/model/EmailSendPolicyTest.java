package com.gtog.event.domain.model;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;

class EmailSendPolicyTest {
	private final EmailSendPolicy policy = new EmailSendPolicy(3, Duration.ofHours(1), 100, 20);
	private final Event event = com.gtog.event.InvitationTestFixtures.event(EventStatus.PUBLISHED, null);
	private Guest withHistory(int count, Instant last) {
		var g = guest(101);
		return Guest.reconstitute(g.getId(), g.getName(), g.getEmail(), null, g.getToken(), InvitationStatus.OPENED,
				DeliveryChannel.LINK, NOW.minusSeconds(7200), NOW.minusSeconds(3600), count, last);
	}
	@Test void newGuestsHaveNoEmailHistory() {
		assertThat(guest(101).getEmailSendCount()).isZero();
		assertThat(guest(101).getLastEmailSentAt()).isNull();
	}
	@Test void guestsWithoutEmailAreSkipped() {
		var g = Guest.create("Guest", null, "+34612345678", "token", phone -> phone);
		assertThat(policy.skipReason(event, g, false, NOW)).isEqualTo(EmailSendSkipReason.NO_EMAIL);
	}
	@Test void answeredGuestsAreSkipped() {
		assertThat(policy.skipReason(event, guest(101), true, NOW)).isEqualTo(EmailSendSkipReason.ALREADY_ANSWERED);
	}
	@Test void openedGuestsWithoutResponseRemainEligible() {
		assertThat(policy.skipReason(event, withHistory(0, null), false, NOW)).isNull();
	}
	@Test void theLifetimeLimitTakesPrecedenceOverTheInterval() {
		assertThat(policy.guestLimitReason(withHistory(3, NOW), NOW)).isEqualTo(EmailSendSkipReason.LIMIT_REACHED);
	}
	@ParameterizedTest @ValueSource(longs = { -1, 0, 1 })
	void theGuestIntervalIncludesItsExactBoundary(long offset) {
		var result = policy.guestLimitReason(withHistory(1, NOW.minusSeconds(3600)), NOW.plusSeconds(offset));
		assertThat(result).isEqualTo(offset < 0 ? EmailSendSkipReason.THROTTLED : null);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
	void onlyPublishedEventsAcceptInvitationSending(EventStatus status) {
		var other = com.gtog.event.InvitationTestFixtures.event(status, null);
		assertThatThrownBy(() -> policy.skipReason(other, guest(101), false, NOW)).isInstanceOf(EventInvitationSendingNotAllowedException.class);
	}
	@Test void changingTheNormalizedEmailResetsOnlyEmailHistory() {
		var before = withHistory(3, NOW);
		var after = before.editContact(before.getName(), "new@example.com", null, phone -> phone);
		assertThat(after.getEmailSendCount()).isZero(); assertThat(after.getLastEmailSentAt()).isNull();
		assertThat(after.getToken()).isEqualTo(before.getToken()); assertThat(after.getSentAt()).isEqualTo(before.getSentAt());
		assertThat(after.getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
	}
	@Test void anEquivalentEmailPreservesEmailHistory() {
		var before = withHistory(2, NOW);
		var after = before.editContact("Updated name", " GUEST101@EXAMPLE.COM ", null, phone -> phone);
		assertThat(after.getEmailSendCount()).isEqualTo(2); assertThat(after.getLastEmailSentAt()).isEqualTo(NOW);
	}
	@ParameterizedTest @ValueSource(longs = { -1, 0, 1 })
	void theHostWindowExpiresAtExactly24Hours(long offset) {
		var quota = HostSendQuota.from(100, NOW.minusSeconds(86400), 100, NOW.plusSeconds(offset));
		assertThat(quota.remaining()).isEqualTo(offset < 0 ? 0 : 100);
		assertThat(quota.resetsAt()).isEqualTo(offset < 0 ? NOW : null);
	}
	@Test void anAbsentHostWindowHasNoResetDate() {
		assertThat(HostSendQuota.from(0, null, 100, NOW)).isEqualTo(new HostSendQuota(100, 100, null));
	}
}
