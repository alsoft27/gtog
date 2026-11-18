package com.gtog.event.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import com.gtog.event.domain.model.HostSendQuota;
import com.gtog.event.domain.model.HostSendQuotaReservation;

public interface HostSendQuotaPort {
	Optional<HostSendQuotaReservation> reserve(String hostId, Instant now, int limit);
	boolean release(HostSendQuotaReservation reservation);
	HostSendQuota read(String hostId, Instant now, int limit);
}
