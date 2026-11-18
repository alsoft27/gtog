package com.gtog.event.infrastructure.out.mail;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.gtog.event.domain.model.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailSenderAdapterTest {
	private final JavaMailSender smtp = mock(JavaMailSender.class);
	private final SmtpEmailSenderAdapter adapter = new SmtpEmailSenderAdapter(smtp, "sender@example.com", "gtog");
	private final InvitationEmail email = new InvitationEmail("private-recipient@example.com", "Título", "Texto privado y token-secreto");

	@BeforeEach
	void prepareMimeMessageWithoutAnyTransport() {
		when(smtp.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new java.util.Properties())));
	}

	@Test
	void sendsOnePlainTextMessageWithOneRecipientAndNoReplyToCcOrBcc() throws Exception {
		assertThat(adapter.send(email).accepted()).isTrue();
		var captured = ArgumentCaptor.forClass(MimeMessage.class);
		verify(smtp, times(1)).send(captured.capture());
		MimeMessage message = captured.getValue();
		assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.TO)).extracting(Object::toString).containsExactly(email.recipient());
		assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.CC)).isNull();
		assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.BCC)).isNull();
		assertThat(message.getHeader("Reply-To")).isNull();
		assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("gtog");
		assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("sender@example.com");
		assertThat(message.getSubject()).isEqualTo(email.subject()); assertThat(message.getContent()).isEqualTo(email.body());
	}

	@Test
	void rejectsInvalidRecipientsWithoutCallingTheTransport() {
		assertThat(adapter.send(new InvitationEmail("bad address", "Title", "Body")).code()).isEqualTo(EmailSendFailureCode.INVALID_RECIPIENT);
		verifyNoInteractions(smtp);
	}

	@Test
	void rejectedRecipientsAreDifferentFromProviderFailures() throws Exception {
		var rejected = new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, "private-recipient@example.com token-secreto");
		doThrow(new MailSendException("private", rejected)).when(smtp).send(any(MimeMessage.class));
		var result = adapter.send(email);
		assertThat(result.code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
		assertThat(result.code().isProviderFailure()).isFalse();
		verify(smtp, times(1)).send(any(MimeMessage.class));
	}

	@Test
	void translatesInvalidAddressesStoredInTheFailedMessageMap() throws Exception {
		var failed = new SendFailedException("private", null, null, null, new InternetAddress[] { new InternetAddress(email.recipient()) });
		doThrow(new MailSendException(Map.of(new SimpleMailMessage(), failed))).when(smtp).send(any(MimeMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.INVALID_RECIPIENT);
	}

	@Test
	void followsTheMessagingNextExceptionChainWithoutReadingMessages() throws Exception {
		var failure = new MessagingException("private-recipient@example.com");
		failure.setNextException(new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, "private"));
		doThrow(new MailSendException("private", failure)).when(smtp).send(any(MimeMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> providerFailures() {
		return java.util.stream.Stream.of(
				org.junit.jupiter.params.provider.Arguments.of(new MailAuthenticationException("private-recipient@example.com token-secreto"), "AUTH_FAILED"),
				org.junit.jupiter.params.provider.Arguments.of(new MailSendException("private-recipient@example.com", new ConnectException("token-secreto")), "CONNECTION"),
				org.junit.jupiter.params.provider.Arguments.of(new MailSendException("private-recipient@example.com", new SocketTimeoutException("token-secreto")), "TIMEOUT"),
				org.junit.jupiter.params.provider.Arguments.of(new MailSendException("private-recipient@example.com token-secreto"), "UNCLASSIFIED"));
	}

	@ParameterizedTest
	@MethodSource("providerFailures")
	void providerFailuresAreSanitizedAndNeverRetried(MailException failure, String category, CapturedOutput output) {
		doThrow(failure).when(smtp).send(any(MimeMessage.class));
		assertThat(adapter.send(email)).isEqualTo(EmailDeliveryResult.failure(EmailSendFailureCode.PROVIDER_UNAVAILABLE));
		assertThat(EmailSendFailureCode.PROVIDER_UNAVAILABLE.isProviderFailure()).isTrue();
		verify(smtp, times(1)).send(any(MimeMessage.class));
		assertThat(output.getAll()).contains("SMTP_SEND_FAILED", "category=" + category, "exceptionType=" + failure.getClass().getName(), "correlationId=")
				.doesNotContain(email.recipient(), "token-secreto");
	}

	static java.util.stream.Stream<MailException> nonRecipientFailures() throws Exception {
		var sender = new SMTPSenderFailedException(new InternetAddress("sender@example.com"), "MAIL FROM", 550, "private-recipient@example.com token-secreto");
		var mixed = new MessagingException("private-recipient@example.com token-secreto");
		mixed.setNextException(new SMTPAddressFailedException(new InternetAddress("private-recipient@example.com"), "RCPT TO", 550, "private"));
		mixed.setNextException(sender);
		return java.util.stream.Stream.of(
				new MailSendException("private", sender),
				new MailSendException("private", new SMTPSendFailedException("MAIL FROM", 550, "private", sender, null, null, null)),
				new MailSendException("private", new SMTPSendFailedException("DATA", 554, "private", null, null, null, null)),
				new MailSendException("private", new SendFailedException("private")),
				new MailParseException("private-recipient@example.com token-secreto"),
				new MailSendException("private", new SMTPAddressFailedException(new InternetAddress("other@example.com"), "RCPT TO", 550, "private")),
				new MailSendException("private", new SMTPAddressFailedException(new InternetAddress("private-recipient@example.com"), "RCPT TO", 599, "private")),
				new MailSendException("private", mixed));
	}

	@ParameterizedTest @MethodSource("nonRecipientFailures")
	void onlyClearRejectionOfThisRecipientCanAvoidTheProviderCircuitBreaker(MailException failure, CapturedOutput output) {
		doThrow(failure).when(smtp).send(any(MimeMessage.class));
		var result = adapter.send(email);
		assertThat(result.code()).isEqualTo(EmailSendFailureCode.PROVIDER_UNAVAILABLE);
		assertThat(result.code().isProviderFailure()).isTrue();
		assertThat(output.getAll()).doesNotContain(email.recipient(), "token-secreto");
	}

	@Test
	void rejectedRecipientLogsDoNotLeakSmtpMessagesOrCauses(CapturedOutput output) throws AddressException {
		var rejected = new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, email.recipient() + " token-secreto");
		doThrow(new MailSendException(email.recipient(), rejected)).when(smtp).send(any(MimeMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
		assertThat(output.getAll()).contains("SMTP_SEND_FAILED", "category=RECIPIENT_REJECTED", "correlationId=").doesNotContain(email.recipient(), "token-secreto", "RCPT TO");
	}

	@ParameterizedTest @ValueSource(booleans = { false, true })
	void rejectedSenderLogsItsInternalCategoryWithoutLeakingTheRecipient(boolean hasSenderException, CapturedOutput output) throws Exception {
		String privateMessage = email.recipient() + " token-secreto";
		Exception rejection = hasSenderException
				? new SMTPSenderFailedException(new InternetAddress("sender@example.com"), "MAIL FROM", 550, privateMessage)
				: new SMTPSendFailedException("MAIL FROM:<sender@example.com>", 550, privateMessage, null, null, null, null);
		doThrow(new MailSendException(privateMessage, rejection)).when(smtp).send(any(MimeMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.PROVIDER_UNAVAILABLE);
		assertThat(output.getAll()).contains("SMTP_SEND_FAILED", "category=SENDER_REJECTED", "exceptionType=" + MailSendException.class.getName(), "correlationId=")
				.doesNotContain(email.recipient(), "sender@example.com", "token-secreto", "MAIL FROM");
	}

	@Test
	void missingConfigurationIsUnavailableWithoutAnyTransport() {
		var result = new UnavailableEmailSenderAdapter().send(email);
		assertThat(result.code()).isEqualTo(EmailSendFailureCode.EMAIL_NOT_CONFIGURED);
		assertThat(result.code().isProviderFailure()).isTrue();
		verifyNoInteractions(smtp);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mañana: reunión y café 🎉", "Fiesta\r\nsegunda línea" })
	void theSerializedMimeDeclaresSpanishAndPreservesTheSanitizedUnicodeSubject(String subject) throws Exception {
		var transport = new MimeCapturingSender();
		transport.setDefaultEncoding("UTF-8");
		var sender = new SmtpEmailSenderAdapter(transport, "sender@example.com", "gtog");
		assertThat(sender.send(new InvitationEmail(email.recipient(), subject, "Texto plano")).accepted()).isTrue();
		var bytes = new java.io.ByteArrayOutputStream();
		transport.message.writeTo(bytes);
		var decoded = new MimeMessage(Session.getInstance(new java.util.Properties()), new java.io.ByteArrayInputStream(bytes.toByteArray()));
		assertThat(decoded.getSubject()).isEqualTo(subject.replace('\r', ' ').replace('\n', ' ')).doesNotContain("\r", "\n");
		assertThat(decoded.isMimeType("text/plain")).isTrue();
		assertThat(decoded.getHeader("Content-Language")).containsExactly("es");
	}

	// Se serializa el MIME compuesto por el adaptador, sin abrir transporte alguno.
	private static final class MimeCapturingSender extends JavaMailSenderImpl {
		private MimeMessage message;
		@Override protected void doSend(MimeMessage[] messages, Object[] originals) {
			assertThat(messages).hasSize(1);
			message = messages[0];
			try { message.saveChanges(); }
			catch (MessagingException exception) { throw new MailSendException("MIME composition failed"); }
		}
	}
}
