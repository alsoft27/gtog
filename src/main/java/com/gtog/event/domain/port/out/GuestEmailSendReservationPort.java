package com.gtog.event.domain.port.out;

import java.time.Instant;
import com.gtog.event.domain.model.*;

public interface GuestEmailSendReservationPort {
	GuestEmailReservationResult reserve(String eventId, String hostId, String guestId, String email, Instant now, EmailSendPolicy policy);
	boolean release(GuestEmailReservation reservation);
	boolean recordSent(GuestEmailReservation reservation, Instant now);
}
