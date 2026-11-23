package com.gtog.event.infrastructure.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gtog.event.domain.model.PublicInvitation;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicInvitationResponse(boolean enabled,
		@Schema(description = "Ruta del enlace; null en borrador o desactivado") String path) {
	public static PublicInvitationResponse from(PublicInvitation invitation) {
		return new PublicInvitationResponse(invitation.enabled(), invitation.token() == null ? null : "/j/" + invitation.token());
	}
}
