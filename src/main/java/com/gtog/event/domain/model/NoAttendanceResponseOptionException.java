package com.gtog.event.domain.model;

public class NoAttendanceResponseOptionException extends EventDomainException {

	public NoAttendanceResponseOptionException() {
		super("At least one response option must count as attendance");
	}
}
