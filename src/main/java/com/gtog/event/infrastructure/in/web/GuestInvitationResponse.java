package com.gtog.event.infrastructure.in.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.DeliveryChannel;

@Schema(description = "Vista del anfitrion para alta y listado, con ruta para copiar la invitacion")
public record GuestInvitationResponse(
		String id, String name, String email, String phoneE164, InvitationStatus invitationStatus,
		DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt,
		@Schema(description = "Ruta relativa al frontend para construir el enlace individual",
				example = "/r/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX") String invitationPath,
		com.gtog.event.domain.model.GuestSource source) {

	public static GuestInvitationResponse from(Guest guest) {
		return new GuestInvitationResponse(guest.getId(), guest.getName(), guest.getEmail(), guest.getPhoneE164(),
				guest.getInvitationStatus(), guest.getDeliveryChannel(), guest.getSentAt(), guest.getOpenedAt(),
				"/r/" + guest.getToken(), guest.getSource());
	}
}
