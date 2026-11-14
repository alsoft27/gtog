package com.gtog.event.infrastructure.out.persistence;

import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.DeliveryChannel;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;

@Component
public class GuestMapper {

	public GuestDocument toDocument(Guest guest) {
		return new GuestDocument(guest.getId(), guest.getName(), guest.getEmail(), guest.getPhoneE164(), guest.getToken(),
				guest.getInvitationStatus().name(), guest.getDeliveryChannel() == null ? null : guest.getDeliveryChannel().name(),
				guest.getSentAt(), guest.getOpenedAt());
	}

	public Guest toDomain(GuestDocument document) {
		return Guest.reconstitute(document.id(), document.name(), document.email(), document.phoneE164(), document.token(),
				InvitationStatus.valueOf(document.invitationStatus()),
				document.deliveryChannel() == null ? null : DeliveryChannel.valueOf(document.deliveryChannel()),
				document.sentAt(), document.openedAt());
	}
}
