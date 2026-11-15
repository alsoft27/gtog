package com.gtog.event;

import com.gtog.event.domain.model.DeliveryChannel;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.GuestResponse;
import com.gtog.event.domain.model.InvitationStatus;
import static com.gtog.event.InvitationTestFixtures.NOW;

public final class AttendanceTestFixtures {
	private AttendanceTestFixtures() {}

	public static Event event(EventStatus status) {
		return InvitationTestFixtures.event(status, null,
				guest(1, InvitationStatus.NOT_SENT, null), guest(2, InvitationStatus.SENT, DeliveryChannel.EMAIL),
				guest(3, InvitationStatus.OPENED, DeliveryChannel.LINK));
	}

	public static Guest guest(int id, InvitationStatus status, DeliveryChannel channel) {
		return Guest.reconstitute("guest-" + id, "Guest " + id, "contact-" + id + "@example.com", "+34612345" + (600 + id),
				GuestTestFixtures.token(id), status, channel, status == InvitationStatus.NOT_SENT ? null : NOW.minusSeconds(60),
				status == InvitationStatus.OPENED ? NOW : null);
	}

	public static GuestResponse response(Event event, int guestIndex, String optionId) {
		return new GuestResponse(event.getId(), event.getGuests().get(guestIndex).getToken(), optionId,
				"Response comment " + guestIndex, "private-response-" + guestIndex + "@example.com", NOW, NOW.plusSeconds(1));
	}
}
