package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import java.util.List;
import com.gtog.event.domain.model.DatePollSchedule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gtog.UserTestFixtures;
import com.gtog.event.domain.model.EventType;
import com.gtog.event.domain.model.FixedDateSchedule;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventDocument;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.auth.auto-verify-email=false", "app.events.max-candidate-dates=3"})
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DatePollDraftIntegrationTest {
	private static final String REQUEST = """
			{"title":"Cena","eventType":"DATE_POLL","timeZone":"Europe/Madrid","modality":"IN_PERSON",
			"responseDeadline":"2027-05-09T18:00:00","publicInvitationEnabled":true,
			"datePoll":{"candidateDates":[{"date":"2027-05-10","startTime":"20:00:00"},{"date":"2027-05-11"},{"date":"2027-05-12","startTime":"21:00:00"}]},
			"venue":{"placeName":"Sala","address":"Dirección","latitude":0,"longitude":0,"placeId":"p"}}
			""";
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	@MockitoBean private Clock clock;

	DatePollDraftIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.passwords = passwords;
	}

	@BeforeEach void setUp() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2027-03-01T10:00:00Z"));
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), UserDocument.class);
		mongo.getCollection("responses").deleteMany(new Document());
		mongo.insert(UserTestFixtures.verified("host", "Anfitrión", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"),
				"X-XSRF-TOKEN", csrf);
	}

	@AfterEach void close() { client.close(); }

	@Test void createsAPollWithMixedTimesAndNoFabricatedDatesAndPreservesTheBsonOnReads() throws Exception {
		var created = send("POST", "/api/events", REQUEST, auth);
		assertThat(created.statusCode()).isEqualTo(201);
		var json = JsonPath.parse(created.body()); String id = json.read("$.id");
		assertThat(created.headers().firstValue("Location")).contains("/api/events/" + id);
		assertThat(json.read("$.eventType", String.class)).isEqualTo("DATE_POLL");
		assertThat(json.read("$.status", String.class)).isEqualTo("DRAFT");
		assertThat(json.read("$.startsAt", String.class)).isNull(); assertThat(json.read("$.endsAt", String.class)).isNull();
		assertThat(json.read("$.responseOptions", List.class)).isEmpty();
		assertThat(json.read("$.datePoll.candidateDates[*].date", List.class)).containsExactly("2027-05-10", "2027-05-11", "2027-05-12");
		assertThat(json.read("$.datePoll.candidateDates[*].id", List.class)).hasSize(3).doesNotHaveDuplicates();
		assertThat(json.read("$.datePoll.candidateDates[1].startTime", String.class)).isNull();
		assertThat(java.time.LocalTime.parse(json.read("$.datePoll.candidateDates[0].startTime", String.class))).isEqualTo(java.time.LocalTime.of(20, 0));
		Document before = bson(id);
		assertThat(before).doesNotContainKeys("startsAt", "endsAt");
		assertThat(before.getString("eventType")).isEqualTo("DATE_POLL");
		assertThat(before.getLong("version")).isZero();
		List<Document> dates = before.get("datePoll", Document.class).getList("candidateDates", Document.class);
		assertThat(dates.get(0).getString("_id")).isEqualTo(json.read("$.datePoll.candidateDates[0].id", String.class));
		assertThat(dates.get(0).getString("date")).isEqualTo("2027-05-10");
		assertThat(dates.get(0).getString("startTime")).isEqualTo("20:00");
		assertThat(dates.get(1)).doesNotContainKeys("startTime", "endsAt", "endTime");
		assertThat(before.get("publicInvitation", Document.class)).doesNotContainKey("token");
		var reloaded = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		assertThat(reloaded.candidateDates()).extracting(com.gtog.event.domain.model.CandidateDate::id)
				.containsExactlyElementsOf(json.read("$.datePoll.candidateDates[*].id", List.class));
		var detail = send("GET", "/api/events/" + id, "", auth); assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(detail.body()).read("$.datePoll.candidateDates", List.class)).isEqualTo(json.read("$.datePoll.candidateDates", List.class));
		var listing = send("GET", "/api/events", "", auth); assertThat(listing.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(listing.body()).read("$[0].eventType", String.class)).isEqualTo("DATE_POLL");
		assertThat(JsonPath.parse(listing.body()).read("$[0].startsAt", String.class)).isNull();
		assertThat(bson(id)).isEqualTo(before);
	}

	@Test void generalEditingAndOtherDraftWritesPreserveTheCandidateIdentitiesAndMissingTimes() throws Exception {
		String id = create(); Document candidates = bson(id).get("datePoll", Document.class);
		var edited = send("PUT", "/api/events/" + id, """
				{"title":"Cena editada","eventType":"DATE_POLL","timeZone":"America/Bogota","modality":"IN_PERSON",
				"responseDeadline":"2027-05-08T18:00:00","allowComment":true}
				""", auth);
		assertThat(edited.statusCode()).isEqualTo(200);
		assertThat(bson(id).get("datePoll", Document.class)).isEqualTo(candidates);
		assertThat(bson(id).getString("title")).isEqualTo("Cena editada");
		assertThat(bson(id).getString("timeZone")).isEqualTo("America/Bogota");
		assertThat(bson(id).getLong("version")).isEqualTo(1L);
		assertThat(bson(id).get("publicInvitation", Document.class).getBoolean("enabled")).isTrue();
		assertThat(send("PUT", "/api/events/" + id + "/response-deadline", "{\"responseDeadline\":\"2027-05-07T10:00:00\"}", auth).statusCode()).isEqualTo(200);
		assertThat(send("PUT", "/api/events/" + id + "/venue", "{\"placeName\":\"Otra sala\",\"address\":\"Dirección\",\"latitude\":0,\"longitude\":0,\"placeId\":\"new\"}", auth).statusCode()).isEqualTo(200);
		assertThat(bson(id).get("datePoll", Document.class)).isEqualTo(candidates);
		assertThat(bson(id)).doesNotContainKeys("startsAt", "endsAt");
	}

	private static Stream<Arguments> invalidRequests() {
		return Stream.of(
				Arguments.of("missing deadline", REQUEST.replace("\"responseDeadline\":\"2027-05-09T18:00:00\",", ""), 422),
				Arguments.of("null deadline", JsonPath.parse(REQUEST).set("$.responseDeadline", null).jsonString(), 422),
				Arguments.of("missing block", JsonPath.parse(REQUEST).delete("$.datePoll").jsonString(), 422),
				Arguments.of("missing list", JsonPath.parse(REQUEST).set("$.datePoll", Map.of()).jsonString(), 422),
				Arguments.of("null list", JsonPath.parse(REQUEST).set("$.datePoll.candidateDates", null).jsonString(), 422),
				Arguments.of("empty list", JsonPath.parse(REQUEST).set("$.datePoll.candidateDates", List.of()).jsonString(), 422),
				Arguments.of("one candidate", JsonPath.parse(REQUEST).set("$.datePoll.candidateDates", List.of(Map.of("date", "2027-05-10"))).jsonString(), 422),
				Arguments.of("configured maximum", JsonPath.parse(REQUEST).set("$.datePoll.candidateDates", List.of(Map.of("date", "2027-05-10"), Map.of("date", "2027-05-11"), Map.of("date", "2027-05-12"), Map.of("date", "2027-05-13"))).jsonString(), 422),
				Arguments.of("missing date", JsonPath.parse(REQUEST).delete("$.datePoll.candidateDates[0].date").jsonString(), 422),
				Arguments.of("null candidate", JsonPath.parse(REQUEST).set("$.datePoll.candidateDates[0]", null).jsonString(), 422),
				Arguments.of("duplicate date", REQUEST.replace("2027-05-11", "2027-05-10"), 422),
				Arguments.of("invalid zone", REQUEST.replace("Europe/Madrid", "Unknown/Zone"), 422),
				Arguments.of("definitive start", JsonPath.parse(REQUEST).put("$", "startsAt", "2027-05-10T20:00:00").jsonString(), 422),
				Arguments.of("definitive end", JsonPath.parse(REQUEST).put("$", "endsAt", "2027-05-10T22:00:00").jsonString(), 422),
				Arguments.of("attendance options", JsonPath.parse(REQUEST).put("$", "responseOptions", List.of(Map.of("label", "Sí", "countsAsAttendance", true))).jsonString(), 422),
				Arguments.of("invalid date syntax", REQUEST.replace("2027-05-10", "not-a-date"), 400),
				Arguments.of("invalid time syntax", REQUEST.replace("20:00:00", "25:00:00"), 400),
				Arguments.of("invalid deadline syntax", REQUEST.replace("2027-05-09T18:00:00", "not-a-deadline"), 400),
				Arguments.of("invalid event type", REQUEST.replace("DATE_POLL", "UNKNOWN_TYPE"), 400));
	}
	@ParameterizedTest(name = "{0}") @MethodSource("invalidRequests")
	void rejectsInvalidRequestsWithoutStoringAnything(String description, String body, int expectedStatus) throws Exception {
		var response = send("POST", "/api/events", body, auth);
		assertThat(response.statusCode()).as(description).isEqualTo(expectedStatus);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(mongo.getCollection("events").countDocuments()).isZero();
	}

	@Test void cannotRemoveTheDeadlineOrConvertTheTypeAndRejectedEditsLeaveTheBsonIntact() throws Exception {
		String id = create(); Document before = bson(id);
		assertThat(send("PUT", "/api/events/" + id + "/response-deadline", "{\"responseDeadline\":null}", auth).statusCode()).isEqualTo(422);
		assertThat(send("PUT", "/api/events/" + id, JsonPath.parse(REQUEST).delete("$.responseDeadline").jsonString(), auth).statusCode()).isEqualTo(422);
		assertThat(send("PUT", "/api/events/" + id, """
				{"title":"Cena","eventType":"FIXED_DATE","startsAt":"2027-05-10T20:00:00","endsAt":"2027-05-10T22:00:00",
				"timeZone":"Europe/Madrid","modality":"IN_PERSON","responseDeadline":"2027-05-09T18:00:00"}
				""", auth).statusCode()).isEqualTo(422);
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void createsOnlinePollsWithoutDefinitiveDates() throws Exception {
		String request = JsonPath.parse(REQUEST).set("$.modality", "ONLINE").delete("$.venue")
				.put("$", "onlineAccess", Map.of("platform", "Zoom", "url", "https://example.com/private-room", "linkVisibility", "ON_CONFIRMATION")).jsonString();
		var response = send("POST", "/api/events", request, auth);
		assertThat(response.statusCode()).isEqualTo(201);
		var json = JsonPath.parse(response.body());
		assertThat(json.read("$.modality", String.class)).isEqualTo("ONLINE");
		assertThat(json.read("$.startsAt", String.class)).isNull();
		assertThat(json.read("$.endsAt", String.class)).isNull();
		assertThat(json.read("$.datePoll.candidateDates", List.class)).hasSize(3);
	}
	@Test void loweringTheCreationLimitDoesNotPreventReadingExistingLargerPolls() throws Exception {
		String id = create();
		Document candidate = new Document();
		mongo.getConverter().write(new com.gtog.event.infrastructure.out.persistence.DatePollDocument.Candidate(
				"previous-candidate", "2027-05-13", null), candidate);
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$push",
				new Document("datePoll.candidateDates", candidate)));
		Document before = bson(id);
		var response = send("GET", "/api/events/" + id, "", auth);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(response.body()).read("$.datePoll.candidateDates", List.class)).hasSize(4);
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void publicationPreservesThePollButReplacingAttendanceOptionsRemainsForbidden() throws Exception {
		String id = create(); Document before = bson(id);
		// Se retira la guarda temporal del paso 2, conservando las comprobaciones de integridad.
		assertThat(send("POST", "/api/events/" + id + "/publish", "", auth).statusCode()).isEqualTo(200);
		Document published = bson(id);
		assertThat(published.getString("status")).isEqualTo("PUBLISHED");
		assertThat(published.get("datePoll", Document.class)).isEqualTo(before.get("datePoll", Document.class));
		assertThat(published.getLong("version")).isEqualTo(before.getLong("version") + 1);
		assertThat(send("PUT", "/api/events/" + id + "/response-options", "{\"responseOptions\":[{\"label\":\"Sí\",\"countsAsAttendance\":true},{\"label\":\"No\",\"countsAsAttendance\":false}]}", auth).statusCode()).isEqualTo(409);
		assertThat(bson(id)).isEqualTo(published);
	}
	@Test void anUnverifiedHostCannotCreatePolls() throws Exception {
		mongo.getCollection("users").updateOne(new Document("_id", "host"), new Document("$set", new Document("emailVerified", false)));
		var response = send("POST", "/api/events", REQUEST, auth);
		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(JsonPath.parse(response.body()).read("$.code", String.class)).isEqualTo("EMAIL_NOT_VERIFIED");
		assertThat(mongo.getCollection("events").countDocuments()).isZero();
	}
	@Test void anAnonymousCallerAndAnInvalidCsrfCannotCreatePolls() throws Exception {
		String csrfCookie = auth.get("Cookie").split(";")[0];
		assertThat(send("POST", "/api/events", REQUEST, Map.of("Content-Type", "application/json", "Cookie", csrfCookie, "X-XSRF-TOKEN", auth.get("X-XSRF-TOKEN"))).statusCode()).isEqualTo(401);
		assertThat(send("POST", "/api/events", REQUEST, Map.of("Content-Type", "application/json", "Cookie", auth.get("Cookie"))).statusCode()).isEqualTo(403);
		assertThat(mongo.getCollection("events").countDocuments()).isZero();
	}
	@Test void anotherHostsPollIsHidden() throws Exception {
		String id = create(); mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("hostId", "another-host")));
		assertThat(send("GET", "/api/events/" + id, "", auth).statusCode()).isEqualTo(404);
	}
	@Test void corruptCandidateDataIsAStorageErrorRatherThanAClientValidationError() throws Exception {
		String id = create(); mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("datePoll.candidateDates.0.date", "corrupt-date")));
		var response = send("GET", "/api/events/" + id, "", auth);
		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).doesNotContain("corrupt-date", "DateTimeParseException");
	}
	private String create() throws Exception {
		var response = send("POST", "/api/events", REQUEST, auth); assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.parse(response.body()).read("$.id");
	}
	private Document bson(String id) {
		return mongo.getCollection("events").find(new Document("_id", id)).first();
	}

	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(value -> value.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
