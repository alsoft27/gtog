package com.gtog.event.infrastructure.in.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.DeliveryChannel;

@Schema(description = "Datos del invitado sin token ni enlace; respuesta de edicion")
public record GuestResponse(
		String id, String name, String email, String phoneE164, InvitationStatus invitationStatus,
		DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt) {

	public static GuestResponse from(Guest guest) {
		return new GuestResponse(guest.getId(), guest.getName(), guest.getEmail(), guest.getPhoneE164(),
				guest.getInvitationStatus(), guest.getDeliveryChannel(), guest.getSentAt(), guest.getOpenedAt());
	}
}
