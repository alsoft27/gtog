package com.gtog.event.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.gtog.event.domain.port.out.GuestTokenGeneratorPort;
import com.gtog.event.domain.port.out.PhoneNormalizerPort;

/** Preparacion pura: valida todo antes de generar invitados y nunca modifica el evento. */
public final class GuestBatch {
	private GuestBatch() {}
	public static List<Guest> prepare(Event event, List<GuestInput> inputs, int maxBatch,
			PhoneNormalizerPort phones, GuestTokenGeneratorPort tokens) {
		return prepare(event, inputs, maxBatch, phones, tokens, false);
	}
	private static List<Guest> prepare(Event event, List<GuestInput> inputs, int maxBatch,
			PhoneNormalizerPort phones, GuestTokenGeneratorPort tokens, boolean individual) {
		event.ensureGuestsEditable();
		if (inputs == null || inputs.isEmpty() || inputs.size() > maxBatch) { throw new GuestBatchRejectedException(List.of(), false); }
		var contacts = inputs.stream().map(input -> input == null ? Guest.validateContact(null, null, null, phones)
				: Guest.validateContact(input.name(), input.email(), input.phone(), phones)).toList();
		Map<String, List<Integer>> emails = new HashMap<>(), numbers = new HashMap<>();
		for (int i = 0; i < contacts.size(); i++) {
			index(emails, contacts.get(i).email(), i); index(numbers, contacts.get(i).phoneE164(), i);
		}
		Set<Integer> duplicates = new HashSet<>();
		for (var map : List.of(emails, numbers)) { map.values().stream().filter(ids -> ids.size() > 1).forEach(duplicates::addAll); }
		var errors = new ArrayList<GuestBatchError>();
		for (int i = 0; i < contacts.size(); i++) {
			for (var code : contacts.get(i).errors()) { errors.add(new GuestBatchError(i, code)); }
			if (duplicates.contains(i)) { errors.add(new GuestBatchError(i, GuestErrorCode.DUPLICATE_IN_BATCH)); }
		}
		if (!errors.isEmpty()) { throw new GuestBatchRejectedException(errors, false); }
		var created = contacts.stream().map(contact -> Guest.fromContact(contact, tokens.generate())).toList();
		if (individual) { event.addGuest(created.getFirst()); return created; }
		var existingErrors = existingErrors(event, created);
		if (!existingErrors.isEmpty()) { throw new GuestBatchRejectedException(existingErrors, true); }
		Set<String> uniqueTokens = new HashSet<>(); event.getGuests().forEach(guest -> uniqueTokens.add(guest.getToken()));
		for (Guest guest : created) { if (!uniqueTokens.add(guest.getToken())) { throw new DuplicateGuestTokenException(); } }
		return created;
	}
	public static Guest prepareIndividual(Event event, GuestInput input, PhoneNormalizerPort phones, GuestTokenGeneratorPort tokens) {
		try { return prepare(event, List.of(input), 1, phones, tokens, true).getFirst(); }
		catch (GuestBatchRejectedException exception) {
			throw exception.getErrors().getFirst().code().individualException();
		}
	}
	public static List<GuestBatchError> existingErrors(Event event, List<Guest> created) {
		var errors = new ArrayList<GuestBatchError>();
		for (int i = 0; i < created.size(); i++) {
			Guest candidate = created.get(i);
			if (event.getGuests().stream().anyMatch(candidate::sharesContact)) {
				errors.add(new GuestBatchError(i, GuestErrorCode.DUPLICATE_IN_EVENT));
			}
		}
		return List.copyOf(errors);
	}
	private static void index(Map<String, List<Integer>> map, String value, int index) {
		if (value != null) { map.computeIfAbsent(value, ignored -> new ArrayList<>()).add(index); }
	}
}
