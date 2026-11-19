package com.gtog.event.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationMessageFormatterTest {
	@ParameterizedTest @ValueSource(strings = { "👨‍👩‍👧‍👦", "🇪🇸", "👍🏽" })
	void truncationKeepsTheEntireGraphemeAtTheCutBoundary(String emoji) {
		String input = "a".repeat(96) + emoji + "b".repeat(10);
		assertThat(InvitationMessageFormatter.abbreviate(input, 100)).isEqualTo("a".repeat(96) + emoji + "...");
	}
	@ParameterizedTest @ValueSource(ints = { 99, 100, 101 })
	void theLimitIncludesTheThreeEllipsisGraphemes(int length) {
		String input = "a".repeat(length);
		assertThat(InvitationMessageFormatter.abbreviate(input, 100)).isEqualTo(length <= 100 ? input : "a".repeat(97) + "...");
	}
}
