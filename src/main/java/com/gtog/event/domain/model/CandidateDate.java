package com.gtog.event.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CandidateDate(String id, LocalDate date, LocalTime startTime) {
	public CandidateDate {
		if (id == null || id.isBlank()) { throw new InvalidCandidateDateIdException(); }
		if (date == null) { throw new MissingCandidateDateException(); }
	}
	public static CandidateDate create(CandidateDateDraft draft) {
		if (draft == null) { throw new MissingCandidateDateException(); }
		return new CandidateDate(UUID.randomUUID().toString(), draft.date(), draft.startTime());
	}
}
