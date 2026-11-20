package com.gtog.user.domain.model;

import java.time.Duration;

public record AuthMailPolicy(Duration cooldown, int maxPerDay) {
	public static final Duration WINDOW = Duration.ofHours(24);
	public AuthMailPolicy {
		if (cooldown.isNegative() || cooldown.isZero() || maxPerDay < 1) { throw new IllegalArgumentException("Invalid authentication mail limits"); }
	}
}
