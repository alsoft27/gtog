package com.gtog.event.domain.model;

public record WhatsAppConfirmationResult(String guestId, WhatsAppConfirmationStatus status) {
	// Solo se evalua tras fallar las escrituras condicionadas y volver a consultar el evento.
	public static WhatsAppConfirmationResult unchanged(Guest guest) {
		if (guest.getPhoneE164() == null || guest.getPhoneE164().isBlank()) {
			return new WhatsAppConfirmationResult(guest.getId(), WhatsAppConfirmationStatus.NO_PHONE);
		}
		if (guest.getInvitationStatus() == InvitationStatus.SENT) {
			return new WhatsAppConfirmationResult(guest.getId(), WhatsAppConfirmationStatus.ALREADY_SENT);
		}
		if (guest.getInvitationStatus() == InvitationStatus.OPENED && guest.getSentAt() != null) {
			return new WhatsAppConfirmationResult(guest.getId(), WhatsAppConfirmationStatus.ALREADY_OPENED);
		}
		throw new EventConcurrentModificationException();
	}
}
