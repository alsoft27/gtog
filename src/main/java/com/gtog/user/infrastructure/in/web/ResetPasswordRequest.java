package com.gtog.user.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(@Schema(description = "Token del fragmento del enlace") String token,
		@Schema(description = "Contraseña nueva; mínimo ocho caracteres") String newPassword) {
	@Override public String toString() { return "ResetPasswordRequest[redacted]"; }
}
