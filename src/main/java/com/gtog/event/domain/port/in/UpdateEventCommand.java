package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.EventEdit;

public record UpdateEventCommand(String hostId, String eventId, EventEdit edit) {
}
