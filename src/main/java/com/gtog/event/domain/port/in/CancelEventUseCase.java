package com.gtog.event.domain.port.in;

import java.time.Instant;

import com.gtog.event.domain.model.Event;

public interface CancelEventUseCase {

	Event cancel(String eventId, String reason, Instant now);
}
