package com.gtog.event.infrastructure.out.persistence;

import java.util.List;

// ISO como texto: no convertimos una fecha sin hora en un instante de medianoche ni LocalTime en otra fecha.
public record DatePollDocument(List<Candidate> candidateDates, Long pollRevision, String selectedCandidateDateId, Long noticeClaimedRevision) {
	public DatePollDocument(List<Candidate> candidateDates) { this(candidateDates, null, null, null); }
	public record Candidate(String id, String date, String startTime) {}
}
