package com.gtog.event.infrastructure.out.persistence;

import java.util.List;

// ISO como texto: no convertimos una fecha sin hora en un instante de medianoche ni LocalTime en otra fecha.
public record DatePollDocument(List<Candidate> candidateDates) {
	public record Candidate(String id, String date, String startTime) {}
}
