package com.gtog.event.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gtog.event.domain.model.EmailSendFailureCode;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.port.out.EmailSenderPort;
import com.gtog.event.infrastructure.out.mail.UnavailableEmailSenderAdapter;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.shared.config.MongoIndexInitializer;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;

import static org.assertj.core.api.Assertions.assertThat;

// Contexto completo, sin perfil test ni credenciales y sin conexiones SMTP o Mongo.
@SpringBootTest(properties = {
		"spring.profiles.active=", "MAILJET_API_KEY=", "MAILJET_SECRET_KEY=", "app.mail.from=",
		"spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
				+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration" })
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UnconfiguredMailApplicationTest {
	@MockitoBean private MongoTemplate mongo;
	@MockitoBean private MongoIndexInitializer indexes;
	@MockitoBean private EventMongoRepository events;
	@MockitoBean private UserMongoRepository users;
	private final ApplicationContext context;
	private final EmailSenderPort sender;

	UnconfiguredMailApplicationTest(ApplicationContext context, EmailSenderPort sender) {
		this.context = context; this.sender = sender;
	}

	@Test
	void fullApplicationStartsWithoutCredentialsAndFailsClosedWithoutTheTestProfile() {
		assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("test");
		assertThat(context.getBeansOfType(EmailSenderPort.class)).hasSize(1);
		assertThat(sender).isExactlyInstanceOf(UnavailableEmailSenderAdapter.class);
		var result = sender.send(new InvitationEmail("guest@example.com", "Title", "Body"));
		assertThat(result.accepted()).isFalse();
		assertThat(result.code()).isEqualTo(EmailSendFailureCode.EMAIL_NOT_CONFIGURED);
		assertThat(result.code().isProviderFailure()).isTrue();
	}
}
