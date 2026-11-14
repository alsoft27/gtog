package com.gtog.event.domain.port.in;

import java.time.LocalDateTime;

public record ChangeResponseDeadlineCommand(String hostId, String eventId, LocalDateTime responseDeadline) {}
