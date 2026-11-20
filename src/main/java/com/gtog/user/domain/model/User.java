package com.gtog.user.domain.model;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class User {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final String id;
	private final String name;
	private final String email;
	private final Password password;
	private final String timeZone;
	private final int emailSendsInWindow;
	private final Instant windowStartedAt;
	private final boolean emailVerified;
	private final int authEmailSends;
	private final Instant authEmailWindowStartedAt;
	private final Instant lastAuthEmailAt;

	private User(String id, String name, String email, Password password, String timeZone) {
		this(id, name, email, password, timeZone, 0, null);
	}

	private User(String id, String name, String email, Password password, String timeZone, int emailSendsInWindow, Instant windowStartedAt) {
		this(id, name, email, password, timeZone, emailSendsInWindow, windowStartedAt, false, 0, null, null);
	}

	private User(String id, String name, String email, Password password, String timeZone, int emailSendsInWindow,
			Instant windowStartedAt, boolean emailVerified, int authEmailSends, Instant authEmailWindowStartedAt, Instant lastAuthEmailAt) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.timeZone = timeZone;
		this.emailSendsInWindow = emailSendsInWindow;
		this.windowStartedAt = windowStartedAt;
		this.emailVerified = emailVerified;
		this.authEmailSends = authEmailSends;
		this.authEmailWindowStartedAt = authEmailWindowStartedAt;
		this.lastAuthEmailAt = lastAuthEmailAt;
	}

	/** Para usuarios nuevos: genera UUID, normaliza el email a minúsculas y valida los campos. */
	public static User register(String name, String rawEmail, Password password, String timeZone) {
		return register(name, rawEmail, password, timeZone, false);
	}
	public static User register(String name, String rawEmail, Password password, String timeZone, boolean emailVerified) {
		if (name == null || name.isBlank()) {
			throw new BlankUserNameException();
		}
		String normalized = normalizeEmail(rawEmail);
		if (timeZone == null || timeZone.isBlank()) {
			throw new BlankTimeZoneException();
		}
		try {
			ZoneId.of(timeZone);
		} catch (DateTimeException e) {
			throw new InvalidUserTimeZoneException(timeZone);
		}
		return new User(UUID.randomUUID().toString(), name, normalized, password, timeZone, 0, null, emailVerified, 0, null, null);
	}

	public static String normalizeEmail(String rawEmail) {
		String normalized = rawEmail == null ? "" : rawEmail.strip().toLowerCase(java.util.Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(normalized).matches()) { throw new InvalidEmailException(rawEmail); }
		return normalized;
	}

	/** Para rehidratación desde persistencia: acepta los campos tal como se guardaron, sin revalidar. */
	public static User reconstitute(String id, String name, String email, Password password, String timeZone) {
		return new User(id, name, email, password, timeZone);
	}

	public String getId() { return id; }
	public static User reconstitute(String id, String name, String email, Password password, String timeZone,
			int emailSendsInWindow, Instant windowStartedAt) {
		return new User(id, name, email, password, timeZone, emailSendsInWindow, windowStartedAt);
	}
	public int getEmailSendsInWindow() { return emailSendsInWindow; }
	public static User reconstitute(String id, String name, String email, Password password, String timeZone,
			int emailSendsInWindow, Instant windowStartedAt, boolean emailVerified, int authEmailSends,
			Instant authEmailWindowStartedAt, Instant lastAuthEmailAt) {
		return new User(id, name, email, password, timeZone, emailSendsInWindow, windowStartedAt,
				emailVerified, authEmailSends, authEmailWindowStartedAt, lastAuthEmailAt);
	}
	public boolean isEmailVerified() { return emailVerified; }
	public int getAuthEmailSends() { return authEmailSends; }
	public Instant getAuthEmailWindowStartedAt() { return authEmailWindowStartedAt; }
	public Instant getLastAuthEmailAt() { return lastAuthEmailAt; }
	public Instant getWindowStartedAt() { return windowStartedAt; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public Password getPassword() { return password; }
	public String getTimeZone() { return timeZone; }
}
