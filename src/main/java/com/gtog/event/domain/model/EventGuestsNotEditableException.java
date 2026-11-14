package com.gtog.event.domain.model;

public class EventGuestsNotEditableException extends EventDomainException {

	public EventGuestsNotEditableException() {
		super("Guests cannot be changed in a finished or cancelled event");
	}
}
