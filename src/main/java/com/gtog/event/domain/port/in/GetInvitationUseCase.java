package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.InvitationView;

public interface GetInvitationUseCase {

	InvitationView getInvitation(String token);
}
