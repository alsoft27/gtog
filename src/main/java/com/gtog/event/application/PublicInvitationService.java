package com.gtog.event.application;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.*;
import com.gtog.event.domain.port.out.*;

@Service
public class PublicInvitationService implements ChangePublicInvitationUseCase, JoinPublicInvitationUseCase, GetPublicInvitationUseCase {
	private final EventRepositoryPort events;
	private final PublicInvitationRepositoryPort invitations;
	private final PublicInvitationTokenGeneratorPort linkTokens;
	private final GuestTokenGeneratorPort guestTokens;
	private final PhoneNormalizerPort phones;
	private final Clock clock;

	public PublicInvitationService(EventRepositoryPort events, PublicInvitationRepositoryPort invitations,
			PublicInvitationTokenGeneratorPort linkTokens, GuestTokenGeneratorPort guestTokens,
			PhoneNormalizerPort phones, Clock clock) {
		this.events = events; this.invitations = invitations; this.linkTokens = linkTokens;
		this.guestTokens = guestTokens; this.phones = phones; this.clock = clock;
	}

	@Override public PublicInvitation get(String hostId, String eventId) {
		return events.findById(eventId).filter(value -> hostId.equals(value.getHostId()))
				.orElseThrow(() -> new EventNotFoundException(eventId)).getPublicInvitation();
	}

	@Override public Optional<PublicInvitation> change(String hostId, String eventId, boolean enabled) {
		Event event = events.findById(eventId).filter(value -> hostId.equals(value.getHostId()))
				.orElseThrow(() -> new EventNotFoundException(eventId));
		event.ensurePublicInvitationEditable();
		if (enabled == event.isPublicInvitationEnabled()) { return enabled ? Optional.of(event.getPublicInvitation()) : Optional.empty(); }
		String token = enabled && event.getStatus() == EventStatus.PUBLISHED ? linkTokens.generate() : null;
		PublicInvitation link = new PublicInvitation(enabled, token);
		if (token != null && (event.getId().equals(link.token())
				|| event.getGuests().stream().anyMatch(guest -> guest.getToken().equals(link.token())))) {
			throw new PublicInvitationStorageException();
		}
		return invitations.change(event, link);
	}

	@Override public Guest join(JoinPublicInvitationCommand command) {
		if (command.joinToken() == null) { throw new InvitationNotFoundException(); }
		Event event = invitations.findByToken(command.joinToken()).filter(value -> value.getStatus() != EventStatus.DRAFT)
				.orElseThrow(InvitationNotFoundException::new);
		if (command.previousGuestToken() != null && event.getGuests().stream().anyMatch(guest ->
				guest.getSource() == GuestSource.PUBLIC_LINK && command.previousGuestToken().equals(guest.getToken()))) {
			throw new PublicJoinConflictException(PublicJoinCode.ALREADY_JOINED);
		}
		var now = clock.instant();
		if (!event.responseAcceptancePolicy(now).canRespond(false)) { throw new PublicJoinConflictException(PublicJoinCode.JOIN_CLOSED); }
		Guest guest = Guest.joinPublicInvitation(command.name(), command.email(), command.phone(), guestTokens.generate(), phones, now);
		if (guest.getToken().equals(command.joinToken()) || guest.getToken().equals(event.getId())
				|| event.getGuests().stream().anyMatch(existing -> existing.getToken().equals(guest.getToken()))) {
			throw new DuplicateGuestTokenException();
		}
		// Mismo camino de E/S con y sin contacto duplicado en esta lectura; Mongo conserva la autoridad.
		Guest candidate = event.discardConflictingPublicContact(guest);
		PublicJoinWriteResult result = invitations.tryAddGuest(event, command.joinToken(), candidate, now);
		if (result == PublicJoinWriteResult.CONTACT_CONFLICT) {
			candidate = candidate.withoutContact();
			result = invitations.tryAddGuest(event, command.joinToken(), candidate, now);
		}
		if (result == PublicJoinWriteResult.CREATED) { return candidate; }
		if (result == PublicJoinWriteResult.NOT_FOUND) { throw new InvitationNotFoundException(); }
		if (result == PublicJoinWriteResult.CONTACT_CONFLICT) { throw new PublicInvitationStorageException(); }
		throw new PublicJoinConflictException(PublicJoinCode.valueOf(result.name()));
	}
}
