package com.gtog.event.domain.model;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.DateTimeException;

// Cada tipo temporal aporta sus propios datos; una consulta no necesita fechas ficticias.
public sealed interface EventSchedule permits FixedDateSchedule, DatePollSchedule {
	EventType type();
	String timeZone();
	void validateResponseDeadline(LocalDateTime deadline);
	boolean hasEnded(Instant now);

	static ZoneId zone(String timeZone) {
		if (timeZone == null) { throw new InvalidTimeZoneException(null); }
		try { return ZoneId.of(timeZone); }
		catch (DateTimeException exception) { throw new InvalidTimeZoneException(timeZone); }
	}
}
