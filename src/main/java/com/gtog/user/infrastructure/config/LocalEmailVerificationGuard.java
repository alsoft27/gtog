package com.gtog.user.infrastructure.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

public class LocalEmailVerificationGuard implements InitializingBean {
	private static final Logger LOG = LoggerFactory.getLogger(LocalEmailVerificationGuard.class);
	private final AuthProperties properties;
	private final String baseUrl;
	public LocalEmailVerificationGuard(AuthProperties properties, String baseUrl) { this.properties = properties; this.baseUrl = baseUrl; }
	@Override public void afterPropertiesSet() {
		if (!properties.isAutoVerifyEmail()) { return; }
		String host;
		try { host = URI.create(baseUrl).getHost(); } catch (IllegalArgumentException exception) { host = null; }
		if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))) {
			throw new IllegalStateException("app.auth.auto-verify-email=true (APP_AUTH_AUTOVERIFYEMAIL) requires localhost or 127.0.0.1; rejected host=" + (host == null ? "<invalid>" : host));
		}
		LOG.warn("LOCAL BYPASS ENABLED: app.auth.auto-verify-email=true (APP_AUTH_AUTOVERIFYEMAIL=true). SOLO DESARROLLO LOCAL; NO ES PARA PRODUCCION.");
	}
}
