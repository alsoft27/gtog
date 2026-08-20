package com.gtog.event.domain.model;

// El backend NO llama a la API de Google Maps ni a ninguna otra: confia en que el cliente ya ha resuelto el
// lugar (buscandolo, geocodificandolo) y le manda los datos finales. No se valida que placeId exista de verdad
// ni que latitude/longitude sean coherentes con address. Esa resolucion y esa responsabilidad son del cliente.
public record Venue(String placeName, String address, Double latitude, Double longitude, String placeId,
		String directions) {

	public Venue {
		requireNonBlank(placeName, "placeName");
		requireNonBlank(address, "address");
		requireNonBlank(placeId, "placeId");
		if (latitude == null) {
			throw new MissingVenueFieldException("latitude");
		}
		if (longitude == null) {
			throw new MissingVenueFieldException("longitude");
		}
	}

	private static void requireNonBlank(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new MissingVenueFieldException(fieldName);
		}
	}
}
