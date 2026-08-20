package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseOptionTest {

	@Test
	void createGeneratesANonNullId() {
		ResponseOption first = ResponseOption.create("Asisto", true);
		ResponseOption second = ResponseOption.create("Asisto", true);

		assertThat(first.id()).isNotNull();
		assertThat(first.id()).isNotEqualTo(second.id());
	}

	@Test
	void createPreservesLabelAndCountsAsAttendance() {
		ResponseOption option = ResponseOption.create("No asisto", false);

		assertThat(option.label()).isEqualTo("No asisto");
		assertThat(option.countsAsAttendance()).isFalse();
	}

	@Test
	void rejectsNullLabel() {
		assertThatThrownBy(() -> new ResponseOption("id-1", null, true))
				.isInstanceOf(BlankResponseOptionLabelException.class);
	}

	@Test
	void rejectsBlankLabel() {
		assertThatThrownBy(() -> new ResponseOption("id-1", "   ", true))
				.isInstanceOf(BlankResponseOptionLabelException.class);
	}
}
