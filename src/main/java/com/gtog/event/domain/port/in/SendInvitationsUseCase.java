package com.gtog.event.domain.port.in;

import com.gtog.event.domain.model.InvitationSendBatchResult;

public interface SendInvitationsUseCase {
	InvitationSendBatchResult send(SendInvitationsCommand command);
}
