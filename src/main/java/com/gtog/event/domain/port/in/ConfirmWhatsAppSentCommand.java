package com.gtog.event.domain.port.in;

import java.util.List;

public record ConfirmWhatsAppSentCommand(String hostId, String eventId, List<String> guestIds) {}
