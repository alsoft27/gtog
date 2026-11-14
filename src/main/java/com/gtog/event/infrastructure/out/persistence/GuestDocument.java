package com.gtog.event.infrastructure.out.persistence;

import java.time.Instant;

// Documento embebido en events; no tiene @Document ni coleccion propia.
public record GuestDocument(String id, String name, String email, String phoneE164, String token,
		String invitationStatus, String deliveryChannel, Instant sentAt, Instant openedAt) {}
