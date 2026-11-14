package com.gtog.event.domain.port.in;

import java.util.List;

import com.gtog.event.domain.model.Guest;

public interface ListGuestUseCase {

	List<Guest> list(String hostId, String eventId);
}
