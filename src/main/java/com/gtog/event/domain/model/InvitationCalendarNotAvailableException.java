package com.gtog.event.domain.model;

public class InvitationCalendarNotAvailableException extends EventDomainException {
	public InvitationCalendarNotAvailableException() { super("Calendar is not available for a cancelled event"); }
	private InvitationCalendarNotAvailableException(String message) { super(message); }
	public static InvitationCalendarNotAvailableException withoutDefinitiveDate() {
		return new InvitationCalendarNotAvailableException("Calendar is not available without a definitive date");
	}
}
