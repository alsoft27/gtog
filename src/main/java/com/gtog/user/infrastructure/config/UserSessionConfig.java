package com.gtog.user.infrastructure.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration(proxyBeanMethods = false)
public class UserSessionConfig {
	@Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

	@Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry registry) {
		// El login manual y la cadena comparten fijacion y registro; CSRF no se renueva al autenticar.
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new RegisterSessionAuthenticationStrategy(registry)));
	}

	@Bean HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }
}
