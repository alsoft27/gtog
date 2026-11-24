package com.gtog.event;

import java.time.*;
import java.util.List;
import com.gtog.event.domain.model.*;

public final class DatePollTestFixtures {
	private DatePollTestFixtures() {}
	public static Event draft(LinkVisibility visibility) {
		return Event.builder().hostId("host-id").title("Cena").description("Consulta de fechas")
				.eventType(EventType.DATE_POLL).timeZone("Europe/Madrid").responseDeadline(LocalDateTime.of(2027, 5, 9, 18, 0))
				.datePoll(new DatePollDraft(List.of(new CandidateDateDraft(LocalDate.of(2027, 5, 10), LocalTime.of(20, 0)),
						new CandidateDateDraft(LocalDate.of(2027, 5, 11), null), new CandidateDateDraft(LocalDate.of(2027, 5, 12), LocalTime.of(21, 0)))))
				.modality(visibility == null ? Modality.IN_PERSON : Modality.ONLINE)
				.venue(visibility == null ? new Venue("Sala", "Dirección", 0.0, 0.0, "p", null) : null)
				.onlineAccess(visibility == null ? null : new OnlineAccess("Zoom", InvitationTestFixtures.URL, InvitationTestFixtures.ROOM,
						InvitationTestFixtures.PASSWORD, InvitationTestFixtures.INSTRUCTIONS, visibility, visibility == LinkVisibility.HOURS_BEFORE ? 2 : null)).build();
	}
	public static Event published(LinkVisibility visibility) { Event event = draft(visibility); event.publish(); return event; }
}
