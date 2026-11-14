package com.gtog.event.domain.port.out;

// R8 sustituira el adaptador temporal: la respuesta sigue fuera de Guest y de Event.
public interface GuestResponseCleanupPort {

	boolean hasResponse(String eventId, String guestToken);

	void deleteResponse(String eventId, String guestToken);
}
