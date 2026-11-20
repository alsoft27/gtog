package com.gtog.user.infrastructure.in.web;

import java.net.URI;
import java.net.HttpCookie;
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
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gtog.user.infrastructure.config.AuthProperties;
import com.gtog.user.infrastructure.out.persistence.*;
import com.gtog.user.domain.model.*;
import com.gtog.user.domain.port.out.AuthTokenRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
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
class EmailVerificationIntegrationTest {
	private static final String EMAIL = "verification-private@example.com";
	private static final String EVENT = """
			{"title":"Evento","startsAt":"2027-01-01T18:00:00","endsAt":"2027-01-01T20:00:00","timeZone":"Europe/Madrid","modality":"IN_PERSON",
			"venue":{"placeName":"Sala","address":"Madrid","latitude":40.4,"longitude":-3.7,"placeId":"place"}}
			""";
	private final int port;
	private final MongoTemplate mongo;
	private final UserMongoRepository users;
	private final CapturingEmailSenderAdapter mail;
	private final AuthTokenRepositoryPort tokens;
	private final AuthProperties properties;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoBean private Clock clock;
	private String csrf;
	private String userId;
	EmailVerificationIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, UserMongoRepository users,
			CapturingEmailSenderAdapter mail, AuthTokenRepositoryPort tokens, AuthProperties properties) {
		this.port = port; this.mongo = mongo; this.users = users; this.mail = mail; this.tokens = tokens; this.properties = properties;
	}
	@BeforeEach void setUp() throws Exception {
		mongo.remove(new Query(), UserDocument.class); mongo.remove(new Query(), AuthTokenDocument.class); mongo.remove(new Query(), EventDocument.class);
		mail.reset(); when(clock.instant()).thenReturn(NOW);
		csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
	}
	@Test void verificationEnablesCreationAndRefreshesMeInTheSameSessionWithoutTouchingInvitationQuota(CapturedOutput output) throws Exception {
		register(); String raw = token(); var auth = login();
		assertThat(properties.isAutoVerifyEmail()).isFalse();
		var denied = send("POST", "/api/events", EVENT, auth); assertThat(denied.statusCode()).isEqualTo(403);
		assertThat(read(denied, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED"); assertThat(read(denied, "$.instance")).isEqualTo("/api/events");
		assertThat(read(send("GET", "/api/auth/me", "", auth), "$.emailVerified")).isEqualTo(false);
		mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), new Update().set("emailSendsInWindow", 7).set("windowStartedAt", NOW), UserDocument.class);
		var quotaBefore = users.findById(userId).orElseThrow();
		when(clock.instant()).thenReturn(NOW.plusSeconds(300));
		assertThat(send("POST", "/api/auth/resend-verification", "", auth).statusCode()).isEqualTo(204);
		assertThat(mail.messages()).hasSize(2); raw = token();
		var quotaAfterResend = users.findById(userId).orElseThrow();
		assertThat(quotaAfterResend.getEmailSendsInWindow()).isEqualTo(quotaBefore.getEmailSendsInWindow());
		assertThat(quotaAfterResend.getWindowStartedAt()).isEqualTo(quotaBefore.getWindowStartedAt());
		var result = verify(raw, auth); assertThat(result.statusCode()).isEqualTo(200); assertThat(result.body()).isEmpty(); noStore(result);
		assertThat(send("POST", "/api/events", EVENT, auth).statusCode()).isEqualTo(201);
		var me = send("GET", "/api/auth/me", "", auth); assertThat(me.statusCode()).isEqualTo(200); noStore(me);
		assertThat(read(me, "$.emailVerified")).isEqualTo(true);
		var stored = users.findById(userId).orElseThrow();
		assertThat(stored.getEmailSendsInWindow()).isEqualTo(quotaBefore.getEmailSendsInWindow());
		assertThat(stored.getWindowStartedAt()).isEqualTo(quotaBefore.getWindowStartedAt());
		assertThat(stored.getAuthEmailSends()).isEqualTo(2); assertThat(output.getAll()).doesNotContain(raw);
	}
	@Test void capturedVerificationMailIsFixedAndOnlyTheHashIsPersisted() throws Exception {
		register(); String raw = token(); var message = mail.messages().getFirst(); URI link = URI.create(message.body().lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow());
		assertThat(message.recipient()).isEqualTo(EMAIL); assertThat(link.getQuery()).isNull(); assertThat(link.getFragment()).isEqualTo("token=" + raw);
		assertThat(link.getPath()).isEqualTo("/verify-email");
		assertThat(message.body()).contains("Si no lo has pedido, ignóralo.").doesNotContain(EMAIL, "Sensitive Private Name", "Europe/Madrid", "password12");
		var docs = mongo.getCollection("auth_tokens").find().into(new ArrayList<>()); assertThat(docs).hasSize(1);
		assertThat(docs.getFirst().toJson()).doesNotContain(raw, EMAIL); assertThat(docs.getFirst().getString("hash")).isEqualTo(AuthToken.hash(raw));
		assertThat(docs.getFirst().getDate("expiresAt").toInstant()).isEqualTo(NOW.plus(Duration.ofHours(24)));
		var indexes = mongo.getCollection("auth_tokens").listIndexes().into(new ArrayList<>());
		assertThat(indexes).anySatisfy(index -> { assertThat(index.getString("name")).isEqualTo("auth_token_hash"); assertThat(index.getBoolean("unique")).isTrue(); });
		assertThat(indexes).anySatisfy(index -> { assertThat(index.getString("name")).isEqualTo("auth_token_per_purpose"); assertThat(index.getBoolean("unique")).isTrue(); });
		assertThat(indexes).anySatisfy(index -> { assertThat(index.getString("name")).isEqualTo("auth_token_expiry"); assertThat(((Number) index.get("expireAfterSeconds")).longValue()).isZero(); });
	}
	@Test void usedExpiredAndInventedTokensReturnTheSameGenericProblemAndNeverLogTheToken(CapturedOutput output) throws Exception {
		register(); String raw = token(); assertThat(verify(raw, anonymous()).statusCode()).isEqualTo(200);
		var used = verify(raw, anonymous()); var invented = verify("B".repeat(43), anonymous());
		tokens.replace(new AuthToken(userId, AuthTokenPurpose.EMAIL_VERIFICATION, AuthToken.hash("C".repeat(43)), NOW));
		var expired = verify("C".repeat(43), anonymous());
		for (var result : List.of(used, invented, expired)) { assertThat(result.statusCode()).isEqualTo(400); noStore(result); assertThat(read(result, "$.instance")).isEqualTo("/api/auth"); }
		assertThat(used.body()).isEqualTo(invented.body()).isEqualTo(expired.body());
		assertThat(output.getAll()).doesNotContain(raw, "B".repeat(43), "C".repeat(43));
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void expiryIsCheckedBeforeExactlyAndAfterTheInstantEvenWhenMongoRetainsTheDocument(int offset) throws Exception {
		register(); String raw = token(); var expiry = NOW.plus(Duration.ofHours(24)); when(clock.instant()).thenReturn(expiry.plusSeconds(offset));
		assertThat(mongo.count(new Query(), AuthTokenDocument.class)).isEqualTo(1);
		assertThat(verify(raw, anonymous()).statusCode()).isEqualTo(offset < 0 ? 200 : 400);
	}
	@Test void concurrentVerificationConsumesExactlyOnce() throws Exception {
		register(); String raw = token(); var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { barrier.await(); return verify(raw, anonymous()); });
			var second = executor.submit(() -> { barrier.await(); return verify(raw, anonymous()); });
			assertThat(List.of(first.get(30, TimeUnit.SECONDS).statusCode(), second.get(30, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 400);
		}
		assertThat(mongo.count(new Query(), AuthTokenDocument.class)).isZero(); assertThat(users.findById(userId).orElseThrow().isEmailVerified()).isTrue();
	}
	@Test void theFirstAnonymousMeBootstrapsCsrfForPublicVerification() throws Exception {
		var me = send("GET", "/api/auth/me", "", Map.of()); assertThat(me.statusCode()).isEqualTo(401); noStore(me);
		String value = cookie(me, "XSRF-TOKEN");
		var response = verify("A".repeat(43), Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + value, "X-XSRF-TOKEN", value));
		assertThat(response.statusCode()).isEqualTo(400); noStore(response);
	}
	@Test void resendAndMeRequireAuthenticationAndVerificationStillRequiresCsrf() throws Exception {
		assertThat(send("POST", "/api/auth/resend-verification", "", anonymous()).statusCode()).isEqualTo(401);
		assertThat(send("GET", "/api/auth/me", "", Map.of()).statusCode()).isEqualTo(401);
		assertThat(verify("A".repeat(43), Map.of("Content-Type", "application/json")).statusCode()).isEqualTo(403);
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void resendCooldownAllowsTheExactBoundary(int offset) throws Exception {
		register(); var auth = login(); when(clock.instant()).thenReturn(NOW.plusSeconds(300).plusMillis(offset));
		var result = send("POST", "/api/auth/resend-verification", "", auth); assertThat(result.statusCode()).isEqualTo(204); noStore(result);
		assertThat(mail.messages()).hasSize(offset < 0 ? 1 : 2);
	}
	@Test void theFifthEmissionIsTheLastOneInTheWindow() throws Exception {
		register(); var auth = login();
		for (int i = 1; i <= 5; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(i * 300L)); assertThat(send("POST", "/api/auth/resend-verification", "", auth).statusCode()).isEqualTo(204); }
		assertThat(mail.messages()).hasSize(5); assertThat(users.findById(userId).orElseThrow().getAuthEmailSends()).isEqualTo(5);
	}
	@Test void concurrentResendsReserveOnceAndBothReturnTheUniformResponse() throws Exception {
		register(); var auth = login(); when(clock.instant()).thenReturn(NOW.plusSeconds(300)); var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { barrier.await(); return send("POST", "/api/auth/resend-verification", "", auth); });
			var second = executor.submit(() -> { barrier.await(); return send("POST", "/api/auth/resend-verification", "", auth); });
			assertThat(List.of(first.get(30, TimeUnit.SECONDS).statusCode(), second.get(30, TimeUnit.SECONDS).statusCode())).containsExactly(204, 204);
		}
		assertThat(mail.messages()).hasSize(2); assertThat(users.findById(userId).orElseThrow().getAuthEmailSends()).isEqualTo(2);
		assertThat(mongo.count(new Query(), AuthTokenDocument.class)).isEqualTo(1);
	}
	@ParameterizedTest @ValueSource(ints = { -1, 0, 1 })
	void theDailyWindowResetsExactlyAtTwentyFourHours(int offset) throws Exception {
		register(); var auth = login();
		mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), new Update().set("authEmailSends", 5), UserDocument.class);
		when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(24)).plusMillis(offset));
		assertThat(send("POST", "/api/auth/resend-verification", "", auth).statusCode()).isEqualTo(204);
		assertThat(mail.messages()).hasSize(offset < 0 ? 1 : 2);
		assertThat(users.findById(userId).orElseThrow().getAuthEmailSends()).isEqualTo(offset < 0 ? 5 : 1);
	}
	@Test void aVerifiedAccountResendDoesNotWriteOrSend() throws Exception {
		register(); var auth = login(); assertThat(verify(token(), anonymous()).statusCode()).isEqualTo(200);
		String before = mongo.getCollection("users").find(new Document("_id", userId)).first().toJson();
		var result = send("POST", "/api/auth/resend-verification", "", auth); assertThat(result.statusCode()).isEqualTo(204); noStore(result);
		assertThat(mail.messages()).hasSize(1); assertThat(mongo.getCollection("users").find(new Document("_id", userId)).first().toJson()).isEqualTo(before);
	}
	@Test void aReplacementRevokesThePreviousTokenWithoutCreatingAnotherDocument() throws Exception {
		register(); String old = token(); var auth = login(); when(clock.instant()).thenReturn(NOW.plusSeconds(300));
		var before = mongo.getCollection("auth_tokens").find(new Document("userId", userId).append("purpose", "EMAIL_VERIFICATION")).first();
		assertThat(before).isNotNull();
		assertThat(before.getString("hash")).isEqualTo(AuthToken.hash(old));
		assertThat(before.toJson()).doesNotContain(old);
		assertThat(send("POST", "/api/auth/resend-verification", "", auth).statusCode()).isEqualTo(204);
		assertThat(mongo.count(new Query(), AuthTokenDocument.class)).isEqualTo(1);
		String replacement = token(); assertThat(replacement).isNotEqualTo(old);
		var after = mongo.getCollection("auth_tokens").find(new Document("userId", userId).append("purpose", "EMAIL_VERIFICATION")).first();
		assertThat(after).isNotNull();
		assertThat(after.get("_id")).isEqualTo(before.get("_id"));
		assertThat(after.getString("hash")).isEqualTo(AuthToken.hash(replacement)).isNotEqualTo(before.getString("hash"));
		assertThat(after.toJson()).doesNotContain(old, replacement);
		assertThat(verify(old, anonymous()).statusCode()).isEqualTo(400);
		assertThat(verify(replacement, anonymous()).statusCode()).isEqualTo(200);
	}
	@Test void aPasswordResetTokenCannotVerifyEmail() throws Exception {
		register(); String raw = "D".repeat(43); tokens.replace(new AuthToken(userId, AuthTokenPurpose.PASSWORD_RESET, AuthToken.hash(raw), NOW.plusSeconds(3600)));
		assertThat(verify(raw, anonymous()).statusCode()).isEqualTo(400);
		assertThat(mongo.exists(Query.query(Criteria.where("hash").is(AuthToken.hash(raw))), AuthTokenDocument.class)).isTrue();
	}
	@Test void aProviderFailureKeepsTheAuthReservationAndDoesNotAlterRegistration(CapturedOutput output) throws Exception {
		mail.enqueueResult(EmailDeliveryResult.failure(EmailSendFailureCode.EMAIL_NOT_CONFIGURED)); register();
		assertThat(mail.messages()).isEmpty(); assertThat(mail.attempts()).hasSize(1);
		var user = users.findById(userId).orElseThrow(); assertThat(user.getAuthEmailSends()).isEqualTo(1); assertThat(user.getEmailSendsInWindow()).isZero();
		assertThat(send("POST", "/api/auth/resend-verification", "", login()).statusCode()).isEqualTo(204); assertThat(mail.attempts()).hasSize(1);
		assertThat(output.getAll()).contains("ERROR", "AUTH_MAIL_SEND_FAILED", "correlationId=").doesNotContain(EMAIL);
	}
	@Test void aLegacyUserWithoutVerificationOrAuthCountersIsUnverifiedAndCanReserve() throws Exception {
		register(); var auth = login();
		mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), new Update().unset("emailVerified").unset("authEmailSends").unset("authEmailWindowStartedAt").unset("lastAuthEmailAt"), UserDocument.class);
		assertThat(read(send("GET", "/api/auth/me", "", auth), "$.emailVerified")).isEqualTo(false);
		assertThat(send("POST", "/api/events", EVENT, auth).statusCode()).isEqualTo(403);
		assertThat(send("POST", "/api/auth/resend-verification", "", auth).statusCode()).isEqualTo(204);
		assertThat(users.findById(userId).orElseThrow().getAuthEmailSends()).isEqualTo(1);
	}
	@Test void openApiDocumentsTheNewEndpointsAndEmailVerified() throws Exception {
		var result = send("GET", "/v3/api-docs", "", Map.of()); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(result.body()).read("$.paths", Map.class)).containsKeys("/api/auth/me", "/api/auth/verify-email", "/api/auth/resend-verification");
		assertThat(JsonPath.parse(result.body()).read("$.components.schemas.AuthUserResponse.properties", Map.class)).containsKey("emailVerified");
	}

	private void register() throws Exception {
		var result = send("POST", "/api/auth/register", "{\"name\":\"Sensitive Private Name\",\"email\":\"" + EMAIL + "\",\"rawPassword\":\"password12\",\"timeZone\":\"Europe/Madrid\"}", anonymous());
		assertThat(result.statusCode()).isEqualTo(201); assertThat(read(result, "$.emailVerified")).isEqualTo(false); userId = (String) read(result, "$.id");
	}
	private Map<String, String> login() throws Exception {
		var response = send("POST", "/api/auth/login", "{\"email\":\"" + EMAIL + "\",\"password\":\"password12\"}", anonymous()); assertThat(response.statusCode()).isEqualTo(200);
		var headers = new HashMap<>(anonymous()); headers.put("Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(response, "JSESSIONID")); return headers;
	}
	private Map<String, String> anonymous() { return Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf); }
	private HttpResponse<String> verify(String raw, Map<String, String> headers) throws Exception { return send("POST", "/api/auth/verify-email", "{\"token\":\"" + raw + "\"}", headers); }
	private String token() { return mail.messages().getLast().body().lines().filter(line -> line.startsWith("http")).map(URI::create).findFirst().orElseThrow().getFragment().substring("token=".length()); }
	private void noStore(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store"); }
	private static Object read(HttpResponse<String> response, String path) { return JsonPath.parse(response.body()).read(path); }
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(builder::header);
		return client.send(builder.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream()).filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
