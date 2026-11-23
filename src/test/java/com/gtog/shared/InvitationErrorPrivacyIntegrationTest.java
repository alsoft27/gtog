package com.gtog.shared;

import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.InvitationCalendarRendererPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import com.jayway.jsonpath.JsonPath;
import static com.gtog.event.GuestTestFixtures.token;
import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
@org.springframework.context.annotation.Import(InvitationErrorPrivacyIntegrationTest.ObservationConfig.class)
class InvitationErrorPrivacyIntegrationTest {
	private static final String TOKEN = token(101), VALID = "{\"responseOptionId\":\"yes-id\"}";
	private final int port;
	private final MongoTemplate mongo;
	private final InstanceObserver observer;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoBean private Clock clock;
	@MockitoSpyBean private EventRepositoryPort events;
	@MockitoSpyBean private InvitationCalendarRendererPort calendar;
	@MockitoSpyBean private com.gtog.event.domain.port.out.PublicInvitationRepositoryPort publicInvitations;
	InvitationErrorPrivacyIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, InstanceObserver observer) {
		this.port = port; this.mongo = mongo; this.observer = observer;
	}
	@BeforeEach void setUp() {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class);
		when(clock.instant()).thenReturn(NOW);
		observer.instance.set(null);
		events.save(event(EventStatus.PUBLISHED, null, guest(101, InvitationStatus.NOT_SENT, null)));
	}
	@org.junit.jupiter.api.AfterEach void closeClient() { client.close(); }

	record ErrorCase(String name, String method, String suffix, String body, String contentType, String accept, String setup, int status) {
		@Override public String toString() { return name; }
	}
	static Stream<ErrorCase> cases() {
		var cases = new ArrayList<ErrorCase>();
		for (String suffix : List.of("", "/response", "/calendar")) {
			String method = suffix.equals("/response") ? "PUT" : "GET", body = method.equals("PUT") ? VALID : "";
			cases.add(new ErrorCase(suffix + " unknown", method, suffix, body, "application/json", "*/*", "unknown", 404));
			cases.add(new ErrorCase(suffix + " draft", method, suffix, body, "application/json", "*/*", "draft", 404));
			cases.add(new ErrorCase(suffix + " method", "DELETE", suffix, "", "application/json", "*/*", "none", 405));
			cases.add(new ErrorCase(suffix + " accept", method, suffix, body, "application/json", "application/xml", "none", 406));
			cases.add(new ErrorCase(suffix + " unexpected", method, suffix, body, "application/json", "*/*", "unexpected", 500));
			// Dato almacenado invalido: la rehidratacion puede lanzar una excepcion de dominio del evento.
			cases.add(new ErrorCase(suffix + " invalid stored venue", method, suffix, body, "application/json", "*/*", "invalidVenue", 500));
			cases.add(new ErrorCase(suffix + " arbitrary domain error", method, suffix, body, "application/json", "*/*", "domain", 409));
		}
		cases.add(new ErrorCase("response malformed", "PUT", "/response", "{\"responseOptionId\":\"" + TOKEN, "application/json", "*/*", "none", 400));
		cases.add(new ErrorCase("response media type", "PUT", "/response", VALID, "text/plain", "*/*", "none", 415));
		cases.add(new ErrorCase("response cancelled", "PUT", "/response", VALID, "application/json", "*/*", "cancelled", 409));
		cases.add(new ErrorCase("response finished", "PUT", "/response", VALID, "application/json", "*/*", "finished", 409));
		cases.add(new ErrorCase("response deadline", "PUT", "/response", VALID, "application/json", "*/*", "deadline", 409));
		cases.add(new ErrorCase("response already answered", "PUT", "/response", VALID, "application/json", "*/*", "answered", 409));
		cases.add(new ErrorCase("response unknown option", "PUT", "/response", "{\"responseOptionId\":\"absent-option\"}", "application/json", "*/*", "none", 422));
		cases.add(new ErrorCase("response disabled comment", "PUT", "/response", "{\"responseOptionId\":\"yes-id\",\"comment\":\"" + TOKEN + "\"}", "application/json", "*/*", "noComments", 422));
		cases.add(new ErrorCase("response long comment", "PUT", "/response", "{\"responseOptionId\":\"yes-id\",\"comment\":\"" + "x".repeat(501) + "\"}", "application/json", "*/*", "none", 422));
		cases.add(new ErrorCase("response invalid email", "PUT", "/response", "{\"responseOptionId\":\"yes-id\",\"email\":\"" + TOKEN + "@@\"}", "application/json", "*/*", "none", 422));
		cases.add(new ErrorCase("calendar cancelled", "GET", "/calendar", "", "application/json", "*/*", "cancelled", 409));
		cases.add(new ErrorCase("calendar rendering", "GET", "/calendar", "", "application/json", "*/*", "rendering", 500));
		return cases.stream();
	}

	@ParameterizedTest(name = "{0}") @MethodSource("cases")
	void everyPublicInvitationErrorHasAConstantInstanceAndNeverEchoesItsToken(ErrorCase sample, CapturedOutput output) throws Exception {
		String requestedToken = sample.setup().equals("unknown") ? token(999) : TOKEN;
		Query query = Query.query(Criteria.where("id").is("event-id"));
		switch (sample.setup()) {
			case "draft" -> mongo.updateFirst(query, new Update().set("status", "DRAFT"), EventDocument.class);
			case "cancelled" -> mongo.updateFirst(query, new Update().set("status", "CANCELLED"), EventDocument.class);
			case "finished" -> mongo.updateFirst(query, new Update().set("status", "FINISHED"), EventDocument.class);
			case "invalidVenue" -> mongo.updateFirst(query, new Update().set("venue.placeName", ""), EventDocument.class);
			case "noComments" -> mongo.updateFirst(query, new Update().set("allowComment", false), EventDocument.class);
			case "deadline" -> mongo.updateFirst(query, new Update().set("responseDeadline", java.time.LocalDateTime.of(2027, 1, 1, 15, 0)), EventDocument.class);
			case "answered" -> mongo.insert(new GuestResponseDocument("event-id", TOKEN, "yes-id", null, null, NOW, NOW));
			case "unexpected" -> doThrow(new RuntimeException("Private " + TOKEN)).when(events).findByGuestToken(TOKEN);
			case "domain" -> doThrow(new ModalityConflictException("Private " + TOKEN)).when(events).findByGuestToken(TOKEN);
			case "rendering" -> doThrow(new InvitationCalendarGenerationException()).when(calendar).render(any());
			default -> { }
		}
		String path = "/api/invitations/" + requestedToken + sample.suffix();
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", sample.contentType()).header("Accept", sample.accept());
		var response = client.send(request.method(sample.method(), sample.body().isEmpty() ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(sample.body())).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(sample.status());
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		var privacy = new org.assertj.core.api.SoftAssertions();
		privacy.assertThat(response.body()).doesNotContain(requestedToken, TOKEN, path);
		privacy.assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/invitations");
		privacy.assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
		privacy.assertThat(output.getAll()).doesNotContain(requestedToken, TOKEN);
		privacy.assertAll();
		if (sample.setup().equals("domain") || sample.setup().equals("invalidVenue")) {
			// El observador ve la ruta que Spring ya añadió; el saneamiento posterior la sustituye.
			assertThat(observer.instance.get()).isEqualTo(URI.create(path));
		}
		if (sample.setup().equals("invalidVenue")) {
			assertThat(output.getAll()).contains("STORED_DATA_INTEGRITY_FAILURE", "MissingVenueFieldException",
					"eventId=event-id", "correlationId=" );
			assertThat(response.body()).doesNotContain("placeName", "Venue field");
		}
	}

	static Stream<ErrorCase> joinCases() {
		var cases = new ArrayList<ErrorCase>();
		for (String method : List.of("GET", "POST")) {
			for (String setup : List.of("unknown", "draft", "revoked")) {
				cases.add(new ErrorCase(method + " join " + setup, method, "", "{\"name\":\"Ana\"}", "application/json", "*/*", setup, 404));
			}
			cases.add(new ErrorCase(method + " join accept", method, "", "{\"name\":\"Ana\"}", "application/json", "application/xml", "none", 406));
			cases.add(new ErrorCase(method + " join unexpected", method, "", "{\"name\":\"Ana\"}", "application/json", "*/*", "unexpected", 500));
			cases.add(new ErrorCase(method + " join domain", method, "", "{\"name\":\"Ana\"}", "application/json", "*/*", "domain", 409));
		}
		cases.add(new ErrorCase("join unsupported method", "DELETE", "", "", "application/json", "*/*", "none", 405));
		cases.add(new ErrorCase("join media type", "POST", "", "{\"name\":\"Ana\"}", "text/plain", "*/*", "none", 415));
		cases.add(new ErrorCase("join malformed", "POST", "", "{\"name\":\"" + TOKEN, "application/json", "*/*", "none", 400));
		cases.add(new ErrorCase("join invalid", "POST", "", "{\"name\":\"\",\"email\":\"" + TOKEN + "@@\"}", "application/json", "*/*", "none", 422));
		cases.add(new ErrorCase("join cancelled", "POST", "", "{\"name\":\"Ana\"}", "application/json", "*/*", "cancelled", 409));
		cases.add(new ErrorCase("join finished", "POST", "", "{\"name\":\"Ana\"}", "application/json", "*/*", "finished", 409));
		return cases.stream();
	}
	@ParameterizedTest(name = "{0}") @MethodSource("joinCases")
	void publicJoinErrorsNeverContainEitherCredentialAndHaveAConstantInstance(ErrorCase sample, CapturedOutput output) throws Exception {
		String joinToken = token(102);
		Query query = Query.query(Criteria.where("id").is("event-id"));
		mongo.updateFirst(query, new Update().set("publicInvitation.enabled", true).set("publicInvitation.token", joinToken), EventDocument.class);
		switch (sample.setup()) {
			case "unknown" -> joinToken = token(999);
			case "draft" -> mongo.updateFirst(query, new Update().set("status", "DRAFT").unset("publicInvitation.token"), EventDocument.class);
			case "revoked" -> mongo.updateFirst(query, new Update().unset("publicInvitation"), EventDocument.class);
			case "cancelled" -> mongo.updateFirst(query, new Update().set("status", "CANCELLED"), EventDocument.class);
			case "finished" -> mongo.updateFirst(query, new Update().set("status", "FINISHED"), EventDocument.class);
			case "unexpected" -> doThrow(new RuntimeException("Private " + TOKEN + joinToken)).when(publicInvitations).findByToken(joinToken);
			case "domain" -> doThrow(new ModalityConflictException("Private " + TOKEN + joinToken)).when(publicInvitations).findByToken(joinToken);
			default -> { }
		}
		String path = "/api/invitations/join/" + joinToken;
		org.bson.Document before = mongo.getCollection("events").find(new org.bson.Document("_id", "event-id")).first();
		var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", sample.accept()).header("Content-Type", sample.contentType());
		var response = client.send(builder.method(sample.method(), sample.method().equals("POST") ? HttpRequest.BodyPublishers.ofString(sample.body()) : HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(sample.status());
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(response.body()).doesNotContain(TOKEN, joinToken, path);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/invitations");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(output.getAll()).doesNotContain(TOKEN, joinToken);
		if (sample.status() == 406) {
			assertThat(mongo.getCollection("events").find(new org.bson.Document("_id", "event-id")).first()).isEqualTo(before);
		}
	}

	@org.junit.jupiter.api.Test
	void aTestExceptionCannotSmuggleAGuestTokenInAnExtensionOnAJoinRoute(CapturedOutput output) throws Exception {
		String path = "/api/invitations/join/" + token(102) + "/extension-probe";
		var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(response.body()).doesNotContain(TOKEN, token(102), "privateExtension", "\"code\"", "\"errors\"");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/invitations");
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(output.getAll()).doesNotContain(TOKEN, token(102));
	}

	@org.springframework.boot.test.context.TestComponent
	@org.springframework.web.bind.annotation.RestController
	static class ExtensionProbe {
		static class ProbeException extends RuntimeException {}
		@org.springframework.web.bind.annotation.GetMapping("/api/invitations/join/{joinToken}/extension-probe")
		Object probe() { throw new ProbeException(); }
		@org.springframework.web.bind.annotation.ExceptionHandler(ProbeException.class)
		org.springframework.http.ProblemDetail error(jakarta.servlet.http.HttpServletResponse response) {
			response.setHeader("Set-Cookie", "gtog_join_probe=" + TOKEN + "; HttpOnly; Path=/api/invitations/join");
			var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.CONFLICT, "Invalid request");
			problem.setProperty("privateExtension", TOKEN); problem.setProperty("code", TOKEN);
			problem.setProperty("errors", List.of(Map.of("field", TOKEN, "code", TOKEN)));
			return problem;
		}
	}

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class ObservationConfig {
		@org.springframework.context.annotation.Bean InstanceObserver instanceObserver() { return new InstanceObserver(); }
		@org.springframework.context.annotation.Bean ExtensionProbe extensionProbe() { return new ExtensionProbe(); }
	}

	@org.springframework.boot.test.context.TestComponent
	@org.springframework.web.bind.annotation.ControllerAdvice
	@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
	static class InstanceObserver implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
		final java.util.concurrent.atomic.AtomicReference<URI> instance = new java.util.concurrent.atomic.AtomicReference<>();
		@Override public boolean supports(org.springframework.core.MethodParameter parameter,
				Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter) { return true; }
		@Override public Object beforeBodyWrite(Object body, org.springframework.core.MethodParameter parameter,
				org.springframework.http.MediaType type, Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter,
				org.springframework.http.server.ServerHttpRequest request, org.springframework.http.server.ServerHttpResponse response) {
			if (body instanceof org.springframework.http.ProblemDetail problem) { instance.set(problem.getInstance()); }
			return body;
		}
	}
}
