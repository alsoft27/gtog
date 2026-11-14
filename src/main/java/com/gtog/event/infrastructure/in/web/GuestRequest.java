package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// Las reglas de obligatoriedad y formato de contacto son de dominio y producen 422.
public record GuestRequest(
		@Schema(description = "Nombre obligatorio del invitado", example = "Ana") String name,
		@Schema(description = "Correo; obligatorio si no hay telefono", example = "ana@example.com") String email,
		@Schema(description = "Telefono con prefijo internacional explicito; se normaliza a E.164",
				example = "+34 612 345 678") String phone) {}
