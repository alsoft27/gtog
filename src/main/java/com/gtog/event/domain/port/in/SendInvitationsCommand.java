package com.gtog.event.domain.port.in;

import java.util.List;

public record SendInvitationsCommand(String hostId, String hostName, String eventId, List<String> guestIds) {}
