package com.gtog.user.domain.port.out;

public interface SessionInvalidationPort {
	void invalidateAll(String userId);
}
