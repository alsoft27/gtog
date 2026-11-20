package com.gtog.user.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyEmailRequest(@Schema(description = "Token del fragmento del enlace de verificación; no registrar.") String token) {
	@Override public String toString() { return "VerifyEmailRequest[redacted]"; }
}
