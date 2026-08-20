package com.gtog.event.domain.model;

public class MissingHoursBeforeException extends EventDomainException {

	public MissingHoursBeforeException() {
		super("hoursBefore is required when linkVisibility is HOURS_BEFORE");
	}
}
