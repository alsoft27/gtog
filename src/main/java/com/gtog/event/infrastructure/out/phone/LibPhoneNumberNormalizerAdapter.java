package com.gtog.event.infrastructure.out.phone;

import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.gtog.event.domain.model.InvalidGuestPhoneException;
import com.gtog.event.domain.port.out.PhoneNormalizerPort;

@Component
public class LibPhoneNumberNormalizerAdapter implements PhoneNormalizerPort {

	private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();

	@Override
	public String normalize(String phone) {
		if (phone == null || !phone.strip().startsWith("+")) {
			throw new InvalidGuestPhoneException();
		}
		try {
			var parsed = phoneNumbers.parse(phone, "ZZ");
			if (parsed.hasExtension() || !phoneNumbers.isValidNumber(parsed)) {
				throw new InvalidGuestPhoneException();
			}
			return phoneNumbers.format(parsed, PhoneNumberFormat.E164);
		} catch (NumberParseException e) {
			throw new InvalidGuestPhoneException();
		}
	}
}
