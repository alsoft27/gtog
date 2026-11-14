package com.gtog.event.domain.port.in;

public record RecordGuestResponseCommand(String token, String responseOptionId, String comment, String email) {
	@Override
	public String toString() { return "RecordGuestResponseCommand[redacted]"; }
}
