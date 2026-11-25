package com.gtog.event.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EventAvailabilitySummary(long pollRevision, List<CandidateCount> byCandidate,
		List<GuestRef> pendingGuests, List<GuestRef> unavailableGuests) {
	public EventAvailabilitySummary {
		byCandidate = List.copyOf(byCandidate);
		pendingGuests = List.copyOf(pendingGuests);
		unavailableGuests = List.copyOf(unavailableGuests);
	}

	public record GuestRef(String id, String name) {}
	public record CandidateCount(CandidateDate candidate, List<GuestRef> guests) {
		public CandidateCount { guests = List.copyOf(guests); }
	}

	public static EventAttendanceSummary dashboard(Event event, DatePollSchedule poll, List<GuestAvailabilityResponse> responses) {
		var current = currentAnswers(event, poll, responses);
		var byCandidate = new HashMap<String, List<GuestRef>>();
		for (CandidateDate candidate : poll.candidateDates()) {
			byCandidate.put(candidate.id(), new ArrayList<>());
		}
		var rows = new ArrayList<EventAttendanceSummary.GuestRow>();
		var pending = new ArrayList<GuestRef>();
		var unavailable = new ArrayList<GuestRef>();
		for (Guest guest : event.getGuests()) {
			var reference = new GuestRef(guest.getId(), guest.getName());
			GuestAvailability answer = current.get(guest.getToken());
			rows.add(new EventAttendanceSummary.GuestRow(guest, answer != null, null, null));
			if (answer == null) {
				pending.add(reference);
			} else if (answer.candidateDateIds().isEmpty()) {
				unavailable.add(reference);
			} else {
				for (String candidateId : answer.candidateDateIds()) {
					var available = byCandidate.get(candidateId);
					if (available != null) { available.add(reference); }
				}
			}
		}
		var candidates = poll.candidateDates().stream()
				.map(candidate -> new CandidateCount(candidate, byCandidate.get(candidate.id())))
				.toList();
		var breakdown = new EventAvailabilitySummary(poll.pollRevision(), candidates, pending, unavailable);
		return new EventAttendanceSummary(totals(rows.size(), pending.size()), List.of(), rows, null, breakdown);
	}

	public static EventAttendanceSummary.Totals totals(Event event, DatePollSchedule poll, List<GuestAvailabilityResponse> responses) {
		var current = currentAnswers(event, poll, responses);
		int pending = 0;
		for (Guest guest : event.getGuests()) {
			if (!current.containsKey(guest.getToken())) { pending++; }
		}
		return totals(event.getGuests().size(), pending);
	}

	private static EventAttendanceSummary.Totals totals(int guests, int pending) {
		return new EventAttendanceSummary.Totals(guests, guests - pending, pending, 0);
	}

	private static Map<String, GuestAvailability> currentAnswers(Event event, DatePollSchedule poll,
			List<GuestAvailabilityResponse> responses) {
		var current = new HashMap<String, GuestAvailability>();
		for (GuestAvailabilityResponse response : responses) {
			if (event.getId().equals(response.eventId()) && response.availability().pollRevision() == poll.pollRevision()) {
				current.put(response.guestToken(), response.availability());
			}
		}
		return current;
	}
}
