package com.gtog.event.domain.model;

public class InvalidHoursBeforeException extends EventDomainException {

	private InvalidHoursBeforeException(String message) {
		super(message);
	}

	public static InvalidHoursBeforeException notPositive(int hoursBefore) {
		return new InvalidHoursBeforeException("hoursBefore must be greater than zero, got %d".formatted(hoursBefore));
	}

	public static InvalidHoursBeforeException notApplicable(LinkVisibility linkVisibility) {
		return new InvalidHoursBeforeException(
				"hoursBefore is not used when linkVisibility is %s and must not be sent".formatted(linkVisibility));
	}
}
