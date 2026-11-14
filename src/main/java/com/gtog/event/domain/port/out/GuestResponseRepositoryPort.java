package com.gtog.event.domain.port.out;

import java.util.Optional;
import com.gtog.event.domain.model.GuestResponse;

public interface GuestResponseRepositoryPort {
	GuestResponse insert(GuestResponse response);
	GuestResponse upsert(GuestResponse response);
	Optional<GuestResponse> find(String eventId, String guestToken);
}
