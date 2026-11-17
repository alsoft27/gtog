package com.gtog.shared.config;

import java.net.URI;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.gtog.user.infrastructure.out.persistence.UserMongoRepository;
import com.jayway.jsonpath.JsonPath;

import static com.gtog.event.GuestTestFixtures.token;
import static com.gtog.event.InvitationTestFixtures.event;
import static com.gtog.event.InvitationTestFixtures.guest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CsrfCookieIntegrationTest {
	private static final String COOKIE = "XSRF-TOKEN";
	private static final String HEADER = "X-XSRF-TOKEN";
	private static final String REGISTRATION = """
			{"name":"CSRF host","email":"csrf-host@example.com","rawPassword":"password12","timeZone":"Europe/Madrid"}
			""";
	private static final String LOGIN = """
			{"email":"csrf-host@example.com","password":"password12"}
			""";
	private final MockMvc mvc;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final UserMongoRepository users;
	private final int port;

	CsrfCookieIntegrationTest(MockMvc mvc, MongoTemplate mongo, EventRepositoryPort events, UserMongoRepository users,
			@Value("${local.server.port}") int port) {
		this.mvc = mvc;
		this.mongo = mongo;
		this.events = events;
		this.users = users;
		this.port = port;
	}

	@BeforeEach
	void setUp() {
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), GuestResponseDocument.class);
		users.deleteAll();
		events.save(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null)));
	}

	@Test
	void theFirstHealthGetIssuesAReadableCsrfCookieAtTheRootPath() throws Exception {
		var result = mvc.perform(get("/actuator/health")).andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.SET_COOKIE)).andExpect(cookie().exists(COOKIE))
				.andExpect(cookie().path(COOKIE, "/")).andExpect(cookie().httpOnly(COOKIE, false)).andReturn();
		assertThat(result.getResponse().getCookie(COOKIE).getValue()).isNotBlank();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "/calendar" })
	void publicInvitationAndCalendarDoNotIssueCsrfCookies(String suffix) throws Exception {
		mvc.perform(get("/api/invitations/" + token(101) + suffix)).andExpect(status().isOk())
				.andExpect(cookie().doesNotExist(COOKIE)).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "/calendar" })
	void unknownPublicInvitationsDoNotIssueCsrfCookiesEither(String suffix) throws Exception {
		mvc.perform(get("/api/invitations/" + "A".repeat(43) + suffix)).andExpect(status().isNotFound())
				.andExpect(cookie().doesNotExist(COOKIE)).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void registrationAcceptsTheRawCookieValueInTheHeader() throws Exception {
		Cookie csrf = bootstrapCookie();
		register(csrf);
		assertThat(users.findByEmail("csrf-host@example.com")).isPresent();
	}

	@Test
	void registrationRejectsADifferentHeaderValueAndDoesNotCreateTheAccount() throws Exception {
		Cookie csrf = bootstrapCookie();
		var response = rejectedRegistration(csrf);
		assertSecurityProblem(response, 403, "/api/auth/register");
		assertThat(users.findByEmail("csrf-host@example.com")).isEmpty();
	}

	@Test
	void anonymousRegistrationWithoutCsrfIsRejectedBeforeCreatingTheAccount() throws Exception {
		var response = rejectedRegistration(null);
		assertSecurityProblem(response, 403, "/api/auth/register");
		assertThat(response.headers().allValues(HttpHeaders.SET_COOKIE)).anyMatch(value -> value.startsWith(COOKIE + "="));
		assertThat(users.findByEmail("csrf-host@example.com")).isEmpty();
		Cookie csrf = bootstrapCookie();
		mvc.perform(post("/api/auth/login").cookie(csrf).header(HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(LOGIN)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/events", "/api/events/%s" })
	void missingAuthenticationIsA401ProblemWithoutTheRequestedPath(String template) throws Exception {
		String path = template.formatted(token(101));
		try (var client = HttpClient.newHttpClient()) {
			var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertSecurityProblem(response, 401, path);
		}
	}

	@Test
	void authenticatedCsrfRejectionIsAlsoA403Problem() throws Exception {
		Cookie csrf = bootstrapCookie();
		register(csrf);
		try (var client = HttpClient.newHttpClient()) {
			var loginRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.header(HttpHeaders.COOKIE, COOKIE + "=" + csrf.getValue()).header(HEADER, csrf.getValue())
					.POST(HttpRequest.BodyPublishers.ofString(LOGIN)).build();
			var login = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
			assertThat(login.statusCode()).isEqualTo(200);
			String session = login.headers().allValues(HttpHeaders.SET_COOKIE).stream()
					.flatMap(value -> HttpCookie.parse(value).stream()).filter(cookie -> cookie.getName().equals("JSESSIONID"))
					.findFirst().orElseThrow().getValue();
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/events"))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.header(HttpHeaders.COOKIE, COOKIE + "=" + csrf.getValue() + "; JSESSIONID=" + session)
					.header(HEADER, csrf.getValue() + "-different").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
			assertSecurityProblem(client.send(request, HttpResponse.BodyHandlers.ofString()), 403, "/api/events");
		}
	}

	@Test
	void loginKeepsTheExistingCsrfCookie() throws Exception {
		Cookie csrf = bootstrapCookie();
		register(csrf);
		var login = login(csrf);
		assertThat(login.getRequest().getSession(false)).isNotNull();
		mvc.perform(get("/actuator/health").cookie(csrf)).andExpect(status().isOk())
				.andExpect(cookie().doesNotExist(COOKIE));
	}

	@Test
	void logoutExpiresTheCsrfCookieAndTheNextGetIssuesANewOne() throws Exception {
		Cookie csrf = bootstrapCookie();
		register(csrf);
		var session = (MockHttpSession) login(csrf).getRequest().getSession(false);
		mvc.perform(post("/api/auth/logout").session(session).cookie(csrf).header(HEADER, csrf.getValue()))
				.andExpect(status().isNoContent()).andExpect(cookie().value(COOKIE, ""))
				.andExpect(cookie().maxAge(COOKIE, 0)).andExpect(cookie().path(COOKIE, "/"));
		assertThat(session.isInvalid()).isTrue();
		assertThat(bootstrapCookie().getValue()).isNotEqualTo(csrf.getValue());
	}

	private Cookie bootstrapCookie() throws Exception {
		return mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(cookie().exists(COOKIE))
				.andReturn().getResponse().getCookie(COOKIE);
	}

	private HttpResponse<String> rejectedRegistration(Cookie csrf) throws Exception {
		// El servidor real permite comprobar que el rechazo no acaba en un despacho a /error.
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/register"))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.POST(HttpRequest.BodyPublishers.ofString(REGISTRATION));
		if (csrf != null) {
			request.header(HttpHeaders.COOKIE, COOKIE + "=" + csrf.getValue()).header(HEADER, csrf.getValue() + "-different");
		}
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private void assertSecurityProblem(HttpResponse<String> response, int status, String path) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		var problem = JsonPath.parse(response.body());
		assertThat(problem.read("$.status", Integer.class)).isEqualTo(status);
		assertThat(problem.read("$.instance", String.class)).isEqualTo("/api/errors");
		assertThat(problem.read("$.detail", String.class)).isEqualTo(status == 403 ? "Access denied" : "Authentication required");
		assertThat(response.body()).doesNotContain(path, token(101), "\"path\"");
	}

	private void register(Cookie csrf) throws Exception {
		mvc.perform(post("/api/auth/register").cookie(csrf).header(HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(REGISTRATION)).andExpect(status().isCreated());
	}

	private MvcResult login(Cookie csrf) throws Exception {
		return mvc.perform(post("/api/auth/login").cookie(csrf).header(HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(LOGIN)).andExpect(status().isOk())
				.andExpect(cookie().doesNotExist(COOKIE)).andReturn();
	}
}
