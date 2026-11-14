package com.gtog.event.domain.port.in;

public record DeleteGuestCommand(String hostId, String eventId, String guestId, boolean confirmDeleteResponse) {}
