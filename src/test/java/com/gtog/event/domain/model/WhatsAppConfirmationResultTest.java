package com.gtog.event.domain.model;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;

class WhatsAppConfirmationResultTest {
	private Guest guest(String phone, InvitationStatus status, Instant sentAt) {
		return Guest.reconstitute("guest", "Ana", "ana@example.com", phone, token(101), status, DeliveryChannel.EMAIL, sentAt, null);
	}
	@ParameterizedTest @NullAndEmptySource @ValueSource(strings = { "  " })
	void absentOrBlankPhoneIsReportedWithoutMarking(String phone) {
		assertThat(WhatsAppConfirmationResult.unchanged(guest(phone, InvitationStatus.NOT_SENT, null)).status()).isEqualTo(WhatsAppConfirmationStatus.NO_PHONE);
	}
	@Test void sentKeepsTheFirstSend() {
		assertThat(WhatsAppConfirmationResult.unchanged(guest("+34612345678", InvitationStatus.SENT, Instant.EPOCH)).status()).isEqualTo(WhatsAppConfirmationStatus.ALREADY_SENT);
	}
	@Test void openedWithSendHistoryIsAlreadyOpened() {
		assertThat(WhatsAppConfirmationResult.unchanged(guest("+34612345678", InvitationStatus.OPENED, Instant.EPOCH)).status()).isEqualTo(WhatsAppConfirmationStatus.ALREADY_OPENED);
	}
	@Test void anUnmatchedNotSentGuestRequiresReloadingRatherThanInventingSuccess() {
		assertThatThrownBy(() -> WhatsAppConfirmationResult.unchanged(guest("+34612345678", InvitationStatus.NOT_SENT, null))).isInstanceOf(EventConcurrentModificationException.class);
	}
	@Test void openedWithoutSendHistoryStillRequiresAnAtomicWrite() {
		assertThatThrownBy(() -> WhatsAppConfirmationResult.unchanged(guest("+34612345678", InvitationStatus.OPENED, null))).isInstanceOf(EventConcurrentModificationException.class);
	}
}
