package com.gtog.event.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import com.gtog.event.domain.model.*;

public interface PublicInvitationRepositoryPort {
	Optional<Event> findByToken(String token);
	Optional<PublicInvitation> change(Event snapshot, PublicInvitation desired);
	PublicJoinWriteResult tryAddGuest(Event snapshot, String joinToken, Guest guest, Instant now);
}
