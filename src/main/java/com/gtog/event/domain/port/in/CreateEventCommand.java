package com.gtog.event.domain.port.in;

import java.time.LocalDateTime;
import java.util.List;

import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.ResponseOptionDraft;

public record CreateEventCommand(
		String hostId,
		String title,
		String description,
		LocalDateTime startsAt,
		LocalDateTime endsAt,
		String timeZone,
		Modality modality,
		List<ResponseOptionDraft> responseOptions,
		Boolean allowComment,
		Boolean allowResponseChange,
		LocalDateTime responseDeadline) {
}
