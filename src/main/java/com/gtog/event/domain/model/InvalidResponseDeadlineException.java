package com.gtog.event.domain.model;

import java.time.LocalDateTime;

public class InvalidResponseDeadlineException extends EventDomainException {

	public InvalidResponseDeadlineException(LocalDateTime responseDeadline, LocalDateTime startsAt) {
		super("responseDeadline (%s) must not be after startsAt (%s)".formatted(responseDeadline, startsAt));
	}
}
