package com.gtog.shared;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.ErrorResponseException;

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
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class MvcExceptionHandlerIntegrationTest {
	private static final String TOKEN = token(101);
	private static final String INVITATION = "/api/invitations/" + TOKEN;
	private final MongoTemplate mongo;
	private final UserMongoRepository users;
	private final int port;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoSpyBean private EventRepositoryPort events;

	MvcExceptionHandlerIntegrationTest(MongoTemplate mongo, UserMongoRepository users, @Value("${local.server.port}") int port) {
		this.mongo = mongo;
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

	@AfterEach
	void closeClient() {
		client.close();
	}

	@Test
	void malformedPublicJsonKeepsItsSanitized400(CapturedOutput output) throws Exception {
		String path = INVITATION + "/response";
		var response = send("PUT", path, "{\"responseOptionId\":\"yes-id\"", Map.of(HttpHeaders.CONTENT_TYPE, "application/json"));
		assertProblem(response, 400, path, "/api/invitations");
		assertThat(JsonPath.parse(response.body()).read("$.detail", String.class)).isEqualTo("Invalid request body");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@Test
	void unsupportedPublicContentTypeReturns415InsteadOf401(CapturedOutput output) throws Exception {
		String path = INVITATION + "/response";
		assertProblem(send("PUT", path, "{}", Map.of(HttpHeaders.CONTENT_TYPE, "text/plain")), 415, path, "/api/invitations");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@Test
	void unsupportedPublicMethodReturns405AndPreservesAllow(CapturedOutput output) throws Exception {
		String path = INVITATION + "/response";
		var response = send("DELETE", path, "", Map.of());
		assertProblem(response, 405, path, "/api/invitations");
		assertThat(String.join(",", response.headers().allValues(HttpHeaders.ALLOW)).split(",\\s*")).contains("PUT");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@Test
	void anIncompatibleAcceptReturnsASanitized406(CapturedOutput output) throws Exception {
		String path = INVITATION + "/calendar";
		assertProblem(send("GET", path, "", Map.of(HttpHeaders.ACCEPT, "application/xml")), 406, path, "/api/invitations");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/invitations", "/api/invitations/%s/x" })
	void anUnknownPublicRouteReturns404InsteadOf401(String template, CapturedOutput output) throws Exception {
		String path = template.formatted(TOKEN);
		assertProblem(send("GET", path, "", Map.of()), 404, path, "/api/invitations");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@Test
	void anUnexpectedPublicExceptionReturns500WithoutLoggingItsMessageOrToken(CapturedOutput output) throws Exception {
		String secretMessage = "PRIVATE_FAILURE " + INVITATION;
		doThrow(new RuntimeException(secretMessage)).when(events).findByGuestToken(TOKEN);
		var response = send("GET", INVITATION, "", Map.of());
		assertProblem(response, 500, INVITATION, "/api/invitations");
		String correlationId = response.headers().firstValue("X-Correlation-ID").orElseThrow();
		assertThat(UUID.fromString(correlationId).toString()).isEqualTo(correlationId);
		assertThat(output.getAll()).contains(RuntimeException.class.getName(), "correlationId=" + correlationId)
				.doesNotContain(secretMessage, TOKEN, INVITATION);
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 404, 405, 406, 415, 500, 503 })
	void frameworkErrorBodiesAreRebuiltWithoutRequestPropertiesForEverySupportedStatus(int status, CapturedOutput output) throws Exception {
		var untrusted = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), "PRIVATE_DETAIL " + TOKEN);
		untrusted.setTitle("PRIVATE_TITLE " + TOKEN);
		untrusted.setType(URI.create("https://private.example.com/" + TOKEN));
		untrusted.setInstance(URI.create(INVITATION));
		untrusted.setProperty("path", INVITATION);
		untrusted.setProperty("rejectedValue", TOKEN);
		untrusted.setProperty("parameters", Map.of("guestToken", TOKEN));
		doThrow(new ErrorResponseException(HttpStatus.valueOf(status), untrusted, null)).when(events).findByGuestToken(TOKEN);
		var response = send("GET", INVITATION, "", Map.of());
		assertProblem(response, status, INVITATION, "/api/invitations");
		assertThat(response.body()).doesNotContain("PRIVATE_DETAIL", "PRIVATE_TITLE", "private.example.com");
		assertThat(output.getAll()).doesNotContain(TOKEN);
	}

	@Test
	void malformedAuthenticatedJsonAlsoHasAConstantInstance() throws Exception {
		assertProblem(send("POST", "/api/events", "{\"title\":", authenticatedHeaders()), 400, "/api/events", "/api/errors");
	}

	@Test
	void anUnknownAuthenticatedRouteReturnsASanitized404() throws Exception {
		String path = "/api/events/" + TOKEN + "/unknown-route";
		assertProblem(send("GET", path, "", authenticatedHeaders()), 404, path, "/api/errors");
	}

	@Test
	void anInvalidQueryParameterDoesNotExposeItsRejectedValue() throws Exception {
		String path = "/api/events/event-id/guests/guest-101?confirmDeleteResponse=" + TOKEN;
		assertProblem(send("DELETE", path, "", authenticatedHeaders()), 400, path, "/api/errors");
	}

	@Test
	void domainExceptionsStillUseTheirExistingAdvice(CapturedOutput output) throws Exception {
		String unknown = "A".repeat(43);
		String path = "/api/invitations/" + unknown;
		var response = send("GET", path, "", Map.of());
		assertProblem(response, 404, path, "/api/invitations");
		assertThat(response.body()).doesNotContain(unknown);
		assertThat(JsonPath.parse(response.body()).read("$.detail", String.class)).isEqualTo("Invitation not found");
		assertThat(output.getAll()).doesNotContain(TOKEN, unknown);
	}

	private void assertProblem(HttpResponse<String> response, int status, String path, String instance) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		var problem = JsonPath.parse(response.body());
		assertThat(problem.read("$.status", Integer.class)).isEqualTo(status);
		assertThat(problem.read("$.instance", String.class)).isEqualTo(instance);
		assertThat(response.body()).doesNotContain(TOKEN);
		// La raiz /api/invitations coincide con la constante permitida; las rutas con datos variables nunca deben aparecer.
		if (!path.equals(instance)) { assertThat(response.body()).doesNotContain(path); }
		Map<String, Object> properties = problem.read("$");
		assertThat(properties.keySet()).contains("title", "status", "detail", "instance")
				.isSubsetOf("type", "title", "status", "detail", "instance");
		if (properties.containsKey("type")) { assertThat(properties.get("type")).isEqualTo("about:blank"); }
	}

	private Map<String, String> authenticatedHeaders() throws Exception {
		String csrf = cookieValue(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var headers = Map.of(HttpHeaders.CONTENT_TYPE, "application/json", HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf);
		var registration = send("POST", "/api/auth/register",
				"{\"name\":\"MVC host\",\"email\":\"mvc-host@example.com\",\"rawPassword\":\"password12\",\"timeZone\":\"Europe/Madrid\"}", headers);
		assertThat(registration.statusCode()).isEqualTo(201);
		var login = send("POST", "/api/auth/login", "{\"email\":\"mvc-host@example.com\",\"password\":\"password12\"}", headers);
		assertThat(login.statusCode()).isEqualTo(200);
		return Map.of(HttpHeaders.CONTENT_TYPE, "application/json", HttpHeaders.COOKIE,
				"XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookieValue(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String cookieValue(HttpResponse<?> response, String name) {
		return response.headers().allValues(HttpHeaders.SET_COOKIE).stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
