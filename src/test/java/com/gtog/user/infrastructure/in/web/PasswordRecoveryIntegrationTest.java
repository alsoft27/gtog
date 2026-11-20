package com.gtog.user.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
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
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.out.*;
import com.gtog.user.infrastructure.out.persistence.*;
import com.gtog.event.infrastructure.out.mail.CapturingEmailSenderAdapter;
import com.gtog.event.domain.model.EmailDeliveryResult;
import com.gtog.event.domain.model.EmailSendFailureCode;
import com.jayway.jsonpath.JsonPath;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "app.auth.auto-verify-email=false", "app.public-base-url=http://localhost:4200" })
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryIntegrationTest {
	private static final String EMAIL = "private-reset@example.com", OTHER_EMAIL = "other-reset@example.com";
	private static final String OLD = "old-password12", NEW = "new-password34";
	private final int port;
	private final MongoTemplate mongo;
	private final UserMongoRepository users;
	private final CapturingEmailSenderAdapter mail;
	private final AuthTokenRepositoryPort tokens;
	private final PasswordEncoder encoder;
	private final SessionRegistry registry;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoBean private Clock clock;
	@MockitoSpyBean private PasswordHasherPort hasher;
	@MockitoSpyBean private UserRepositoryPort userPort;
	private String csrf;
	PasswordRecoveryIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, UserMongoRepository users,
			CapturingEmailSenderAdapter mail, AuthTokenRepositoryPort tokens, PasswordEncoder encoder, SessionRegistry registry) {
		this.port = port; this.mongo = mongo; this.users = users; this.mail = mail; this.tokens = tokens; this.encoder = encoder; this.registry = registry;
	}
	@BeforeEach void setUp() throws Exception {
		registry.getAllPrincipals().forEach(principal -> registry.getAllSessions(principal, true).forEach(session -> registry.removeSessionInformation(session.getSessionId())));
		mongo.remove(new Query(), UserDocument.class); mongo.remove(new Query(), AuthTokenDocument.class); mail.reset(); when(clock.instant()).thenReturn(NOW);
		mongo.insert(new UserDocument("reset-user", "Private Name", EMAIL, encoder.encode(OLD), "UTC"));
		csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
	}
	@Test void firstAnonymousMeBootstrapsCsrfForForgotPassword() throws Exception {
		var me = send("GET", "/api/auth/me", "", Map.of()); assertThat(me.statusCode()).isEqualTo(401);
		String value = cookie(me, "XSRF-TOKEN");
		var result = send("POST", "/api/auth/forgot-password", "{\"email\":\"unknown@example.com\"}", headers(value));
		assertThat(result.statusCode()).isEqualTo(202); noStore(result);
	}
	@Test void firstAnonymousMeBootstrapsCsrfForResetPassword() throws Exception {
		var me = send("GET", "/api/auth/me", "", Map.of()); assertThat(me.statusCode()).isEqualTo(401);
		String value = cookie(me, "XSRF-TOKEN");
		var result = send("POST", "/api/auth/reset-password", resetBody("A".repeat(43), NEW), headers(value));
		assertThat(result.statusCode()).isEqualTo(400); noStore(result);
	}
	@Test void existingUnknownCoolingAndDailyLimitedAccountsHaveIdenticalResponses(CapturedOutput output) throws Exception {
		var existing = forgot(EMAIL); var unknown = forgot("nonexistent-private@example.com"); var cooling = forgot(EMAIL);
		mongo.updateFirst(Query.query(Criteria.where("id").is("reset-user")), new Update().set("authEmailSends", 5).set("lastAuthEmailAt", NOW.minusSeconds(300)), UserDocument.class);
		var limited = forgot(EMAIL);
		for (var result : List.of(existing, unknown, cooling, limited)) {
			assertThat(result.statusCode()).isEqualTo(202); assertThat(result.body()).isEqualTo(existing.body());
			assertThat(withoutDate(result)).isEqualTo(withoutDate(existing)); noStore(result);
		}
		assertThat(mail.messages()).hasSize(1); assertThat(output.getAll()).doesNotContain("nonexistent-private@example.com");
	}
	@Test void theResetMailAndMongoDocumentContainOnlyTheIntendedData() throws Exception {
		forgot(EMAIL); String raw = token(); var message = mail.messages().getFirst();
		URI link = URI.create(message.body().lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow());
		assertThat(link.getPath()).isEqualTo("/reset-password"); assertThat(link.getFragment()).isEqualTo("token=" + raw); assertThat(link.getQuery()).isNull();
		assertThat(message.body()).contains("Si no lo has pedido, ignóralo.").doesNotContain(EMAIL, "Private Name", OLD);
		var doc = mongo.getCollection("auth_tokens").find(new Document("userId", "reset-user")).first(); assertThat(doc).isNotNull();
		assertThat(doc.getString("hash")).isEqualTo(AuthToken.hash(raw)); assertThat(doc.getString("purpose")).isEqualTo("PASSWORD_RESET");
		assertThat(doc.getDate("expiresAt").toInstant()).isEqualTo(NOW.plusSeconds(3600)); assertThat(doc.toJson()).doesNotContain(raw, EMAIL);
	}
	@Test void shortPasswordReturns422WithoutConsumingTheToken() throws Exception {
		forgot(EMAIL); String raw = token(); var result = reset(raw, "short");
		assertThat(result.statusCode()).isEqualTo(422); noStore(result); assertThat(result.body()).doesNotContain(raw, EMAIL);
		assertThat(tokenExists(raw)).isTrue(); assertThat(reset(raw, NEW).statusCode()).isEqualTo(200);
	}
	@Test void hashingFailureAfterConsumptionSpendsTheTokenAndSanitizesErrors(CapturedOutput output) throws Exception {
		forgot(EMAIL); String raw = token();
		doThrow(new IllegalStateException(raw + " " + EMAIL)).when(hasher).hash(NEW);
		assertSanitizedFailure(reset(raw, NEW), raw, output);
		assertThat(tokenExists(raw)).isFalse(); assertThat(reset(raw, NEW).statusCode()).isEqualTo(400);
		assertThat(encoder.matches(OLD, users.findById("reset-user").orElseThrow().getPasswordHash())).isTrue();
	}
	@Test void persistenceFailureAfterConsumptionSpendsTheTokenAndSanitizesErrors(CapturedOutput output) throws Exception {
		forgot(EMAIL); String raw = token();
		doThrow(new IllegalStateException(raw + " " + EMAIL)).when(userPort).updatePasswordAndVerify(eq("reset-user"), anyString());
		assertSanitizedFailure(reset(raw, NEW), raw, output);
		assertThat(tokenExists(raw)).isFalse(); assertThat(reset(raw, NEW).statusCode()).isEqualTo(400);
		assertThat(encoder.matches(OLD, users.findById("reset-user").orElseThrow().getPasswordHash())).isTrue();
	}
	@Test void resetChangesPasswordVerifiesEmailInvalidatesOnlyItsSessionsAndDeletesAllItsTokens() throws Exception {
		mongo.insert(new UserDocument("other-user", "Other", OTHER_EMAIL, encoder.encode(OLD), "UTC"));
		var first = login(EMAIL, OLD); var second = login(EMAIL, OLD); var other = login(OTHER_EMAIL, OLD);
		String verification = "V".repeat(43), otherToken = "O".repeat(43);
		tokens.replace(new AuthToken("reset-user", AuthTokenPurpose.EMAIL_VERIFICATION, AuthToken.hash(verification), NOW.plusSeconds(86400)));
		tokens.replace(new AuthToken("other-user", AuthTokenPurpose.EMAIL_VERIFICATION, AuthToken.hash(otherToken), NOW.plusSeconds(86400)));
		mongo.updateFirst(Query.query(Criteria.where("id").is("reset-user")), new Update().set("emailSendsInWindow", 9).set("windowStartedAt", NOW), UserDocument.class);
		forgot(EMAIL); String raw = token();
		assertThat(send("POST", "/api/auth/verify-email", "{\"token\":\"" + raw + "\"}", headers(csrf)).statusCode()).isEqualTo(400);
		assertThat(reset(verification, NEW).statusCode()).isEqualTo(400); assertThat(tokenExists(verification)).isTrue(); assertThat(tokenExists(raw)).isTrue();
		clearInvocations(hasher); var result = reset(raw, NEW); assertThat(result.statusCode()).isEqualTo(200); noStore(result);
		verify(hasher).hash(NEW);
		assertThat(send("GET", "/api/auth/me", "", first).statusCode()).isEqualTo(401);
		assertThat(send("GET", "/api/auth/me", "", second).statusCode()).isEqualTo(401);
		assertThat(send("GET", "/api/auth/me", "", other).statusCode()).isEqualTo(200);
		assertThat(mongo.getCollection("auth_tokens").find(new Document("userId", "reset-user")).into(new ArrayList<>())).isEmpty();
		assertThat(tokenExists(otherToken)).isTrue();
		var stored = users.findById("reset-user").orElseThrow(); assertThat(stored.isEmailVerified()).isTrue();
		assertThat(encoder.matches(NEW, stored.getPasswordHash())).isTrue(); assertThat(encoder.matches(OLD, stored.getPasswordHash())).isFalse();
		assertThat(stored.getEmailSendsInWindow()).isEqualTo(9); assertThat(stored.getWindowStartedAt()).isEqualTo(NOW);
		assertThat(send("POST", "/api/auth/login", loginBody(EMAIL, OLD), headers(csrf)).statusCode()).isEqualTo(401);
		var authenticated = login(EMAIL, NEW); assertThat(send("GET", "/api/auth/me", "", authenticated).statusCode()).isEqualTo(200);
	}
	@Test void usedExpiredAndInventedTokensReturnTheSameGeneric400(CapturedOutput output) throws Exception {
		forgot(EMAIL); String usedRaw = token(); assertThat(reset(usedRaw, NEW).statusCode()).isEqualTo(200);
		var used = reset(usedRaw, NEW); var invented = reset("I".repeat(43), NEW);
		String expiredRaw = "E".repeat(43); tokens.replace(new AuthToken("reset-user", AuthTokenPurpose.PASSWORD_RESET, AuthToken.hash(expiredRaw), NOW));
		var expired = reset(expiredRaw, NEW);
		for (var result : List.of(used, expired, invented)) { assertThat(result.statusCode()).isEqualTo(400); noStore(result); assertThat(result.body()).isEqualTo(used.body()); }
		assertThat(output.getAll()).doesNotContain(usedRaw, expiredRaw, "I".repeat(43));
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void tokenExpiryIsCheckedBeforeExactlyAndAfterTheInstant(int offset) throws Exception {
		forgot(EMAIL); String raw = token(); when(clock.instant()).thenReturn(NOW.plusSeconds(3600).plusMillis(offset));
		assertThat(reset(raw, NEW).statusCode()).isEqualTo(offset < 0 ? 200 : 400);
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void resetMailCooldownAcceptsTheExactBoundary(int offset) throws Exception {
		forgot(EMAIL); when(clock.instant()).thenReturn(NOW.plusSeconds(300).plusMillis(offset)); forgot(EMAIL);
		assertThat(mail.messages()).hasSize(offset < 0 ? 1 : 2);
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void resetMailDailyWindowResetsAtTheExactBoundary(int offset) throws Exception {
		forgot(EMAIL); mongo.updateFirst(Query.query(Criteria.where("id").is("reset-user")), new Update().set("authEmailSends", 5), UserDocument.class);
		when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(24)).plusMillis(offset)); forgot(EMAIL);
		assertThat(mail.messages()).hasSize(offset < 0 ? 1 : 2);
		assertThat(users.findById("reset-user").orElseThrow().getAuthEmailSends()).isEqualTo(offset < 0 ? 5 : 1);
	}
	@Test void verifiedAccountsCanReserveResetMailAndShareAuthButNotInvitationQuota() throws Exception {
		mongo.updateFirst(Query.query(Criteria.where("id").is("reset-user")), new Update().set("emailVerified", true).set("emailSendsInWindow", 8).set("windowStartedAt", NOW), UserDocument.class);
		for (int i = 0; i < 6; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(i * 300L)); forgot(EMAIL); }
		assertThat(mail.messages()).hasSize(5); var stored = users.findById("reset-user").orElseThrow(); assertThat(stored.getAuthEmailSends()).isEqualTo(5);
		assertThat(stored.getEmailSendsInWindow()).isEqualTo(8); assertThat(stored.getWindowStartedAt()).isEqualTo(NOW);
	}
	@Test void concurrentRequestsReserveOnlyOneResetEmail() throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> { barrier.await(); return forgot(EMAIL); }); var b = executor.submit(() -> { barrier.await(); return forgot(EMAIL); });
			assertThat(List.of(a.get(30, TimeUnit.SECONDS).statusCode(), b.get(30, TimeUnit.SECONDS).statusCode())).containsExactly(202, 202);
		}
		assertThat(mail.messages()).hasSize(1); assertThat(users.findById("reset-user").orElseThrow().getAuthEmailSends()).isEqualTo(1);
	}
	@Test void concurrentResetsConsumeOnlyOnce() throws Exception {
		forgot(EMAIL); String raw = token(); var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> { barrier.await(); return reset(raw, NEW); }); var b = executor.submit(() -> { barrier.await(); return reset(raw, NEW); });
			assertThat(List.of(a.get(30, TimeUnit.SECONDS).statusCode(), b.get(30, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 400);
		}
	}
	@Test void smtpFailureKeepsTheReservationAndTheUniformResponse(CapturedOutput output) throws Exception {
		mail.enqueueResult(EmailDeliveryResult.failure(EmailSendFailureCode.PROVIDER_UNAVAILABLE));
		var response = forgot(EMAIL); assertThat(response.statusCode()).isEqualTo(202); noStore(response);
		assertThat(users.findById("reset-user").orElseThrow().getAuthEmailSends()).isEqualTo(1); assertThat(mail.attempts()).hasSize(1);
		forgot(EMAIL); assertThat(mail.attempts()).hasSize(1);
		assertThat(output.getAll()).contains("ERROR", "AUTH_MAIL_SEND_FAILED", "correlationId=").doesNotContain(EMAIL);
	}
	@Test void recoveryEndpointsStillRequireCsrf() throws Exception {
		for (String path : List.of("/api/auth/forgot-password", "/api/auth/reset-password")) {
			assertThat(send("POST", path, "{}", Map.of("Content-Type", "application/json")).statusCode()).isEqualTo(403);
		}
	}
	@Test void malformedEmailReturns422AndDoesNotReserveOrSend() throws Exception {
		var response = forgot("invalid"); assertThat(response.statusCode()).isEqualTo(422); noStore(response);
		assertThat(mail.attempts()).isEmpty(); assertThat(users.findById("reset-user").orElseThrow().getAuthEmailSends()).isZero();
	}
	@Test void openApiDocumentsBothPublicRecoveryRoutes() throws Exception {
		var response = send("GET", "/v3/api-docs", "", Map.of()); assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(response.body()).read("$.paths", Map.class)).containsKeys("/api/auth/forgot-password", "/api/auth/reset-password");
	}
	private void assertSanitizedFailure(HttpResponse<String> response, String raw, CapturedOutput output) {
		assertThat(response.statusCode()).isEqualTo(500); noStore(response);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/errors");
		assertThat(response.body()).doesNotContain(raw, EMAIL); assertThat(output.getAll()).doesNotContain(raw, EMAIL);
	}
	private HttpResponse<String> forgot(String email) throws Exception { return send("POST", "/api/auth/forgot-password", "{\"email\":\"" + email + "\"}", headers(csrf)); }
	private HttpResponse<String> reset(String raw, String password) throws Exception { return send("POST", "/api/auth/reset-password", resetBody(raw, password), headers(csrf)); }
	private static String resetBody(String raw, String password) { return "{\"token\":\"" + raw + "\",\"newPassword\":\"" + password + "\"}"; }
	private static String loginBody(String email, String password) { return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"; }
	private Map<String, String> login(String email, String password) throws Exception {
		var response = send("POST", "/api/auth/login", loginBody(email, password), headers(csrf)); assertThat(response.statusCode()).isEqualTo(200);
		return Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(response, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}
	private static Map<String, String> headers(String value) { return Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + value, "X-XSRF-TOKEN", value); }
	private String token() { return mail.messages().getLast().body().lines().filter(line -> line.startsWith("http")).map(URI::create).findFirst().orElseThrow().getFragment().substring(6); }
	private boolean tokenExists(String raw) { return mongo.getCollection("auth_tokens").find(new Document("hash", AuthToken.hash(raw))).first() != null; }
	private static Map<String, List<String>> withoutDate(HttpResponse<?> response) { var headers = new TreeMap<>(response.headers().map()); headers.remove("date"); return headers; }
	private static void noStore(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store"); }
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static String cookie(HttpResponse<?> response, String name) { return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream()).filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getValue(); }
}
