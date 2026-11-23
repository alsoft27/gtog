package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.Guest;

public interface JoinPublicInvitationUseCase { Guest join(JoinPublicInvitationCommand command); }
