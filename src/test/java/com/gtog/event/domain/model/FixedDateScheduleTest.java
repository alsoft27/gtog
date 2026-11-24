package com.gtog.event.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedDateScheduleTest {
	private static final LocalDateTime START = LocalDateTime.of(2027, 3, 28, 1, 30);
	private static final LocalDateTime END = LocalDateTime.of(2027, 3, 28, 4, 30);

	@Test void preservesLocalTimesAcrossADaylightSavingTransitionWithoutFreezingUtcInstants() {
		var schedule = FixedDateSchedule.create(START, END, "Europe/Madrid");
		assertThat(schedule.type()).isEqualTo(EventType.FIXED_DATE);
		assertThat(schedule.startsAt()).isEqualTo(START);
		assertThat(schedule.endsAt()).isEqualTo(END);
		assertThat(schedule.timeZone()).isEqualTo("Europe/Madrid");
		assertThat(schedule.startsAtInstant()).isEqualTo(Instant.parse("2027-03-28T00:30:00Z"));
		assertThat(schedule.endsAtInstant()).isEqualTo(Instant.parse("2027-03-28T02:30:00Z"));
	}

	@Test void rejectsAnInvalidTimeZoneWithTheExistingDomainException() {
		assertThatThrownBy(() -> FixedDateSchedule.create(START, END, "Unknown/Zone"))
				.isInstanceOf(InvalidTimeZoneException.class);
	}

	@Test void rejectsAnEndBeforeTheStartWithTheExistingDomainException() {
		assertThatThrownBy(() -> FixedDateSchedule.create(START, START.minusMinutes(1), "UTC"))
				.isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test void rejectsAnEndEqualToTheStart() {
		assertThatThrownBy(() -> FixedDateSchedule.create(START, START, "UTC"))
				.isInstanceOf(InvalidEventPeriodException.class);
	}

	@Test void acceptsNoDeadlineAndADeadlineExactlyAtTheStart() {
		var schedule = FixedDateSchedule.create(START, END, "UTC");
		assertThatCode(() -> schedule.validateResponseDeadline(null)).doesNotThrowAnyException();
		assertThatCode(() -> schedule.validateResponseDeadline(START)).doesNotThrowAnyException();
	}

	@Test void rejectsADeadlineAfterTheStartWithTheExistingDomainException() {
		var schedule = FixedDateSchedule.create(START, END, "UTC");
		assertThatThrownBy(() -> schedule.validateResponseDeadline(START.plusNanos(1)))
				.isInstanceOf(InvalidResponseDeadlineException.class);
	}

	@Test void creationAndEditingKeepTheEventAndItsTypedScheduleConsistent() {
		var event = Event.builder().hostId("host").title("Cena").startsAt(START).endsAt(END)
				.timeZone("Europe/Madrid").modality(Modality.IN_PERSON)
				.venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null)).build();
		assertThat(event.getEventType()).isEqualTo(EventType.FIXED_DATE);
		assertThat(event.getSchedule()).isInstanceOf(FixedDateSchedule.class);
		var replacement = START.plusDays(10);
		event.edit(new EventEdit("Cena editada", null, replacement, replacement.plusHours(2), "America/Bogota",
				Modality.IN_PERSON, null, null, false, true, replacement.minusDays(1)));
		var schedule = (FixedDateSchedule) event.getSchedule();
		assertThat(event.getEventType()).isEqualTo(EventType.FIXED_DATE);
		assertThat(event.getStartsAt()).isEqualTo(schedule.startsAt()).isEqualTo(replacement);
		assertThat(event.getEndsAt()).isEqualTo(schedule.endsAt()).isEqualTo(replacement.plusHours(2));
		assertThat(event.getTimeZone()).isEqualTo(schedule.timeZone()).isEqualTo("America/Bogota");
		assertThat(event.startsAtInstant()).isEqualTo(schedule.startsAtInstant());
		assertThat(event.endsAtInstant()).isEqualTo(schedule.endsAtInstant());
	}
}
