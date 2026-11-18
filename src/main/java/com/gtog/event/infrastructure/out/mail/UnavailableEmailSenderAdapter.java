package com.gtog.event.infrastructure.out.mail;

import com.gtog.event.domain.model.EmailDeliveryResult;
import com.gtog.event.domain.model.EmailSendFailureCode;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.port.out.EmailSenderPort;

// Permite arrancar en local sin credenciales y sin intentar ninguna conexión SMTP.
public final class UnavailableEmailSenderAdapter implements EmailSenderPort {
	@Override
	public EmailDeliveryResult send(InvitationEmail email) {
		return EmailDeliveryResult.failure(EmailSendFailureCode.EMAIL_NOT_CONFIGURED);
	}
}
