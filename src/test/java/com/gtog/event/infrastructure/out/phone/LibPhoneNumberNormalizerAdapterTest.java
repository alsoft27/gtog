package com.gtog.event.infrastructure.out.phone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gtog.event.domain.model.InvalidGuestPhoneException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibPhoneNumberNormalizerAdapterTest {

	private final LibPhoneNumberNormalizerAdapter adapter = new LibPhoneNumberNormalizerAdapter();

	@Test
	void normalizesInternationalFormattingToE164() {
		assertThat(adapter.normalize("+34 612 345 678")).isEqualTo("+34612345678");
		assertThat(adapter.normalize("+1 (650) 253-0000")).isEqualTo("+16502530000");
	}

	@ParameterizedTest
	@ValueSource(strings = {"612345678", "0034612345678", "+999123456789", "+34123", "+34 612 345 678 ext. 12", "+abc"})
	void rejectsNumbersWithoutAnUnambiguousValidE164Representation(String phone) {
		assertThatThrownBy(() -> adapter.normalize(phone)).isInstanceOf(InvalidGuestPhoneException.class);
	}
}
