package com.gtog.user.infrastructure.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class UserDocument {

	@Id
	private String id;
	private String name;
	private String email;
	private String passwordHash;
	private String timeZone;
	private Integer emailSendsInWindow;
	private Instant windowStartedAt;

	public UserDocument() {
	}

	public UserDocument(String id, String name, String email, String passwordHash, String timeZone) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.timeZone = timeZone;
	}

	public String getId() { return id; }
	public int getEmailSendsInWindow() { return emailSendsInWindow == null ? 0 : emailSendsInWindow; }
	public void setEmailSendsInWindow(int value) { emailSendsInWindow = value; }
	public Instant getWindowStartedAt() { return windowStartedAt; }
	public void setWindowStartedAt(Instant value) { windowStartedAt = value; }
	public void setId(String id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public String getTimeZone() { return timeZone; }
	public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
}
