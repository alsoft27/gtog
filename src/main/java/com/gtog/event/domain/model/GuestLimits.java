package com.gtog.event.domain.model;

public record GuestLimits(int maxBatch, int maxPerEvent) {
	public GuestLimits { if (maxBatch < 1 || maxPerEvent < 1) { throw new IllegalArgumentException("Guest limits must be positive"); } }
}
