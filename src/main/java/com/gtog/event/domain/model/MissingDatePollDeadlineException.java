package com.gtog.event.domain.model;

public class MissingDatePollDeadlineException extends EventDomainException {
	public MissingDatePollDeadlineException() { super("A response deadline is required for a date poll"); }
}
