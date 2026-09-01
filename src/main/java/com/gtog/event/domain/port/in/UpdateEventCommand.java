package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.EventEdit;

public record UpdateEventCommand(String eventId, EventEdit edit) {
}
