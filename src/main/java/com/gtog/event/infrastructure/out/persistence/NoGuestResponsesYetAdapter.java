package com.gtog.event.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.port.out.GuestResponseCleanupPort;

// Temporal y exclusivo de R6: no consulta ni crea responses. R8 debe sustituir este bean por el adaptador real.
@Component
public class NoGuestResponsesYetAdapter implements GuestResponseCleanupPort {

	@Override
	public boolean hasResponse(String eventId, String guestToken) {
		return false;
	}

	@Override
	public void deleteResponse(String eventId, String guestToken) {
		throw new IllegalStateException("Response deletion is not available before R8");
	}
}
