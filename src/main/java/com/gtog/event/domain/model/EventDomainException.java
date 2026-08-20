package com.gtog.event.domain.model;

// Superclase de las violaciones de reglas de negocio del evento, para que shared las traduzca a 422 sin conocer cada subtipo.
public abstract class EventDomainException extends RuntimeException {

	protected EventDomainException(String message) {
		super(message);
	}
}
