package com.gtog.event.domain.port.in;

public record AddGuestCommand(String hostId, String eventId, String name, String email, String phone) {}
