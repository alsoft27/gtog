package com.gtog.shared;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.json.JsonMapper;

import static com.gtog.event.GuestTestFixtures.event;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class EmailErrorPrivacyIntegrationTest {
	private static final String HOST_EMAIL = "private-host@example.com", GUEST_EMAIL = "private-guest@example.com";
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private final JsonMapper json = JsonMapper.builder().build();
	private Map<String, String> auth;

	EmailErrorPrivacyIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.passwords = passwords;
	}

	@BeforeEach
	void setUp() throws Exception {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), UserDocument.class);
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", HOST_EMAIL, passwords.encode("password12"), "UTC"));
		events.save(event("private-event", EventStatus.DRAFT, null,
				Guest.create("Guest", GUEST_EMAIL, "+34612345678", token(7), phone -> phone)));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var headers = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf);
		var login = send("POST", "/api/auth/login", json.writeValueAsString(Map.of("email", HOST_EMAIL, "password", "password12")), headers);
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}

	@AfterEach void closeClient() { client.close(); }

	record Failure(String path, String email, int status, String detail) {}
	static Stream<Failure> failures() {
		return Stream.of(
				new Failure("/api/auth/register", "private-invalid@@example.com", 422, "Invalid email address"),
				new Failure("/api/auth/register", HOST_EMAIL, 409, "Email already registered"),
				new Failure("/api/events/private-event/guests", "private-invalid-guest@@example.com", 422, null),
				new Failure("/api/events/private-event/guests", GUEST_EMAIL, 409, null));
	}

	@ParameterizedTest
	@MethodSource("failures")
	void registrationAndIndividualGuestErrorsNeverEchoEmailInBodyOrLogs(Failure failure, CapturedOutput output) throws Exception {
		Map<String, String> body = failure.path().equals("/api/auth/register")
				? Map.of("name", "Host", "email", failure.email(), "rawPassword", "password12", "timeZone", "UTC")
				: Map.of("name", "Guest", "email", failure.email());
		var result = send("POST", failure.path(), json.writeValueAsString(body), auth);
		assertThat(result.statusCode()).isEqualTo(failure.status());
		assertThat(result.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(result.body()).doesNotContain(failure.email(), HOST_EMAIL, GUEST_EMAIL, "+34612345678", token(7));
		assertThat(output.getAll()).doesNotContain(failure.email(), HOST_EMAIL, GUEST_EMAIL, "+34612345678", token(7));
		if (failure.detail() != null) {
			assertThat(JsonPath.parse(result.body()).read("$.detail", String.class)).isEqualTo(failure.detail());
		}
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
