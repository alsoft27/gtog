package com.gtog.event.domain.port.out;

// La respuesta sigue fuera de Guest y de Event; la aplicacion orquesta su limpieza tras retirar al invitado.
public interface GuestResponseCleanupPort {

	boolean hasResponse(String eventId, String guestToken);

	boolean hasAvailability(String eventId, String guestToken, long revision);

	void deleteResponse(String eventId, String guestToken);
}
