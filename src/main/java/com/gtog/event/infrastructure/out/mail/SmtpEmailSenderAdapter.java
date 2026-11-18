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
			EmailSendFailureCode code = classify(exception, email.recipient(), Collections.newSetFromMap(new IdentityHashMap<>()));
			logFailure(exception);
			return EmailDeliveryResult.failure(code == null ? EmailSendFailureCode.PROVIDER_UNAVAILABLE : code);
		}
	}

	private static void logFailure(Exception exception) {
		// Los mensajes, las causas y los objetos SMTP pueden contener direcciones y el enlace privado.
		log.warn("SMTP_SEND_FAILED exceptionType={} correlationId={}", exception.getClass().getName(), UUID.randomUUID());
	}

	private static EmailSendFailureCode classify(Throwable exception, String recipient, Set<Throwable> visited) {
		if (exception == null || !visited.add(exception)) { return null; }
		if (exception instanceof MailAuthenticationException || exception instanceof AuthenticationFailedException
				|| exception instanceof ConnectException || exception instanceof SocketTimeoutException) {
			return EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		if (exception instanceof AddressException address) {
			return recipient.equalsIgnoreCase(address.getRef()) ? EmailSendFailureCode.INVALID_RECIPIENT : EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		EmailSendFailureCode result = null;
		if (exception.getClass().getName().equals("org.eclipse.angus.mail.smtp.SMTPAddressFailedException")) {
			result = isExplicitRecipientRejection(exception, recipient) ? EmailSendFailureCode.RECIPIENT_REJECTED : EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		else if (exception.getClass() == SendFailedException.class) {
			var invalid = ((SendFailedException) exception).getInvalidAddresses();
			if (invalid != null && invalid.length > 0) {
				result = invalid.length == 1 && invalid[0] instanceof InternetAddress address && recipient.equalsIgnoreCase(address.getAddress())
						? EmailSendFailureCode.INVALID_RECIPIENT : EmailSendFailureCode.PROVIDER_UNAVAILABLE;
			}
		}
		else if (!(exception instanceof MailSendException) && exception.getClass() != MessagingException.class) {
			// Incluye MAIL FROM, DATA, fin de datos y cualquier 5xx o excepción sin clasificar.
			return EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		result = combine(result, classify(exception.getCause(), recipient, visited));
		if (exception instanceof MessagingException messaging) {
			result = combine(result, classify(messaging.getNextException(), recipient, visited));
		}
		if (exception instanceof MailSendException failed) {
			for (Exception nested : failed.getMessageExceptions()) {
				result = combine(result, classify(nested, recipient, visited));
			}
		}
		return result;
	}

	private static EmailSendFailureCode combine(EmailSendFailureCode first, EmailSendFailureCode second) {
		// Un fallo de proveedor prevalece aunque otra excepción encadenada rechace al destinatario.
		if (first == EmailSendFailureCode.PROVIDER_UNAVAILABLE || second == EmailSendFailureCode.PROVIDER_UNAVAILABLE) {
			return EmailSendFailureCode.PROVIDER_UNAVAILABLE;
		}
		return first == null ? second : first;
	}

	private static boolean isExplicitRecipientRejection(Throwable exception, String recipient) {
		// Angus es dependencia de ejecución. Leer solo su API estructurada, sin añadir dependencias ni leer mensajes.
		try {
			Class<?> type = exception.getClass();
			Object address = type.getMethod("getAddress").invoke(exception);
			Object command = type.getMethod("getCommand").invoke(exception);
			int code = (Integer) type.getMethod("getReturnCode").invoke(exception);
			return address instanceof InternetAddress target && recipient.equalsIgnoreCase(target.getAddress())
					&& command instanceof String smtpCommand && smtpCommand.regionMatches(true, 0, "RCPT TO", 0, 7)
					&& code >= 550 && code <= 553;
		}
		catch (ReflectiveOperationException | ClassCastException | SecurityException failure) {
			return false;
		}
	}
}
