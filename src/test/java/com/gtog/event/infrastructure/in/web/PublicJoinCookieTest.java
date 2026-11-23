package com.gtog.event.infrastructure.in.web;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PublicJoinCookieTest {
	@Test void httpsDefaultsToSecure() { assertThat(new PublicJoinCookie("https://gtog.example", null, "Lax", Duration.ofDays(30)).create("link", "guest").isSecure()).isTrue(); }
	@Test void httpDefaultsToInsecureForLocalDevelopment() { assertThat(new PublicJoinCookie("http://localhost:4200", null, "Lax", Duration.ofDays(30)).create("link", "guest").isSecure()).isFalse(); }
	@Test void explicitSecureOverridesTheDeducedDefault() { assertThat(new PublicJoinCookie("http://localhost:4200", true, "Strict", Duration.ofDays(30)).create("link", "guest").isSecure()).isTrue(); }
}
