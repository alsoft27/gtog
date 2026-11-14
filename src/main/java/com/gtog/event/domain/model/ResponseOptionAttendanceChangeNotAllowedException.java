package com.gtog.event.domain.model;

public class ResponseOptionAttendanceChangeNotAllowedException extends EventDomainException {
	public ResponseOptionAttendanceChangeNotAllowedException() {
		super("Cannot change attendance of a response option with responses on a published event");
	}
}
