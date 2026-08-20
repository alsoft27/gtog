package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueTest {

	@Test
	void createsAVenueWithAllFields() {
		Venue venue = new Venue("Sala Apolo", "Carrer Nou de la Rambla, 113, Barcelona", 41.3767, 2.1662,
				"ChIJT7Xj1uOipBIRdKY0X_0V7Xk", "Entrada por la puerta lateral");

		assertThat(venue.placeName()).isEqualTo("Sala Apolo");
		assertThat(venue.directions()).isEqualTo("Entrada por la puerta lateral");
	}

	@Test
	void allowsNullDirections() {
		Venue venue = new Venue("Sala Apolo", "Carrer Nou de la Rambla, 113, Barcelona", 41.3767, 2.1662,
				"ChIJT7Xj1uOipBIRdKY0X_0V7Xk", null);

		assertThat(venue.directions()).isNull();
	}

	@Test
	void rejectsBlankPlaceName() {
		assertThatThrownBy(() -> new Venue(" ", "address", 0.0, 0.0, "place-id", null))
				.isInstanceOf(MissingVenueFieldException.class);
	}

	@Test
	void rejectsBlankAddress() {
		assertThatThrownBy(() -> new Venue("Sala Apolo", " ", 0.0, 0.0, "place-id", null))
				.isInstanceOf(MissingVenueFieldException.class);
	}

	@Test
	void rejectsBlankPlaceId() {
		assertThatThrownBy(() -> new Venue("Sala Apolo", "address", 0.0, 0.0, " ", null))
				.isInstanceOf(MissingVenueFieldException.class);
	}

	@Test
	void rejectsNullLatitude() {
		assertThatThrownBy(() -> new Venue("Sala Apolo", "address", null, 0.0, "place-id", null))
				.isInstanceOf(MissingVenueFieldException.class);
	}

	@Test
	void rejectsNullLongitude() {
		assertThatThrownBy(() -> new Venue("Sala Apolo", "address", 0.0, null, "place-id", null))
				.isInstanceOf(MissingVenueFieldException.class);
	}
}
