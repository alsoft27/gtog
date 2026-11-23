package com.gtog.event.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.PreviewPublicInvitationUseCase;
import com.gtog.event.domain.port.out.PublicInvitationRepositoryPort;

@Service
public class PreviewPublicInvitationService implements PreviewPublicInvitationUseCase {
	private final PublicInvitationRepositoryPort invitations;
	private final PublicJoinLimits limits;
	private final Clock clock;
	public PreviewPublicInvitationService(PublicInvitationRepositoryPort invitations, PublicJoinLimits limits, Clock clock) {
		this.invitations = invitations; this.limits = limits; this.clock = clock;
	}
	@Override public PublicInvitationPreview preview(String joinToken, String previousGuestToken) {
		Event event = invitations.findByToken(joinToken).filter(value -> value.getStatus() != EventStatus.DRAFT)
				.orElseThrow(InvitationNotFoundException::new);
		var previous = event.getGuests().stream().filter(guest -> guest.getSource() == GuestSource.PUBLIC_LINK
				&& guest.getToken().equals(previousGuestToken)).findFirst();
		return new PublicInvitationPreview(event, previous.isEmpty() && event.canJoinPublicInvitation(clock.instant(), limits),
				previous.isPresent(), previous.map(guest -> "/r/" + guest.getToken()).orElse(null));
	}
}
