package com.gtog.event.domain.port.out;

import com.gtog.event.domain.model.EmailDeliveryResult;
import com.gtog.event.domain.model.InvitationEmail;

public interface EmailSenderPort {
	EmailDeliveryResult send(InvitationEmail email);
}
