package com.gtog.event.domain.model;

import java.time.Instant;

public record HostSendQuota(int limit, int remaining, Instant resetsAt) {
	public static HostSendQuota from(int count, Instant startedAt, int limit, Instant now) {
		if (startedAt == null || !now.isBefore(startedAt.plus(EmailSendPolicy.HOST_WINDOW))) {
			return new HostSendQuota(limit, limit, null);
		}
		return new HostSendQuota(limit, Math.max(0, limit - count), startedAt.plus(EmailSendPolicy.HOST_WINDOW));
	}
}
