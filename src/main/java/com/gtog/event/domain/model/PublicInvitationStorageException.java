package com.gtog.event.domain.model;

// No conserva causas ni mensajes de Mongo: pueden contener cualquiera de las credenciales.
public class PublicInvitationStorageException extends RuntimeException {
	public PublicInvitationStorageException() { super("Unable to store public invitation changes"); }
}
