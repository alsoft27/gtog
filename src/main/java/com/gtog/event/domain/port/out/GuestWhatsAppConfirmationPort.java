package com.gtog.event.domain.port.out;

import java.time.Instant;

public interface GuestWhatsAppConfirmationPort {
	// Dos actualizaciones condicionadas: NOT_SENT o OPENED sin fecha, siempre con telefono y evento publicado.
	boolean confirm(String eventId, String hostId, String guestId, Instant now);
}
