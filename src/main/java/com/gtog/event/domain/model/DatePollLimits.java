package com.gtog.event.domain.model;

public record DatePollLimits(int maxCandidates) {
	public DatePollLimits {
		if (maxCandidates < 2) { throw new IllegalArgumentException("The candidate date maximum must be at least two"); }
	}
	public static DatePollLimits defaults() { return new DatePollLimits(10); }
}
