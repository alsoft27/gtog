package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gtog.event.domain.model.Venue;

public record VenueResponse(

		@Schema(description = "Nombre del lugar", example = "Sala Apolo")
		String placeName,

		@Schema(description = "Direccion postal", example = "Carrer Nou de la Rambla, 113, Barcelona")
		String address,

		@Schema(description = "Latitud", example = "41.3767")
		Double latitude,

		@Schema(description = "Longitud", example = "2.1662")
		Double longitude,

		@Schema(description = "Identificador del lugar en Google Places", example = "ChIJT7Xj1uOipBIRdKY0X_0V7Xk")
		String placeId,

		@Schema(description = "Indicaciones adicionales para llegar", example = "Entrada por la puerta lateral")
		String directions) {

	public static VenueResponse from(Venue venue) {
		return new VenueResponse(venue.placeName(), venue.address(), venue.latitude(), venue.longitude(),
				venue.placeId(), venue.directions());
	}
}
