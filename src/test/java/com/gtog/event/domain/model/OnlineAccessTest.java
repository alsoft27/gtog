package com.gtog.event.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnlineAccessTest {

	@Test
	void createsAnOnlineAccessWithAlwaysVisibility() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", "123", "secret",
				"Espera en la sala", LinkVisibility.ALWAYS, null);

		assertThat(onlineAccess.platform()).isEqualTo("Zoom");
		assertThat(onlineAccess.linkVisibility()).isEqualTo(LinkVisibility.ALWAYS);
	}

	@Test
	void allowsHttpUrl() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "http://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null);

		assertThat(onlineAccess.url()).isEqualTo("http://zoom.us/j/123");
	}

	@Test
	void rejectsBlankPlatform() {
		assertThatThrownBy(() -> new OnlineAccess(" ", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null))
				.isInstanceOf(MissingOnlineAccessFieldException.class);
	}

	@Test
	void rejectsBlankUrl() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", " ", null, null, null, LinkVisibility.ALWAYS, null))
				.isInstanceOf(MissingOnlineAccessFieldException.class);
	}

	@Test
	void rejectsNullLinkVisibility() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null, null, null))
				.isInstanceOf(MissingOnlineAccessFieldException.class);
	}

	@Test
	void rejectsUrlWithoutHttpOrHttpsScheme() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "ftp://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, null))
				.isInstanceOf(InvalidOnlineAccessUrlException.class);
	}

	@Test
	void rejectsMalformedUrl() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "not a url", null, null, null, LinkVisibility.ALWAYS, null))
				.isInstanceOf(InvalidOnlineAccessUrlException.class);
	}

	@Test
	void requiresHoursBeforeWhenLinkVisibilityIsHoursBefore() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.HOURS_BEFORE, null))
				.isInstanceOf(MissingHoursBeforeException.class);
	}

	@Test
	void rejectsHoursBeforeThatIsNotPositive() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.HOURS_BEFORE, 0))
				.isInstanceOf(InvalidHoursBeforeException.class);
	}

	@Test
	void acceptsPositiveHoursBeforeWhenLinkVisibilityIsHoursBefore() {
		OnlineAccess onlineAccess = new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.HOURS_BEFORE, 24);

		assertThat(onlineAccess.hoursBefore()).isEqualTo(24);
	}

	@Test
	void rejectsHoursBeforeWhenLinkVisibilityIsAlways() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ALWAYS, 24))
				.isInstanceOf(InvalidHoursBeforeException.class);
	}

	@Test
	void rejectsHoursBeforeWhenLinkVisibilityIsOnConfirmation() {
		assertThatThrownBy(() -> new OnlineAccess("Zoom", "https://zoom.us/j/123", null, null, null,
				LinkVisibility.ON_CONFIRMATION, 24))
				.isInstanceOf(InvalidHoursBeforeException.class);
	}
}
