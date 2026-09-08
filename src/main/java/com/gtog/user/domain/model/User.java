package com.gtog.user.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

public class User {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final String id;
	private final String name;
	private final String email;
	private final Password password;
	private final String timeZone;

	private User(String id, String name, String email, Password password, String timeZone) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.timeZone = timeZone;
	}

	/** Para usuarios nuevos: genera UUID, normaliza el email a minúsculas y valida los campos. */
	public static User register(String name, String rawEmail, Password password, String timeZone) {
		if (name == null || name.isBlank()) {
			throw new BlankUserNameException();
		}
		String normalized = rawEmail == null ? "" : rawEmail.strip().toLowerCase();
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new InvalidEmailException(rawEmail);
		}
		if (timeZone == null || timeZone.isBlank()) {
			throw new BlankTimeZoneException();
		}
		return new User(UUID.randomUUID().toString(), name, normalized, password, timeZone);
	}

	/** Para rehidratación desde persistencia: acepta los campos tal como se guardaron, sin revalidar. */
	public static User reconstitute(String id, String name, String email, Password password, String timeZone) {
		return new User(id, name, email, password, timeZone);
	}

	public String getId() { return id; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public Password getPassword() { return password; }
	public String getTimeZone() { return timeZone; }
}
