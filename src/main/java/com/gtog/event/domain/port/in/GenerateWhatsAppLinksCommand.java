package com.gtog.event.domain.port.in;

import java.util.List;

public record GenerateWhatsAppLinksCommand(String hostId, String hostName, String eventId, List<String> guestIds) {}
