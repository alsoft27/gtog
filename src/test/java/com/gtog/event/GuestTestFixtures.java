package com.gtog.event;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.ResponseOption;
import com.gtog.event.domain.model.Venue;

public final class GuestTestFixtures {

	private GuestTestFixtures() {}

	public static String token(int value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(32).putInt(value).array());
	}

	public static Guest guest(int value) {
		return Guest.create("Guest " + value, "guest" + value + "@example.com", null, token(value), phone -> phone);
	}

	public static Event event(String id, EventStatus status, Long version, Guest... guests) {
		return Event.reconstituteBuilder().id(id).hostId("host-1").title("Event")
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0))
				.timeZone("Europe/Madrid").modality(Modality.IN_PERSON).status(status)
				.venue(new Venue("Sala", "Direccion", 0.0, 0.0, "place-id", null))
				.responseOptions(List.of(ResponseOption.create("Asisto", true), ResponseOption.create("No asisto", false)))
				.guests(List.of(guests)).version(version).build();
	}
}
