package com.gtog.event.domain.model;

public record PublicJoinLimits(int maxPublicGuests, int maxPerEvent) {
	public PublicJoinLimits {
		if (maxPublicGuests < 1 || maxPerEvent < 1) { throw new IllegalArgumentException("Guest limits must be positive"); }
	}
}
