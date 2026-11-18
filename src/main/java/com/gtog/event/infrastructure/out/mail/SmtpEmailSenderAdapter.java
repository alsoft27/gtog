package com.gtog.event.infrastructure.out.mail;

import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.gtog.event.domain.model.EmailDeliveryResult;
import com.gtog.event.domain.model.EmailSendFailureCode;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.port.out.EmailSenderPort;

public final class SmtpEmailSenderAdapter implements EmailSenderPort {
	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSenderAdapter.class);
	private final JavaMailSender sender;
	private final String from;

	public SmtpEmailSenderAdapter(JavaMailSender sender, String from, String fromName) {
		this.sender = sender;
		try {
			InternetAddress address = new InternetAddress(from, fromName.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip(), "UTF-8");
			address.validate();
			this.from = address.toString();
		}
		catch (AddressException | UnsupportedEncodingException exception) {
			throw new IllegalArgumentException("Invalid mail sender configuration");
		}
	}

	@Override
	public EmailDeliveryResult send(InvitationEmail email) {
		try {
			new InternetAddress(email.recipient(), true).validate();
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(email.recipient());
			message.setSubject(email.subject().replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip());
			message.setText(email.body());
			sender.send(message);
			return EmailDeliveryResult.success();
		}
		catch (AddressException exception) {
			logFailure(exception);
			return EmailDeliveryResult.failure(EmailSendFailureCode.INVALID_RECIPIENT);
		}
		catch (MailException exception) {
			EmailSendFailureCode code = classify(exception, Collections.newSetFromMap(new IdentityHashMap<>()));
			logFailure(exception);
			return EmailDeliveryResult.failure(code == null ? EmailSendFailureCode.PROVIDER_UNAVAILABLE : code);
		}
	}

	private static void logFailure(Exception exception) {
		// Los mensajes, las causas y los objetos SMTP pueden contener direcciones y el enlace privado.
		log.warn("SMTP_SEND_FAILED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
	}

	private static EmailSendFailureCode classify(Throwable exception, Set<Throwable> visited) {
		if (exception == null || !visited.add(exception)) { return null; }
		if (exception instanceof MailAuthenticationException || exception instanceof AuthenticationFailedException
				|| exception instanceof ConnectException || exception instanceof SocketTimeoutException) {
			return EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		if (exception instanceof AddressException || exception instanceof MailParseException) {
			return EmailSendFailureCode.INVALID_RECIPIENT;
		}
		if (exception instanceof SendFailedException failed && failed.getInvalidAddresses() != null
				&& failed.getInvalidAddresses().length > 0) { return EmailSendFailureCode.INVALID_RECIPIENT; }
		EmailSendFailureCode result = classify(exception.getCause(), visited);
		if (result != null) { return result; }
		if (exception instanceof MessagingException messaging) {
			result = classify(messaging.getNextException(), visited);
			if (result != null) { return result; }
		}
		if (exception instanceof MailSendException failed) {
			for (Exception nested : failed.getMessageExceptions()) {
				result = classify(nested, visited);
				if (result != null) { return result; }
			}
		}
		// El proveedor SMTP es de ejecución: usamos la API Jakarta, sin añadir dependencias de compilación.
		if (exception instanceof SendFailedException) { return EmailSendFailureCode.RECIPIENT_REJECTED; }
		return null;
	}
}
