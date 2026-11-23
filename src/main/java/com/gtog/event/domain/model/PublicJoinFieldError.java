package com.gtog.event.domain.model;

public record PublicJoinFieldError(String field, Code code) {
	public enum Code { BLANK_NAME, NAME_TOO_LONG, INVALID_NAME, INVALID_EMAIL, INVALID_PHONE }
}
