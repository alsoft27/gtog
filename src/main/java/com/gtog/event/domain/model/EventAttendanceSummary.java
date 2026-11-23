package com.gtog.event.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EventAttendanceSummary(Totals totals, List<OptionCount> byOption, List<GuestRow> guests,
		PublicInvitationSummary publicInvitation) {
	public EventAttendanceSummary {
		byOption = List.copyOf(byOption);
		guests = List.copyOf(guests);
	}

	public record Totals(int guests, int answered, int pending, int attending) {}
	public record OptionCount(String optionId, String label, boolean countsAsAttendance, int count) {}
	public record Response(String responseOptionId, String label, String comment, Instant answeredAt, Instant updatedAt) {}
	public record GuestRow(Guest guest, boolean answered, Response response, Boolean optionKnown) {}
	public record PublicInvitationSummary(boolean enabled, long joined, int limit, boolean open) {}

	public EventAttendanceSummary withPublicInvitation(Event event, Instant now, PublicJoinLimits limits) {
		return new EventAttendanceSummary(totals, byOption, guests, new PublicInvitationSummary(
				event.isPublicInvitationEnabled(), event.publicJoinedCount(), limits.maxPublicGuests(), event.canJoinPublicInvitation(now, limits)));
	}

	public static EventAttendanceSummary calculate(Event event, List<GuestResponse> responses) {
		Map<String, GuestResponse> byToken = new HashMap<>();
		for (GuestResponse response : responses) {
			if (event.getId().equals(response.eventId())) { byToken.put(response.guestToken(), response); }
		}
		Map<String, ResponseOption> options = new HashMap<>();
		for (ResponseOption option : event.getResponseOptions()) { options.put(option.id(), option); }
		Map<String, Integer> counts = new HashMap<>();
		List<GuestRow> rows = new ArrayList<>();
		int answered = 0;
		int attending = 0;
		// guests[] es la fuente de pertenencia: una respuesta sin invitado vivo nunca entra en los totales.
		for (Guest guest : event.getGuests()) {
			GuestResponse response = byToken.get(guest.getToken());
			if (response == null) {
				rows.add(new GuestRow(guest, false, null, null));
				continue;
			}
			answered++;
			ResponseOption option = options.get(response.responseOptionId());
			if (option != null) {
				counts.merge(option.id(), 1, Integer::sum);
				if (option.countsAsAttendance()) { attending++; }
			}
			rows.add(new GuestRow(guest, true, new Response(response.responseOptionId(),
					option == null ? null : option.label(), response.comment(), response.answeredAt(), response.updatedAt()), option != null));
		}
		var byOption = event.getResponseOptions().stream().map(option -> new OptionCount(option.id(), option.label(),
				option.countsAsAttendance(), counts.getOrDefault(option.id(), 0))).toList();
		return new EventAttendanceSummary(new Totals(rows.size(), answered, rows.size() - answered, attending), byOption, rows, null);
	}
}
