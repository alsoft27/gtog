package com.gtog.event.domain.port.in;

import java.time.LocalDateTime;
import java.util.List;

import com.gtog.event.domain.model.ResponseOptionEdit;

public record ReplaceResponseOptionsCommand(
		String eventId,
		List<ResponseOptionEdit> responseOptions,
		boolean allowComment,
		boolean allowResponseChange,
		LocalDateTime responseDeadline) {
}
