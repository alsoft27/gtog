package com.gtog.event.infrastructure.out.calendar;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.util.Configurator;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.DtEnd;
import org.junit.jupiter.api.Test;
import com.gtog.event.domain.model.InvitationCalendarData;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.InvitationTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

class ICal4jInvitationCalendarAdapterTest {
	private final ICal4jInvitationCalendarAdapter adapter = new ICal4jInvitationCalendarAdapter();

	@Test
	void disablesRemoteTimeZoneUpdatesFromTheLibrarysInitializationResource() {
		assertThat(Configurator.getProperty("net.fortuna.ical4j.timezone.update.enabled")).hasValue("false");
	}

	@Test
	void producesAValidCalendarWithLocalTimesTimeZoneUidAndInjectedTimestamp() throws Exception {
		String body = render("Event", "Description", "Europe/Madrid");
		Calendar calendar = parse(body);
		assertThat(calendar.validate().hasErrors()).isFalse();
		VEvent event = meeting(calendar);
		assertThat(event.getRequiredProperty("UID").getValue()).isEqualTo("event-id@gtog");
		assertThat(event.getRequiredProperty("DTSTAMP").getValue()).isEqualTo("20270101T150000Z");
		assertThat(body).contains("BEGIN:VTIMEZONE\r\n", "DTSTART;TZID=Europe/Madrid:20270101T180000\r\n",
				"DTEND;TZID=Europe/Madrid:20270101T200000\r\n");
	}

	@Test
	void roundTripsTextEscapesAndCannotInjectAnotherCalendarProperty() throws Exception {
		String title = "Fiesta, café; grupo \\ A\r\nATTENDEE:mailto:injected@example.com";
		String description = "Primera línea; coma, barra \\ y\nsegunda línea";
		String body = render(title, description, "Europe/Madrid");
		VEvent event = meeting(parse(body));
		assertThat(event.getRequiredProperty("SUMMARY").getValue()).isEqualTo(title.replace("\r\n", "\n"));
		assertThat(event.getRequiredProperty("DESCRIPTION").getValue()).isEqualTo(description);
		assertThat(event.getRequiredProperty("LOCATION").getValue()).isEqualTo("Sala, Direccion");
		assertThat(event.getProperty("ATTENDEE")).isEmpty();
	}

	@Test
	void foldsAt75Utf8OctetsWithoutSplittingMultibyteCharacters() throws Exception {
		String title = "Á🎉漢字".repeat(70);
		String description = "Más texto ñ😉; coma, barra \\ ".repeat(40) + "fin";
		String body = render(title, description, "Europe/Madrid");
		assertThat(body).contains("\r\n ").doesNotContain("\uFFFD");
		for (String line : body.split("\r\n")) {
			assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
		}
		assertThat(body.replace("\r\n", "")).doesNotContain("\n", "\r");
		VEvent event = meeting(parse(body));
		assertThat(event.getRequiredProperty("SUMMARY").getValue()).isEqualTo(title);
		assertThat(event.getRequiredProperty("DESCRIPTION").getValue()).isEqualTo(description);
	}

	@Test
	void supportsUtcWithoutAnOnlineLinkOrDescription() throws Exception {
		String body = render("UTC event", null, "UTC");
		assertThat(parse(body).validate().hasErrors()).isFalse();
		assertThat(meeting(parse(body)).getProperty("URL")).isEmpty();
		assertThat(meeting(parse(body)).getProperty("DESCRIPTION")).isEmpty();
	}

	@Test
	void preservesLocalTimesAndTheirInstantsAcrossADaylightSavingTransition() throws Exception {
		LocalDateTime start = LocalDateTime.of(2027, 3, 28, 1, 30);
		LocalDateTime end = LocalDateTime.of(2027, 3, 28, 3, 30);
		var data = new InvitationCalendarData("event-id", "Cambio de hora", null, start, end, "Europe/Madrid", null, null, NOW);
		String body = new String(adapter.render(data), StandardCharsets.UTF_8);
		VEvent event = meeting(parse(body));
		DtStart<?> parsedStart = event.getRequiredProperty("DTSTART");
		DtEnd<?> parsedEnd = event.getRequiredProperty("DTEND");
		assertThat(Instant.from(parsedStart.getDate())).isEqualTo(start.atZone(ZoneId.of("Europe/Madrid")).toInstant());
		assertThat(Instant.from(parsedEnd.getDate())).isEqualTo(end.atZone(ZoneId.of("Europe/Madrid")).toInstant());
		assertThat(body).contains("DTSTART;TZID=Europe/Madrid:20270328T013000", "DTEND;TZID=Europe/Madrid:20270328T033000");
	}

	private String render(String title, String description, String timeZone) {
		var original = event(com.gtog.event.domain.model.EventStatus.PUBLISHED, null);
		return new String(adapter.render(new InvitationCalendarData("event-id", title, description,
				LocalDateTime.of(2027, 1, 1, 18, 0), LocalDateTime.of(2027, 1, 1, 20, 0), timeZone,
				original.getVenue(), null, NOW)), StandardCharsets.UTF_8);
	}
	private Calendar parse(String body) throws Exception { return new CalendarBuilder().build(new StringReader(body)); }
	private VEvent meeting(Calendar calendar) {
		return calendar.getComponents().stream().filter(VEvent.class::isInstance).map(VEvent.class::cast).findFirst().orElseThrow();
	}
}
