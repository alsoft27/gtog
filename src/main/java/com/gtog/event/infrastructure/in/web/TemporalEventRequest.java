package com.gtog.event.infrastructure.in.web;

import java.time.LocalDateTime;
import com.gtog.event.domain.model.EventType;

public interface TemporalEventRequest {
	EventType eventType();
	LocalDateTime startsAt();
	LocalDateTime endsAt();
}
