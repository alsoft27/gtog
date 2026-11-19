package com.gtog.event.application;

import java.text.BreakIterator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import com.gtog.event.domain.model.Venue;

public final class InvitationMessageFormatter {
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("es"));
	private InvitationMessageFormatter() {}

	public static String singleLine(String value) {
		return value.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ").strip();
	}

	public static String venue(Venue venue) {
		return singleLine(venue.placeName()) + " — " + singleLine(venue.address());
	}

	// Solo información pública: nunca recibe OnlineAccess ni secretos de la sala.
	public static String scheduleAndLocation(LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
			String venue, String platform) {
		return "Inicio: " + startsAt.format(DATE_TIME) + "\nFin: " + endsAt.format(DATE_TIME)
				+ "\nZona horaria: " + timeZone + "\n"
				+ (venue != null ? "Lugar: " + venue : "Plataforma: " + singleLine(platform)) + "\n";
	}

	public static String abbreviate(String value, int maxGraphemes) {
		if (maxGraphemes < 3) { throw new IllegalArgumentException("Grapheme limit must accommodate the ellipsis"); }
		String text = singleLine(value);
		BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
		iterator.setText(text);
		int count = 0;
		int prefixEnd = 0;
		for (int boundary = iterator.next(); boundary != BreakIterator.DONE; boundary = iterator.next()) {
			count++;
			if (count == maxGraphemes - 3) { prefixEnd = boundary; }
			if (count > maxGraphemes) { return text.substring(0, prefixEnd) + "..."; }
		}
		return text;
	}
}
