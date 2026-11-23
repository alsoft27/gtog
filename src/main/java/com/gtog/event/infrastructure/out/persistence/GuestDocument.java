package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;

// Documento embebido en events; no tiene @Document ni coleccion propia.
public record GuestDocument(String id, String name, String email, String phoneE164, String token,
		String invitationStatus, String deliveryChannel, Instant sentAt, Instant openedAt,
		Integer emailSendCount, Instant lastEmailSentAt, String source, Instant joinedAt) {
	public GuestDocument(String id, String name, String email, String phoneE164, String token,
			String invitationStatus, String deliveryChannel, Instant sentAt, Instant openedAt,
			Integer emailSendCount, Instant lastEmailSentAt) {
		this(id, name, email, phoneE164, token, invitationStatus, deliveryChannel, sentAt, openedAt,
				emailSendCount, lastEmailSentAt, "HOST", null);
	}
	public GuestDocument(String id, String name, String email, String phoneE164, String token,
			String invitationStatus, String deliveryChannel, Instant sentAt, Instant openedAt) {
		this(id, name, email, phoneE164, token, invitationStatus, deliveryChannel, sentAt, openedAt, 0, null);
	}
}
