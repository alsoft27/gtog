package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public record GuestResponse(String eventId, String guestToken, String responseOptionId, String comment,
		String email, Instant answeredAt, Instant updatedAt) {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public static GuestResponse create(Event event, String token, String optionId, String comment, String email, Instant now) {
		if (event.getEventType() != EventType.FIXED_DATE) { throw new EventScheduleConflictException(); }
		event.requireAcceptingResponses(now);
		if (event.getResponseOptions().stream().noneMatch(option -> option.id().equals(optionId))) {
			throw new InvalidResponseOptionException();
		}
		String normalizedComment = comment == null || comment.isBlank() ? null : comment;
		if (normalizedComment != null && !event.isAllowComment()) {
			throw new ResponseCommentNotAllowedException();
		}
		if (normalizedComment != null && normalizedComment.length() > 500) {
			throw new ResponseCommentTooLongException();
		}
		String normalizedEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
		if (normalizedEmail != null && !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
			throw new InvalidResponseEmailException();
		}
		return new GuestResponse(event.getId(), token, optionId, normalizedComment, normalizedEmail, now, now);
	}

	@Override
	public String toString() { return "GuestResponse[redacted]"; }
}
