package com.gtog.event.domain.model;

public record GuestInput(String name, String email, String phone) {
	@Override public String toString() { return "GuestInput[redacted]"; }
}
