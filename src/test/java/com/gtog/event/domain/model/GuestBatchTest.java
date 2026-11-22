package com.gtog.event.domain.model;

import java.util.List;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class GuestBatchTest {
	private final Event empty = event("event", EventStatus.PUBLISHED, 0L);
	private List<Guest> prepare(List<GuestInput> inputs) {
		var sequence = new AtomicInteger();
		return GuestBatch.prepare(empty, inputs, 100, phone -> {
			if (!phone.startsWith("+")) { throw new InvalidGuestPhoneException(); }
			return phone.replace(" ", "");
		}, () -> token(sequence.incrementAndGet()));
	}
	@Test void normalizesOnceAndPreservesOrderWithoutMutatingTheEvent() {
		var result = prepare(List.of(new GuestInput(" Ana ", " ANA@EXAMPLE.COM ", null), new GuestInput("Luis", null, "+34 612 345 678")));
		assertThat(result).extracting(Guest::getName).containsExactly("Ana", "Luis");
		assertThat(result.getFirst().getEmail()).isEqualTo("ana@example.com");
		assertThat(result.getLast().getPhoneE164()).isEqualTo("+34612345678");
		assertThat(result).extracting(Guest::getInvitationStatus).containsOnly(InvitationStatus.NOT_SENT);
		assertThat(result).extracting(Guest::getToken).doesNotHaveDuplicates(); assertThat(empty.getGuests()).isEmpty();
	}
	@Test void collectsEveryFieldErrorAndNullElementsWithoutGeneratingTokens() {
		var exception = catchThrowableOfType(() -> prepare(Arrays.asList(new GuestInput(" ", "invalid", "invalid"), null)), GuestBatchRejectedException.class);
		assertThat(exception.getErrors()).containsExactly(new GuestBatchError(0, GuestErrorCode.BLANK_NAME), new GuestBatchError(0, GuestErrorCode.INVALID_EMAIL),
				new GuestBatchError(0, GuestErrorCode.INVALID_PHONE), new GuestBatchError(1, GuestErrorCode.BLANK_NAME), new GuestBatchError(1, GuestErrorCode.NO_CONTACT));
		assertThat(exception.isConflict()).isFalse();
	}
	@Test void marksBothIndicesForEquivalentEmailsAndPhones() {
		var exception = catchThrowableOfType(() -> prepare(List.of(new GuestInput("A", " A@EXAMPLE.COM ", "+34 612 345 678"),
				new GuestInput("B", "a@example.com", "+34612345678"))), GuestBatchRejectedException.class);
		assertThat(exception.getErrors()).containsExactly(new GuestBatchError(0, GuestErrorCode.DUPLICATE_IN_BATCH), new GuestBatchError(1, GuestErrorCode.DUPLICATE_IN_BATCH));
	}
	@Test void validatesBeforeComparingAgainstTheEvent() {
		Event event = event("event", EventStatus.DRAFT, 0L, guest(1));
		var exception = catchThrowableOfType(() -> GuestBatch.prepare(event, List.of(new GuestInput("", "guest1@example.com", null)), 100, phone -> phone,
				() -> { throw new AssertionError("Invalid batches must not generate tokens"); }), GuestBatchRejectedException.class);
		assertThat(exception.isConflict()).isFalse(); assertThat(exception.getErrors()).containsExactly(new GuestBatchError(0, GuestErrorCode.BLANK_NAME));
	}
	@Test void reportsEveryDuplicateAgainstTheEvent() {
		var exception = catchThrowableOfType(() -> GuestBatch.prepare(event("event", EventStatus.PUBLISHED, 0L, guest(1), guest(2)),
				List.of(new GuestInput("A", "guest1@example.com", null), new GuestInput("B", "guest2@example.com", null)), 100, phone -> phone,
				new com.gtog.event.domain.port.out.GuestTokenGeneratorPort() { int value = 10; public String generate() { return token(value++); } }), GuestBatchRejectedException.class);
		assertThat(exception.isConflict()).isTrue(); assertThat(exception.getErrors()).containsExactly(new GuestBatchError(0, GuestErrorCode.DUPLICATE_IN_EVENT), new GuestBatchError(1, GuestErrorCode.DUPLICATE_IN_EVENT));
	}
	@Test void rejectsEmptyNullAndOversizedLists() {
		for (List<GuestInput> inputs : Arrays.asList(null, List.<GuestInput>of(), java.util.Collections.nCopies(101, new GuestInput("A", "a@example.com", null)))) {
			assertThatThrownBy(() -> prepare(inputs)).isInstanceOf(GuestBatchRejectedException.class);
		}
	}
	@Test void rejectsTwoIdenticalTokensWithinTheBatch() {
		assertThatThrownBy(() -> GuestBatch.prepare(empty, List.of(new GuestInput("A", "a@example.com", null), new GuestInput("B", "b@example.com", null)),
				100, phone -> phone, () -> token(1))).isInstanceOf(DuplicateGuestTokenException.class);
	}
	@Test void rejectsTheSameTokenAsAnExistingGuest() {
		assertThatThrownBy(() -> GuestBatch.prepare(event("event", EventStatus.PUBLISHED, 0L, guest(1)), List.of(new GuestInput("A", "a@example.com", null)),
				100, phone -> phone, () -> token(1))).isInstanceOf(DuplicateGuestTokenException.class);
	}
}
