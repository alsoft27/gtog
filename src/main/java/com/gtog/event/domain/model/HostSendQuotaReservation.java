package com.gtog.event.domain.model;

import java.time.Instant;

public record HostSendQuotaReservation(String hostId, Instant windowStartedAt) {}
