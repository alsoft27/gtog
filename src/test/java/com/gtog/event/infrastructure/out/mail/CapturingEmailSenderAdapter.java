package com.gtog.event.infrastructure.out.mail;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gtog.event.domain.model.EmailDeliveryResult;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.port.out.EmailSenderPort;

@Component
@Profile("test")
public final class CapturingEmailSenderAdapter implements EmailSenderPort {
	private final Queue<InvitationEmail> attempts = new ConcurrentLinkedQueue<>();
	private final Queue<InvitationEmail> messages = new ConcurrentLinkedQueue<>();
	private final Queue<EmailDeliveryResult> results = new ConcurrentLinkedQueue<>();

	@Override
	public EmailDeliveryResult send(InvitationEmail email) {
		attempts.add(email);
		EmailDeliveryResult result = results.poll();
		if (result == null) { result = EmailDeliveryResult.success(); }
		if (result.accepted()) { messages.add(email); }
		return result;
	}

	public List<InvitationEmail> messages() { return List.copyOf(messages); }
	public List<InvitationEmail> attempts() { return List.copyOf(attempts); }
	public void enqueueResult(EmailDeliveryResult result) { results.add(result); }
	public void reset() { attempts.clear(); messages.clear(); results.clear(); }
}
