package com.gtog.event.domain.model;

public class PublicJoinConflictException extends EventDomainException {
	private final PublicJoinCode code;
	public PublicJoinConflictException(PublicJoinCode code) { super("Public invitation request conflicts with the current state"); this.code = code; }
	public PublicJoinCode getCode() { return code; }
}
