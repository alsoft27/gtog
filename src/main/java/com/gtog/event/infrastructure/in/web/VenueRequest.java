package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// Sin anotaciones Jakarta en los campos obligatorios: son regla de dominio (MissingVenueFieldException, 422),
// no de formato. El backend no llama a la API de Google: confia en los datos ya resueltos que manda el cliente.
public record VenueRequest(

		@Schema(description = "Nombre del lugar", example = "Sala Apolo")
		String placeName,

		@Schema(description = "Direccion postal", example = "Carrer Nou de la Rambla, 113, Barcelona")
		String address,

		@Schema(description = "Latitud resuelta por el cliente", example = "41.3767")
		Double latitude,

		@Schema(description = "Longitud resuelta por el cliente", example = "2.1662")
		Double longitude,

		@Schema(description = "Identificador del lugar en Google Places", example = "ChIJT7Xj1uOipBIRdKY0X_0V7Xk")
		String placeId,

		@Schema(description = "Indicaciones adicionales para llegar, opcional", example = "Entrada por la puerta lateral")
		String directions) {
}
