package com.gtog.event.application;

import java.util.List;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.AddGuestsUseCase;
import com.gtog.event.domain.port.out.*;

@Service
public class AddGuestsService implements AddGuestsUseCase {
	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final PhoneNormalizerPort phones;
	private final GuestTokenGeneratorPort tokens;
	private final GuestLimits limits;
	public AddGuestsService(EventRepositoryPort events, EventGuestRepositoryPort guests, PhoneNormalizerPort phones,
			GuestTokenGeneratorPort tokens, GuestLimits limits) {
		this.events = events; this.guests = guests; this.phones = phones; this.tokens = tokens; this.limits = limits;
	}
	@Override public List<Guest> add(String hostId, String eventId, List<GuestInput> inputs) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		if (!event.getHostId().equals(hostId)) { throw new EventNotFoundException(eventId); }
		event.ensureGuestsEditable();
		var created = GuestBatch.prepare(event, inputs, limits.maxBatch(), phones, tokens);
		guests.addGuests(eventId, hostId, created);
		return created;
	}
}
