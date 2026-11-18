package com.gtog.event.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.core.io.support.ResourcePropertySource;

import com.gtog.event.domain.port.out.EmailSenderPort;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.infrastructure.out.mail.*;

import static org.assertj.core.api.Assertions.*;

class InvitationMailConfigTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(context -> {
				try {
					context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
				}
				catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
			})
			.withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class, MailHealthContributorAutoConfiguration.class))
			.withUserConfiguration(InvitationMailConfig.class)
			.withPropertyValues("spring.mail.username=", "spring.mail.password=", "app.mail.from=");

	@Test
	void startsWithoutCredentialsWithAnUnavailableSenderAndNoHealthConnection() {
		runner.run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(EmailSenderPort.class).doesNotHaveBean("mailHealthContributor");
			assertThat(context.getBean(EmailSenderPort.class)).isInstanceOf(UnavailableEmailSenderAdapter.class);
		});
	}

	@Test
	void configuredMailUsesTheSmtpAdapterAndTheRequiredTlsAndTimeouts() {
		runner.withPropertyValues("spring.mail.username=fake-key", "spring.mail.password=fake-secret", "app.mail.from=sender@example.com")
				.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(EmailSenderPort.class)).isInstanceOf(SmtpEmailSenderAdapter.class);
			JavaMailSenderImpl smtp = context.getBean(JavaMailSenderImpl.class);
			assertThat(smtp.getHost()).isEqualTo("in-v3.mailjet.com"); assertThat(smtp.getPort()).isEqualTo(587);
			assertThat(smtp.getDefaultEncoding()).isEqualTo("UTF-8");
			assertThat(smtp.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
					.containsEntry("mail.smtp.starttls.enable", "true").containsEntry("mail.smtp.starttls.required", "true")
					.containsEntry("mail.smtp.connectiontimeout", "5000").containsEntry("mail.smtp.timeout", "10000")
					.containsEntry("mail.smtp.writetimeout", "10000").containsEntry("mail.debug", "false");
		});
	}

	@Test
	void testProfileAlwaysUsesTheFakeEvenWithCredentialsPresent() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
				.withUserConfiguration(CapturingEmailSenderAdapter.class)
				.withPropertyValues("spring.mail.username=fake-key", "spring.mail.password=fake-secret", "app.mail.from=sender@example.com")
				.run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(EmailSenderPort.class);
			var fake = context.getBean(CapturingEmailSenderAdapter.class);
			assertThat(context.getBean(EmailSenderPort.class)).isSameAs(fake);
			var email = new InvitationEmail("guest@example.com", "Title", "Body");
			assertThat(fake.send(email).accepted()).isTrue(); assertThat(fake.messages()).containsExactly(email);
		});
	}

	@Test
	void conditionReportConfirmsTheMailHealthPropertyDisablesTheContributor() {
		runner.run(context -> {
			var outcomes = ConditionEvaluationReport.get(context.getBeanFactory()).getConditionAndOutcomesBySource()
					.get(MailHealthContributorAutoConfiguration.class.getName());
			assertThat(outcomes).isNotNull();
			assertThat(outcomes.isFullMatch()).isFalse();
			assertThat(outcomes).anySatisfy(outcome -> {
				assertThat(outcome.getOutcome().isMatch()).isFalse();
				assertThat(outcome.getOutcome().getMessage()).contains("management.health.mail.enabled is false");
			});
			outcomes.forEach(outcome -> System.out.println("Mail health condition: " + outcome.getOutcome()));
			assertThat(context).doesNotHaveBean("mailHealthContributor");
		});
	}

	@Test
	void enablingTheVerifiedPropertyRegistersTheHealthContributorWithoutCallingIt() {
		runner.withPropertyValues("management.health.mail.enabled=true").run(context -> {
			assertThat(context).hasNotFailed().hasBean("mailHealthContributor");
		});
	}
}
