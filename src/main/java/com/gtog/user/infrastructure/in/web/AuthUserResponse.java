package com.gtog.user.infrastructure.in.web;

import com.gtog.user.domain.model.User;

public record AuthUserResponse(String id, String name, String email, String timeZone) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(user.getId(), user.getName(), user.getEmail(), user.getTimeZone());
	}
}
