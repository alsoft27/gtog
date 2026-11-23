package com.gtog.event.domain.port.in;

import java.util.Optional;
import com.gtog.event.domain.model.PublicInvitation;

public interface ChangePublicInvitationUseCase {
	Optional<PublicInvitation> change(String hostId, String eventId, boolean enabled);
}
