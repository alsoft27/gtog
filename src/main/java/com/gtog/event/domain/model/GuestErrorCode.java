package com.gtog.event.domain.model;

public enum GuestErrorCode {
	BLANK_NAME, NO_CONTACT, INVALID_EMAIL, INVALID_PHONE, DUPLICATE_IN_BATCH, DUPLICATE_IN_EVENT;

	EventDomainException individualException() {
		return switch (this) {
			case BLANK_NAME -> new BlankGuestNameException();
			case NO_CONTACT -> new MissingGuestContactException();
			case INVALID_EMAIL -> new InvalidGuestEmailException();
			case INVALID_PHONE -> new InvalidGuestPhoneException();
			default -> new EventConcurrentModificationException();
		};
	}
}
