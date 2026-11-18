package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.gtog.event.domain.port.out.PhoneNormalizerPort;

public final class Guest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern E164_PATTERN = Pattern.compile("\\+[1-9][0-9]{1,14}");

	private final String id;
	private final String name;
	private final String email;
	private final String phoneE164;
	private final String token;
	private final InvitationStatus invitationStatus;
	private final DeliveryChannel deliveryChannel;
	private final Instant sentAt;
	private final Instant openedAt;
	private final int emailSendCount;
	private final Instant lastEmailSentAt;

	private Guest(String id, String name, String email, String phoneE164, String token,
			InvitationStatus invitationStatus, DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt) {
		this(id, name, email, phoneE164, token, invitationStatus, deliveryChannel, sentAt, openedAt, 0, null);
	}

	private Guest(String id, String name, String email, String phoneE164, String token,
			InvitationStatus invitationStatus, DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt,
			int emailSendCount, Instant lastEmailSentAt) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.phoneE164 = phoneE164;
		this.token = token;
		this.invitationStatus = invitationStatus;
		this.deliveryChannel = deliveryChannel;
		this.sentAt = sentAt;
		this.openedAt = openedAt;
		this.emailSendCount = emailSendCount;
		this.lastEmailSentAt = lastEmailSentAt;
	}

	public static Guest create(String name, String email, String phone, String token, PhoneNormalizerPort normalizer) {
		Contact contact = validatedContact(name, email, phone, normalizer);
		return new Guest(UUID.randomUUID().toString(), contact.name, contact.email, contact.phoneE164,
				Objects.requireNonNull(token), InvitationStatus.NOT_SENT, null, null, null);
	}

	public Guest editContact(String name, String email, String phone, PhoneNormalizerPort normalizer) {
		Contact contact = validatedContact(name, email, phone, normalizer);
		return new Guest(id, contact.name, contact.email, contact.phoneE164, token, invitationStatus,
				deliveryChannel, sentAt, openedAt, Objects.equals(this.email, contact.email) ? emailSendCount : 0,
				Objects.equals(this.email, contact.email) ? lastEmailSentAt : null);
	}

	private static Contact validatedContact(String name, String email, String phone, PhoneNormalizerPort normalizer) {
		if (name == null || name.isBlank()) {
			throw new BlankGuestNameException();
		}
		String normalizedEmail = optionalText(email);
		String normalizedPhone = optionalText(phone);
		if (normalizedEmail == null && normalizedPhone == null) {
			throw new MissingGuestContactException();
		}
		if (normalizedEmail != null) {
			normalizedEmail = normalizedEmail.toLowerCase(Locale.ROOT);
			if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
				throw new InvalidGuestEmailException();
			}
		}
		if (normalizedPhone != null) {
			normalizedPhone = normalizer.normalize(normalizedPhone);
			if (normalizedPhone == null || !E164_PATTERN.matcher(normalizedPhone).matches()) {
				throw new InvalidGuestPhoneException();
			}
		}
		return new Contact(name.strip(), normalizedEmail, normalizedPhone);
	}

	private record Contact(String name, String email, String phoneE164) {}

	private static String optionalText(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	// Rehidratacion sin generar identidad, token ni repetir la normalizacion.
	public static Guest reconstitute(String id, String name, String email, String phoneE164, String token,
			InvitationStatus invitationStatus, DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt) {
		return new Guest(id, name, email, phoneE164, token, invitationStatus, deliveryChannel, sentAt, openedAt);
	}

	public String getId() { return id; }
	public static Guest reconstitute(String id, String name, String email, String phoneE164, String token,
			InvitationStatus invitationStatus, DeliveryChannel deliveryChannel, Instant sentAt, Instant openedAt,
			int emailSendCount, Instant lastEmailSentAt) {
		return new Guest(id, name, email, phoneE164, token, invitationStatus, deliveryChannel, sentAt, openedAt, emailSendCount, lastEmailSentAt);
	}

	public int getEmailSendCount() { return emailSendCount; }
	public Instant getLastEmailSentAt() { return lastEmailSentAt; }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public String getPhoneE164() { return phoneE164; }
	public String getToken() { return token; }
	public InvitationStatus getInvitationStatus() { return invitationStatus; }
	public DeliveryChannel getDeliveryChannel() { return deliveryChannel; }
	public Instant getSentAt() { return sentAt; }
	public Instant getOpenedAt() { return openedAt; }
}
