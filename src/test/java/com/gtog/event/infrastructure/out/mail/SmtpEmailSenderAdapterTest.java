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
import org.junit.jupiter.api.Test;
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

	@Test
	void sendsOnePlainTextMessageWithOneRecipientAndNoReplyToCcOrBcc() throws Exception {
		assertThat(adapter.send(email).accepted()).isTrue();
		var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(smtp, times(1)).send(captured.capture());
		SimpleMailMessage message = captured.getValue();
		assertThat(message.getTo()).containsExactly(email.recipient());
		assertThat(message.getCc()).isNull(); assertThat(message.getBcc()).isNull(); assertThat(message.getReplyTo()).isNull();
		assertThat(new InternetAddress(message.getFrom()).getPersonal()).isEqualTo("gtog");
		assertThat(new InternetAddress(message.getFrom()).getAddress()).isEqualTo("sender@example.com");
		assertThat(message.getSubject()).isEqualTo(email.subject()); assertThat(message.getText()).isEqualTo(email.body());
	}

	@Test
	void rejectsInvalidRecipientsWithoutCallingTheTransport() {
		assertThat(adapter.send(new InvitationEmail("bad address", "Title", "Body")).code()).isEqualTo(EmailSendFailureCode.INVALID_RECIPIENT);
		verifyNoInteractions(smtp);
	}

	@Test
	void rejectedRecipientsAreDifferentFromProviderFailures() throws Exception {
		var rejected = new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, "private-recipient@example.com token-secreto");
		doThrow(new MailSendException("private", rejected)).when(smtp).send(any(SimpleMailMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
		verify(smtp, times(1)).send(any(SimpleMailMessage.class));
	}

	@Test
	void translatesInvalidAddressesStoredInTheFailedMessageMap() throws Exception {
		var failed = new SendFailedException("private", null, null, null, new InternetAddress[] { new InternetAddress(email.recipient()) });
		doThrow(new MailSendException(Map.of(new SimpleMailMessage(), failed))).when(smtp).send(any(SimpleMailMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.INVALID_RECIPIENT);
	}

	@Test
	void followsTheMessagingNextExceptionChainWithoutReadingMessages() throws Exception {
		var failure = new MessagingException("private-recipient@example.com");
		failure.setNextException(new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, "private"));
		doThrow(new MailSendException("private", failure)).when(smtp).send(any(SimpleMailMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
	}

	static java.util.stream.Stream<MailException> providerFailures() {
		return java.util.stream.Stream.of(new MailAuthenticationException("private-recipient@example.com token-secreto"),
				new MailSendException("private-recipient@example.com", new ConnectException("token-secreto")),
				new MailSendException("private-recipient@example.com", new SocketTimeoutException("token-secreto")),
				new MailSendException("private-recipient@example.com token-secreto"));
	}

	@ParameterizedTest
	@MethodSource("providerFailures")
	void providerFailuresAreSanitizedAndNeverRetried(MailException failure, CapturedOutput output) {
		doThrow(failure).when(smtp).send(any(SimpleMailMessage.class));
		assertThat(adapter.send(email)).isEqualTo(EmailDeliveryResult.failure(EmailSendFailureCode.PROVIDER_UNAVAILABLE));
		verify(smtp, times(1)).send(any(SimpleMailMessage.class));
		assertThat(output.getAll()).contains("SMTP_SEND_FAILED", "exceptionType=" + failure.getClass().getName(), "correlationId=")
				.doesNotContain(email.recipient(), "token-secreto");
	}

	@Test
	void rejectedRecipientLogsDoNotLeakSmtpMessagesOrCauses(CapturedOutput output) throws AddressException {
		var rejected = new SMTPAddressFailedException(new InternetAddress(email.recipient()), "RCPT TO", 550, email.recipient() + " token-secreto");
		doThrow(new MailSendException(email.recipient(), rejected)).when(smtp).send(any(SimpleMailMessage.class));
		assertThat(adapter.send(email).code()).isEqualTo(EmailSendFailureCode.RECIPIENT_REJECTED);
		assertThat(output.getAll()).contains("SMTP_SEND_FAILED", "correlationId=").doesNotContain(email.recipient(), "token-secreto", "RCPT TO");
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
	void theSerializedMimeSubjectPreservesUnicodeAndNeutralizesLineBreaks(String subject) throws Exception {
		var transport = new MimeCapturingSender();
		transport.setDefaultEncoding("UTF-8");
		var sender = new SmtpEmailSenderAdapter(transport, "sender@example.com", "gtog");
		assertThat(sender.send(new InvitationEmail(email.recipient(), subject, "Texto plano")).accepted()).isTrue();
		var bytes = new java.io.ByteArrayOutputStream();
		transport.message.writeTo(bytes);
		var decoded = new MimeMessage(Session.getInstance(new java.util.Properties()), new java.io.ByteArrayInputStream(bytes.toByteArray()));
		assertThat(decoded.getSubject()).isEqualTo(subject.replace('\r', ' ').replace('\n', ' ')).doesNotContain("\r", "\n");
		assertThat(decoded.isMimeType("text/plain")).isTrue();
	}

	// Se ejercita la conversión real de SimpleMailMessage a MIME, sin abrir transporte alguno.
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
