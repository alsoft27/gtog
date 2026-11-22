package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.JsonPath;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.*;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class GuestBatchControllerIntegrationTest {
	private static final String PATH = "/api/events/batch-event/guests", BATCH = PATH + "/batch";
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final JsonMapper json = JsonMapper.builder().build();
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	private String csrf;
	GuestBatchControllerIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, EventRepositoryPort events, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.passwords = passwords;
	}
	@BeforeEach void setUp() throws Exception {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), UserDocument.class);
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "batch-host@example.com", passwords.encode("password12"), "UTC"));
		events.save(event("batch-event", EventStatus.DRAFT, null));
		csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"batch-host@example.com\",\"password\":\"password12\"}", anonymous());
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}
	private List<GuestRequest> three() { return List.of(new GuestRequest("Ana", " ANA@EXAMPLE.COM ", null), new GuestRequest("Luis", null, "+34 612 345 678"),
			new GuestRequest("Both", " BOTH@EXAMPLE.COM ", "+34 698 765 432")); }
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "PUBLISHED" })
	void createsThreeGuestsInOrderWithNormalizedContactsDistinctTokensAndExactHeaders(EventStatus status) throws Exception {
		state(status); long version = events.findById("batch-event").orElseThrow().getVersion();
		var result = post(body(three())); assertThat(result.statusCode()).isEqualTo(201); noStore(result);
		assertThat(result.headers().firstValue("Location").orElseThrow()).isEqualTo(PATH);
		var parsed = JsonPath.parse(result.body());
		assertThat(parsed.read("$[*].name", List.class)).containsExactly("Ana", "Luis", "Both");
		assertThat(parsed.read("$[0].email", String.class)).isEqualTo("ana@example.com"); assertThat(parsed.read("$[2].email", String.class)).isEqualTo("both@example.com");
		assertThat(parsed.read("$[1].phoneE164", String.class)).isEqualTo("+34612345678"); assertThat(parsed.read("$[2].phoneE164", String.class)).isEqualTo("+34698765432");
		assertThat(parsed.read("$[*].invitationStatus", List.class)).containsOnly("NOT_SENT");
		assertThat(parsed.read("$[*].invitationPath", List.class)).hasSize(3).doesNotHaveDuplicates();
		for (Object path : parsed.read("$[*].invitationPath", List.class)) { assertThat((String) path).matches("/r/[A-Za-z0-9_-]{43}"); }
		assertThat(result.body()).doesNotContain("\"token\"", "guestToken");
		assertThat(events.findById("batch-event").orElseThrow().getVersion()).isEqualTo(version + 1);
	}
	@Test void oneInvalidGuestAmongFiveLeavesTheEntireBsonUnchanged() throws Exception {
		var inputs = new ArrayList<GuestRequest>(); for (int i = 0; i < 5; i++) { inputs.add(new GuestRequest("Name " + i, "unique" + i + "@example.com", null)); }
		inputs.set(2, new GuestRequest("Invalid", "invalid-email", null)); Document before = document();
		assertProblem(post(body(inputs)), 422); assertThat(document()).isEqualTo(before);
	}
	@Test void reportsEveryErrorWithIndexWithoutEchoingPersonalDataOrLoggingIt(CapturedOutput output) throws Exception {
		var inputs = List.of(new GuestRequest("Sensitive One", "secret-invalid-email", "secret-invalid-phone"), new GuestRequest("Sensitive Two", null, null),
				new GuestRequest("", null, null), new GuestRequest("Valid Secret", "valid-private@example.com", "+34698765432"));
		var result = post(body(inputs)); assertProblem(result, 422);
		assertThat(errors(result)).containsExactly(error(0, "INVALID_EMAIL"), error(0, "INVALID_PHONE"), error(1, "NO_CONTACT"), error(2, "BLANK_NAME"), error(2, "NO_CONTACT"));
		assertThat(result.body()).doesNotContain("Sensitive One", "Sensitive Two", "secret-invalid-email", "secret-invalid-phone", "Valid Secret", "valid-private@example.com", "+34698765432");
		assertThat(output.getAll()).doesNotContain("Sensitive One", "Sensitive Two", "secret-invalid-email", "secret-invalid-phone", "Valid Secret", "valid-private@example.com", "+34698765432");
	}
	@Test void equivalentEmailsAndPhoneFormatsMarkBothIndicesAndDoNotWrite() throws Exception {
		var before = document(); var result = post(body(List.of(new GuestRequest("A", " DUP@EXAMPLE.COM ", "+34 612 345 678"), new GuestRequest("B", "dup@example.com", "+34612345678"))));
		assertProblem(result, 422); assertThat(errors(result)).containsExactly(error(0, "DUPLICATE_IN_BATCH"), error(1, "DUPLICATE_IN_BATCH")); assertThat(document()).isEqualTo(before);
	}
	@Test void duplicatesAgainstTheEventReportEveryIndexAndDoNotWrite(CapturedOutput output) throws Exception {
		var existing = Guest.create("Existing", "existing@example.com", "+34612345678", token(9), phone -> phone);
		mongo.updateFirst(query(), new Update().set("guests", List.of(new GuestMapper().toDocument(existing))), EventDocument.class);
		var before = document();
		var result = post(body(List.of(new GuestRequest("One", " EXISTING@EXAMPLE.COM ", null), new GuestRequest("Two", null, "+34 612 345 678"))));
		assertProblem(result, 409); assertThat(errors(result)).containsExactly(error(0, "DUPLICATE_IN_EVENT"), error(1, "DUPLICATE_IN_EVENT")); assertThat(document()).isEqualTo(before);
		assertThat(result.body()).doesNotContain(existing.getToken(), "existing@example.com", "+34612345678");
		assertThat(output.getAll()).doesNotContain(existing.getToken(), "existing@example.com", "+34612345678");
	}
	@Test void elementValidationPrecedesDuplicatesAgainstTheEvent() throws Exception {
		seed(1); var before = document();
		var result = post(body(List.of(new GuestRequest("", "invalid-row@example.com", null), new GuestRequest("Duplicate", "guest1@example.com", null))));
		assertProblem(result, 422); assertThat(errors(result)).containsExactly(error(0, "BLANK_NAME"));
		assertThat(errors(result)).noneMatch(error -> "DUPLICATE_IN_EVENT".equals(error.get("code"))); assertThat(document()).isEqualTo(before);
	}
	@Test void rejectsThreeAt498GuestsWithCapacityCodeAndNoElementErrors() throws Exception {
		seed(498); var before = document(); var result = post(body(three())); assertProblem(result, 409);
		assertThat(JsonPath.parse(result.body()).read("$.code", String.class)).isEqualTo("CAPACITY_EXCEEDED");
		assertThat(JsonPath.parse(result.body()).read("$", Map.class)).doesNotContainKey("errors"); assertThat(document()).isEqualTo(before);
	}
	@Test void acceptsThreeAt497Guests() throws Exception {
		seed(497); var result = post(body(three())); assertThat(result.statusCode()).isEqualTo(201); noStore(result);
		assertThat(events.findById("batch-event").orElseThrow().getGuests()).hasSize(500);
	}
	@Test void theIndividualEndpointAlsoRejectsAt500SeededDirectlyInMongo() throws Exception {
		seed(500); var before = document();
		var result = send("POST", PATH, json.writeValueAsString(new GuestRequest("Additional", "additional@example.com", null)), auth);
		assertThat(result.statusCode()).isEqualTo(409); assertThat(JsonPath.parse(result.body()).read("$.code", String.class)).isEqualTo("CAPACITY_EXCEEDED");
		assertThat(document()).isEqualTo(before);
	}
	@Test void rejects101GuestsBeforeValidatingTheirRepeatedContacts() throws Exception {
		var before = document(); var result = post(body(Collections.nCopies(101, new GuestRequest("Same", "same@example.com", null))));
		assertProblem(result, 422); assertThat(errors(result)).isEmpty(); assertThat(document()).isEqualTo(before);
	}
	@ParameterizedTest @ValueSource(strings = { "{\"guests\":[]}", "{\"guests\":null}", "{}" })
	void emptyNullAndMissingListsAre422(String body) throws Exception { assertProblem(post(body), 422); }
	@ParameterizedTest @ValueSource(strings = { "", "{\"guests\":[" })
	void absentAndUnreadableBodiesAre400(String body) throws Exception {
		var result = post(body); assertThat(result.statusCode()).isEqualTo(400); noStore(result);
	}
	@Test void unreadableBodyIs400EvenForAForeignOrClosedEvent() throws Exception {
		state(EventStatus.CANCELLED); assertThat(post("{").statusCode()).isEqualTo(400);
		mongo.updateFirst(query(), new Update().set("hostId", "other-host"), EventDocument.class);
		assertThat(post("{").statusCode()).isEqualTo(400); assertProblem(post("{\"guests\":[]}"), 404);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "CANCELLED", "FINISHED" })
	void closedStatesPrecedeListValidation(EventStatus status) throws Exception { state(status); assertProblem(post("{\"guests\":[]}"), 409); }
	@Test void unknownAndForeignEventsReturnTheSameSanitized404() throws Exception {
		var unknown = send("POST", "/api/events/unknown-private/guests/batch", body(three()), auth); assertProblem(unknown, 404);
		mongo.updateFirst(query(), new Update().set("hostId", "other-host"), EventDocument.class); var foreign = post(body(three())); assertProblem(foreign, 404);
		assertThat(foreign.body()).isEqualTo(unknown.body()).doesNotContain("other-host", "unknown-private", "batch-event");
	}
	@Test void authenticationAndCsrfAreStillRequired() throws Exception {
		assertThat(send("POST", BATCH, body(three()), anonymous()).statusCode()).isEqualTo(401);
		assertThat(send("POST", BATCH, body(three()), Map.of("Content-Type", "application/json", "Cookie", auth.get("Cookie"))).statusCode()).isEqualTo(403);
	}
	@Test void unverifiedHostsCanAddToAnExistingEvent() throws Exception {
		mongo.updateFirst(Query.query(Criteria.where("id").is("host-1")), new Update().set("emailVerified", false), UserDocument.class);
		assertThat(post(body(three())).statusCode()).isEqualTo(201);
	}
	@Test void individualAndBatchOfOneProduceTheSameGuestDocumentExceptIdentityAndToken() throws Exception {
		var input = new GuestRequest("Same", " SAME@EXAMPLE.COM ", "+34 612 345 678");
		var individual = send("POST", PATH, json.writeValueAsString(input), auth); assertThat(individual.statusCode()).isEqualTo(201);
		assertThat(individual.headers().firstValue("Location").orElseThrow()).startsWith(PATH + "/");
		var first = new Document(document().getList("guests", Document.class).getFirst());
		mongo.updateFirst(query(), new Update().set("guests", List.of()), EventDocument.class);
		var batch = post(body(List.of(input))); assertThat(batch.statusCode()).isEqualTo(201);
		var second = new Document(document().getList("guests", Document.class).getFirst());
		first.remove("_id"); first.remove("token"); second.remove("_id"); second.remove("token"); assertThat(second).isEqualTo(first);
		assertThat(JsonPath.parse(batch.body()).read("$[0]", Map.class).keySet()).isEqualTo(JsonPath.parse(individual.body()).read("$", Map.class).keySet());
	}
	@Test void successfulRequestsNeverLogContactsNamesOrGeneratedTokens(CapturedOutput output) throws Exception {
		var result = post(body(List.of(new GuestRequest("Sensitive Full Name", "private-log@example.com", "+34612345678")))); assertThat(result.statusCode()).isEqualTo(201);
		String path = JsonPath.parse(result.body()).read("$[0].invitationPath", String.class);
		assertThat(output.getAll()).doesNotContain("Sensitive Full Name", "private-log@example.com", "+34612345678", path, path.substring(3));
	}
	@Test void openApiDocumentsBatchWithoutRemovingIndividualRegistration() throws Exception {
		var response = send("GET", "/v3/api-docs", "", Map.of()); assertThat(response.statusCode()).isEqualTo(200);
		var paths = JsonPath.parse(response.body()).read("$.paths", Map.class);
		assertThat(paths).containsKeys("/api/events/{id}/guests/batch", "/api/events/{id}/guests");
	}
	private void seed(int count) { mongo.updateFirst(query(), new Update().set("guests", java.util.stream.IntStream.rangeClosed(1, count)
			.mapToObj(com.gtog.event.GuestTestFixtures::guest).map(new GuestMapper()::toDocument).toList()), EventDocument.class); }
	private void state(EventStatus status) { mongo.updateFirst(query(), new Update().set("status", status.name()), EventDocument.class); }
	private Query query() { return Query.query(Criteria.where("id").is("batch-event")); }
	private Document document() { return mongo.getCollection("events").find(new Document("_id", "batch-event")).first(); }
	private String body(List<GuestRequest> inputs) { return json.writeValueAsString(new GuestBatchRequest(inputs)); }
	private HttpResponse<String> post(String body) throws Exception { return send("POST", BATCH, body, auth); }
	private Map<String, String> anonymous() { return Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf); }
	private static Map<String, Object> error(int index, String code) { return Map.of("index", index, "code", code); }
	private static List<Map<String, Object>> errors(HttpResponse<String> response) { return JsonPath.parse(response.body()).read("$.errors"); }
	private static void noStore(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store"); }
	private static void assertProblem(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).isEqualTo(status); noStore(response);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/events/guests/batch");
	}
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private String cookie(HttpResponse<?> response, String name) { return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
			.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue(); }
}
