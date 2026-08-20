package com.gtog.event.domain.model;

import java.time.LocalDateTime;

public class InvalidEventPeriodException extends EventDomainException {

	public InvalidEventPeriodException(LocalDateTime startsAt, LocalDateTime endsAt) {
		super("endsAt (%s) must be after startsAt (%s)".formatted(endsAt, startsAt));
	}
}
