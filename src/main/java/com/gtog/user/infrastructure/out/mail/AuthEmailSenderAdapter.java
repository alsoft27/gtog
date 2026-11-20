package com.gtog.user.infrastructure.out.mail;

import org.springframework.stereotype.Component;
import com.gtog.user.domain.model.AuthEmail;
import com.gtog.user.domain.port.out.AuthEmailSenderPort;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.port.out.EmailSenderPort;

@Component
public class AuthEmailSenderAdapter implements AuthEmailSenderPort {
	private final EmailSenderPort transport;
	public AuthEmailSenderAdapter(EmailSenderPort transport) { this.transport = transport; }
	@Override public boolean send(AuthEmail email) {
		// Solo infraestructura cruza a event para reutilizar SMTP; no utiliza sus reservas ni cuotas.
		return transport.send(new InvitationEmail(email.recipient(), email.subject(), email.body())).accepted();
	}
}
