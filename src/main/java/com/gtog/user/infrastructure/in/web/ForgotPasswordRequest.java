package com.gtog.user.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record ForgotPasswordRequest(@Schema(description = "Correo de la cuenta", example = "host@example.com") String email) {
	@Override public String toString() { return "ForgotPasswordRequest[redacted]"; }
}
