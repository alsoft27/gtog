package com.gtog.user.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.gtog.user.domain.port.out.SessionInvalidationPort;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(ManualLoginSessionIntegrationTest.SessionFixtureConfiguration.class)
class ManualLoginSessionIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final PasswordEncoder passwords;
	private final SessionRegistry registry;
	private final SessionInvalidationPort invalidation;
	private final HttpClient client = HttpClient.newHttpClient();

	ManualLoginSessionIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, PasswordEncoder passwords,
			SessionRegistry registry, SessionInvalidationPort invalidation) {
		this.port = port; this.mongo = mongo; this.passwords = passwords; this.registry = registry; this.invalidation = invalidation;
	}

	@BeforeEach void setUp() {
		for (Object principal : registry.getAllPrincipals()) {
			registry.getAllSessions(principal, true).forEach(session -> registry.removeSessionInformation(session.getSessionId()));
		}
		mongo.remove(new Query(), UserDocument.class);
		mongo.remove(new Query(), EventDocument.class);
		mongo.insert(new UserDocument("host-session", "Host", "session-host@example.com", passwords.encode("password12"), "Europe/Madrid"));
	}

	@Test void aLoginWithAnExistingSessionChangesItsId() throws Exception {
		Browser browser = anonymousBrowser();
		HttpResponse<String> result = login(browser);
		assertThat(result.statusCode()).isEqualTo(200);
		String authenticatedId = cookie(result, "JSESSIONID").orElse(browser.session());
		assertThat(authenticatedId).isNotEqualTo(browser.session());
		assertThat(events(new Browser(browser.csrf(), authenticatedId)).statusCode()).isEqualTo(200);
		assertThat(events(browser).statusCode()).isEqualTo(401);
	}

	@Test void loginNeitherIssuesNorRenewsTheExistingCsrfCookie() throws Exception {
		Browser browser = anonymousBrowser();
		var result = login(browser);
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(cookie(result, "XSRF-TOKEN")).isEmpty();
		Browser authenticated = new Browser(browser.csrf(), cookie(result, "JSESSIONID").orElseThrow());
		var health = send("GET", "/actuator/health", "", Map.of("Cookie", authenticated.cookieHeader()));
		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(cookie(health, "XSRF-TOKEN")).isEmpty();
		// La misma cookie y cabecera crudas siguen siendo validas tras el login.
		var logout = logout(authenticated);
		assertThat(logout.statusCode()).isEqualTo(204);
	}

	@Test void independentLoginsAreRegisteredUnderTheSameStableUserId() throws Exception {
		Browser first = authenticatedBrowser(), second = authenticatedBrowser();
		assertThat(first.session()).isNotEqualTo(second.session());
		assertThat(registry.getAllPrincipals()).hasSize(1);
		assertThat(registry.getAllSessions(principal("host-session"), false)).extracting(session -> session.getSessionId())
				.containsExactlyInAnyOrder(first.session(), second.session());
	}

	@Test void invalidatingAllSessionsRejectsBothWithADirect401Problem() throws Exception {
		Browser first = authenticatedBrowser(), second = authenticatedBrowser();
		assertThat(events(first).statusCode()).isEqualTo(200);
		assertThat(events(second).statusCode()).isEqualTo(200);
		invalidation.invalidateAll("host-session");
		assertThat(registry.getAllSessions(principal("host-session"), true)).hasSize(2)
				.allSatisfy(session -> assertThat(session.isExpired()).isTrue());
		for (Browser browser : List.of(first, second)) {
			var result = events(browser);
			assertThat(result.statusCode()).isEqualTo(401);
			assertThat(result.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
			assertThat(result.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
			assertThat(result.headers().firstValue("Location")).isEmpty();
			assertThat(JsonPath.parse(result.body()).read("$.instance", String.class)).isEqualTo("/api/errors");
			assertThat(JsonPath.parse(result.body()).read("$.detail", String.class)).isEqualTo("Authentication required");
			assertThat(result.body()).doesNotContain(first.session(), second.session(), "session-host@example.com", "host-session", "/api/events");
			assertThat(registry.getSessionInformation(browser.session())).isNull();
		}
		assertThat(registry.getAllSessions(principal("host-session"), true)).isEmpty();
	}

	@Test void invalidationDoesNotExpireAnotherUsersSession() throws Exception {
		mongo.insert(new UserDocument("other-host", "Other", "other-session@example.com", passwords.encode("password12"), "Europe/Madrid"));
		Browser target = authenticatedBrowser();
		Browser anonymous = anonymousBrowser();
		var otherLogin = login(anonymous, "other-session@example.com");
		assertThat(otherLogin.statusCode()).isEqualTo(200);
		Browser other = new Browser(anonymous.csrf(), cookie(otherLogin, "JSESSIONID").orElseThrow());
		invalidation.invalidateAll("host-session");
		assertThat(events(target).statusCode()).isEqualTo(401);
		assertThat(events(other).statusCode()).isEqualTo(200);
		assertThat(registry.getAllSessions(principal("other-host"), false)).hasSize(1);
	}

	@Test void invalidatingAnUnknownUserLeavesRegisteredSessionsActive() throws Exception {
		Browser browser = authenticatedBrowser();
		invalidation.invalidateAll("unknown-user");
		assertThat(events(browser).statusCode()).isEqualTo(200);
		assertThat(registry.getAllSessions(principal("host-session"), false)).hasSize(1);
	}

	@Test void switchingAccountsOnTheSameSessionMovesRegistrationAndInvalidationToTheNewUser() throws Exception {
		mongo.insert(new UserDocument("other-host", "Other", "other-session@example.com", passwords.encode("password12"), "Europe/Madrid"));
		Browser firstUser = authenticatedBrowser();
		assertThat(events(firstUser).statusCode()).isEqualTo(200);
		assertThat(registry.getAllSessions(principal("host-session"), false)).extracting(session -> session.getSessionId())
				.containsExactly(firstUser.session());

		// B se autentica con la misma cookie de sesion y CSRF que estaba usando A.
		var secondLogin = login(firstUser, "other-session@example.com");
		assertThat(secondLogin.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(secondLogin.body()).read("$.id", String.class)).isEqualTo("other-host");
		Browser secondUser = new Browser(firstUser.csrf(), cookie(secondLogin, "JSESSIONID").orElseThrow());
		assertThat(secondUser.session()).isNotEqualTo(firstUser.session());
		assertThat(registry.getSessionInformation(firstUser.session())).isNull();
		assertThat(registry.getAllSessions(principal("host-session"), true)).isEmpty();
		assertThat(registry.getAllSessions(principal("other-host"), false)).extracting(session -> session.getSessionId())
				.containsExactly(secondUser.session());
		assertThat(events(firstUser).statusCode()).isEqualTo(401);

		invalidation.invalidateAll("host-session");
		assertThat(events(secondUser).statusCode()).isEqualTo(200);
		assertThat(registry.getSessionInformation(secondUser.session()).isExpired()).isFalse();
		invalidation.invalidateAll("other-host");
		assertThat(events(secondUser).statusCode()).isEqualTo(401);
		assertThat(registry.getSessionInformation(secondUser.session())).isNull();
		assertThat(registry.getAllPrincipals()).isEmpty();
	}

	@Test void logoutRemovesTheSessionFromTheRegistryAndExpiresBothCookies() throws Exception {
		Browser browser = authenticatedBrowser();
		assertThat(registry.getSessionInformation(browser.session())).isNotNull();
		var result = logout(browser);
		assertThat(result.statusCode()).isEqualTo(204);
		var cookies = result.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream()).toList();
		for (String name : List.of("JSESSIONID", "XSRF-TOKEN")) {
			assertThat(cookies).filteredOn(cookie -> cookie.getName().equals(name)).singleElement().satisfies(cookie -> {
				assertThat(cookie.getValue()).isEmpty(); assertThat(cookie.getMaxAge()).isZero();
			});
		}
		assertThat(registry.getSessionInformation(browser.session())).isNull();
		assertThat(registry.getAllPrincipals()).isEmpty();
		assertThat(events(browser).statusCode()).isEqualTo(401);
	}

	private record Browser(String csrf, String session) {
		String cookieHeader() { return "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + session; }
		@Override public String toString() { return "Browser[redacted]"; }
	}
	private static AuthenticatedUser principal(String id) {
		return new AuthenticatedUser(id, "Changed name", "changed@example.com", "changed-hash", "UTC");
	}
	private Browser authenticatedBrowser() throws Exception {
		Browser anonymous = anonymousBrowser();
		var result = login(anonymous); assertThat(result.statusCode()).isEqualTo(200);
		return new Browser(anonymous.csrf(), cookie(result, "JSESSIONID").orElseThrow());
	}
	private Browser anonymousBrowser() throws Exception {
		var health = send("GET", "/actuator/health", "", Map.of());
		assertThat(health.statusCode()).isEqualTo(200);
		String csrf = cookie(health, "XSRF-TOKEN").orElseThrow();
		var session = send("GET", "/api/invitations/session-fixture", "", Map.of("Cookie", "XSRF-TOKEN=" + csrf));
		assertThat(session.statusCode()).isEqualTo(200);
		return new Browser(csrf, cookie(session, "JSESSIONID").orElseThrow());
	}
	private HttpResponse<String> login(Browser browser) throws Exception {
		return login(browser, "session-host@example.com");
	}
	private HttpResponse<String> login(Browser browser, String email) throws Exception {
		return send("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", browser.cookieHeader(), "X-XSRF-TOKEN", browser.csrf()));
	}
	private HttpResponse<String> logout(Browser browser) throws Exception {
		return send("POST", "/api/auth/logout", "", Map.of("Cookie", browser.cookieHeader(), "X-XSRF-TOKEN", browser.csrf()));
	}
	private HttpResponse<String> events(Browser browser) throws Exception {
		return send("GET", "/api/events", "", Map.of("Cookie", browser.cookieHeader()));
	}
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
	}
	private static Optional<String> cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream())
				.filter(value -> value.getName().equals(name)).map(HttpCookie::getValue).findFirst();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SessionFixtureConfiguration {
		@Bean SessionFixtureController sessionFixtureController() { return new SessionFixtureController(); }
	}
	@TestComponent
	@RestController
	static class SessionFixtureController {
		// Solo en este contexto de test: permite empezar el login con una sesion anonima real.
		@GetMapping("/api/invitations/session-fixture") String createSession(HttpServletRequest request) {
			request.getSession(true);
			return "ok";
		}
	}
}
