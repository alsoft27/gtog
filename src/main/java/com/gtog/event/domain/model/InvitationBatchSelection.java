package com.gtog.event.domain.model;

import java.util.HashSet;
import java.util.List;

public final class InvitationBatchSelection {
	private InvitationBatchSelection() {}

	public static List<Guest> select(Event event, List<String> guestIds, int maxBatch) {
		if (guestIds == null) {
			if (event.getGuests().size() > maxBatch) { throw new InvalidInvitationBatchException(); }
			return List.copyOf(event.getGuests());
		}
		if (guestIds.isEmpty() || guestIds.size() > maxBatch || guestIds.stream().anyMatch(id -> id == null || id.isBlank())
				|| new HashSet<>(guestIds).size() != guestIds.size()) {
			throw new InvalidInvitationBatchException();
		}
		return guestIds.stream().map(event::getGuest).toList();
	}
}
