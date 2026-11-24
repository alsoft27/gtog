package com.gtog.event.domain.model;

public record DatePollLimits(int maxCandidates) {
	public DatePollLimits {
		if (maxCandidates < 2) { throw new IllegalArgumentException("The candidate date maximum must be at least two"); }
	}
	public void validateCount(int count) {
		if (count < 2 || count > maxCandidates) { throw new InvalidCandidateDateCountException(); }
	}
	public static DatePollLimits defaults() { return new DatePollLimits(10); }
}
