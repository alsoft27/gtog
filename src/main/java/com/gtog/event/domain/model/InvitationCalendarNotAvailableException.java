package com.gtog.event.domain.model;

public class InvitationCalendarNotAvailableException extends EventDomainException {
	public InvitationCalendarNotAvailableException() { super("Calendar is not available for a cancelled event"); }
}
