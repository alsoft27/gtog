package com.gtog.user.domain.model;

// Superclase de las violaciones de reglas de negocio del usuario, para que shared las traduzca a 422 sin conocer cada subtipo.
public abstract class UserDomainException extends RuntimeException {

	protected UserDomainException(String message) {
		super(message);
	}
}
