package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.PublicInvitation;

public interface GetPublicInvitationUseCase {
	PublicInvitation get(String hostId, String eventId);
}
