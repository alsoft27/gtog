package com.gtog.event.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.gtog.event.domain.model.WhatsAppLinkPolicy;
import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppInvitationConfigTest {
	@Test void theWhatsAppMaximumIsConfigurableIndependentlyOfEmailLimits() {
		new ApplicationContextRunner().withUserConfiguration(WhatsAppInvitationConfig.class)
				.withPropertyValues("app.public-base-url=https://gtog.example.com", "app.invitations.max-whatsapp-links-per-request=2",
						"app.invitations.max-batch=20", "app.invitations.max-sends-per-host-per-day=100")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(WhatsAppLinkPolicy.class).maxLinksPerRequest()).isEqualTo(2);
				});
	}
}
