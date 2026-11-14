package com.gtog.event.domain.port.in;

public record UpdateGuestCommand(String hostId, String eventId, String guestId, String name, String email, String phone) {}
