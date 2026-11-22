package com.gtog.event.domain.port.in;

import java.util.List;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestInput;

public interface AddGuestsUseCase {
	List<Guest> add(String hostId, String eventId, List<GuestInput> guests);
}
