package com.gtog.event.infrastructure.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReplaceResponseOptionsRequest(

		@Schema(description = "Lista completa de opciones de respuesta que sustituye a la actual, entre 2 y 5")
		@NotNull List<@Valid ResponseOptionRequest> responseOptions) {
}
