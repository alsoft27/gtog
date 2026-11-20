package com.gtog.user.domain.port.out;

import java.time.Instant;
import com.gtog.user.domain.model.AuthMailPolicy;

public interface AuthMailQuotaPort {
	boolean reserveVerification(String userId, Instant now, AuthMailPolicy policy);
}
