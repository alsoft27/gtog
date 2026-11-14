package com.gtog.event.domain.model;

// No conserva causas ni mensajes del driver: pueden contener la credencial del invitado.
public class GuestResponseStorageException extends RuntimeException {
	public GuestResponseStorageException() { super("Unable to store guest response"); }
}
