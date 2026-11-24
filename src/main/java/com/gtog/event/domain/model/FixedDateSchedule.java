package com.gtog.event.domain.model;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// Event crea y edita mediante create(); la construcción directa se reserva a la rehidratación existente.
public record FixedDateSchedule(LocalDateTime startsAt, LocalDateTime endsAt, String timeZone) implements EventSchedule {

	public static FixedDateSchedule create(LocalDateTime startsAt, LocalDateTime endsAt, String timeZone) {
		ZoneId zone;
		try {
			zone = ZoneId.of(timeZone);
		} catch (DateTimeException exception) {
			throw new InvalidTimeZoneException(timeZone);
		}
		if (!endsAt.atZone(zone).toInstant().isAfter(startsAt.atZone(zone).toInstant())) {
			throw new InvalidEventPeriodException(startsAt, endsAt);
		}
		return new FixedDateSchedule(startsAt, endsAt, timeZone);
	}

	// Conserva el contrato del builder de rehidratación: no aplica validaciones de creación nuevas.
	static FixedDateSchedule reconstitute(LocalDateTime startsAt, LocalDateTime endsAt, String timeZone) {
		return new FixedDateSchedule(startsAt, endsAt, timeZone);
	}

	public void validateResponseDeadline(LocalDateTime deadline) {
		if (deadline != null && deadline.isAfter(startsAt)) {
			throw new InvalidResponseDeadlineException(deadline, startsAt);
		}
	}

	@Override public EventType type() { return EventType.FIXED_DATE; }
	public Instant startsAtInstant() { return startsAt.atZone(ZoneId.of(timeZone)).toInstant(); }
	public Instant endsAtInstant() { return endsAt.atZone(ZoneId.of(timeZone)).toInstant(); }
}
