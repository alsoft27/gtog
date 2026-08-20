package com.gtog.event.infrastructure.out.persistence;

// Documento embebido en EventDocument.venue, sin coleccion ni @Document propios.
public class VenueDocument {

	private final String placeName;
	private final String address;
	private final Double latitude;
	private final Double longitude;
	private final String placeId;
	private final String directions;

	public VenueDocument(String placeName, String address, Double latitude, Double longitude, String placeId,
			String directions) {
		this.placeName = placeName;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.placeId = placeId;
		this.directions = directions;
	}

	public String getPlaceName() {
		return placeName;
	}

	public String getAddress() {
		return address;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public String getPlaceId() {
		return placeId;
	}

	public String getDirections() {
		return directions;
	}
}
