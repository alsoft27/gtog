package com.gtog.event.domain.model;

import java.net.URI;
import java.net.URISyntaxException;

public record OnlineAccess(String platform, String url, String roomId, String password, String instructions,
		LinkVisibility linkVisibility, Integer hoursBefore) {

	public OnlineAccess {
		if (platform == null || platform.isBlank()) {
			throw new MissingOnlineAccessFieldException("platform");
		}
		if (url == null || url.isBlank()) {
			throw new MissingOnlineAccessFieldException("url");
		}
		validateUrl(url);
		if (linkVisibility == null) {
			throw new MissingOnlineAccessFieldException("linkVisibility");
		}
		if (linkVisibility == LinkVisibility.HOURS_BEFORE) {
			if (hoursBefore == null) {
				throw new MissingHoursBeforeException();
			}
			if (hoursBefore <= 0) {
				throw InvalidHoursBeforeException.notPositive(hoursBefore);
			}
		}
		else if (hoursBefore != null) {
			throw InvalidHoursBeforeException.notApplicable(linkVisibility);
		}
	}

	private static void validateUrl(String url) {
		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException e) {
			throw new InvalidOnlineAccessUrlException(url);
		}
		String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new InvalidOnlineAccessUrlException(url);
		}
	}
}
