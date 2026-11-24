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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.domain.model.EventStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
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
class CandidateDatesIntegrationTest {
	private static final String REQUEST = """
			{"title":"Cena","eventType":"DATE_POLL","timeZone":"Europe/Madrid","modality":"IN_PERSON",
			"responseDeadline":"2027-05-09T18:00:00","publicInvitationEnabled":false,
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
	@MockitoSpyBean private EventRepositoryPort eventSpy;

	CandidateDatesIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
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

	@Test void aRealChangeAdvancesOnlyThePollAndClearsSelectionWhilePreservingGuestsAndQuota() throws Exception {
		String id = create(); var request = edits(id); String selected = (String) request.get(1).get("id");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("datePoll.pollRevision", 4L).append("datePoll.selectedCandidateDateId", selected)));
		assertThat(send("POST", "/api/events/" + id + "/guests", "{\"name\":\"Ana\",\"email\":\"ana@example.com\"}", auth).statusCode()).isEqualTo(201);
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("guests.0.invitationStatus", "OPENED")
				.append("guests.0.openedAt", java.util.Date.from(Instant.parse("2027-03-01T09:00:00Z")))
				.append("guests.0.emailSendCount", 2).append("guests.0.lastEmailSentAt", java.util.Date.from(Instant.parse("2027-03-01T08:00:00Z")))));
		mongo.getCollection("users").updateOne(new Document("_id", "host"), new Document("$set", new Document("emailSendsInWindow", 7)
				.append("windowStartedAt", java.util.Date.from(Instant.parse("2027-03-01T08:00:00Z")))));
		Document before = bson(id), userBefore = mongo.getCollection("users").find(new Document("_id", "host")).first();
		request.get(0).put("date", "2027-05-20");
		var response = send("PUT", path(id), body(4L, request), auth); assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(response.body()).read("$.datePoll.pollRevision", Long.class)).isEqualTo(5L);
		assertThat(JsonPath.parse(response.body()).read("$.datePoll.selectedCandidateDateId", String.class)).isNull();
		Document after = bson(id);
		assertThat(after.getLong("version")).isEqualTo(before.getLong("version") + 1);
		assertThat(after.get("datePoll", Document.class).getLong("pollRevision")).isEqualTo(5L);
		assertThat(after.get("datePoll", Document.class)).doesNotContainKey("selectedCandidateDateId");
		assertThat(after.getList("guests", Document.class)).isEqualTo(before.getList("guests", Document.class));
		before.remove("datePoll"); before.remove("version"); after.remove("datePoll"); after.remove("version");
		assertThat(after).isEqualTo(before);
		assertThat(mongo.getCollection("users").find(new Document("_id", "host")).first()).isEqualTo(userBefore);
	}
	@Test void identicalRequestsAreNoOpsAndReorderingKeepsTheRoundAndSelection() throws Exception {
		String id = create(); var request = edits(id); String selected = (String) request.get(1).get("id");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("datePoll.selectedCandidateDateId", selected)));
		Document before = bson(id); clearInvocations(eventSpy);
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(200);
		verify(eventSpy, never()).save(any()); assertThat(bson(id)).isEqualTo(before);
		var reversed = request.reversed();
		assertThat(send("PUT", path(id), body(0L, reversed), auth).statusCode()).isEqualTo(200);
		Document after = bson(id);
		assertThat(after.getLong("version")).isEqualTo(before.getLong("version") + 1);
		var poll = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		assertThat(poll.pollRevision()).isZero(); assertThat(poll.selectedCandidateDateId()).isEqualTo(selected);
		assertThat(poll.candidateDates()).extracting(com.gtog.event.domain.model.CandidateDate::id)
				.containsExactlyElementsOf(reversed.stream().map(candidate -> (String) candidate.get("id")).toList());
		clearInvocations(eventSpy);
		assertThat(send("PUT", path(id), body(0L, reversed), auth).statusCode()).isEqualTo(200);
		verify(eventSpy, never()).save(any()); assertThat(bson(id)).isEqualTo(after);
	}
	@Test void aRepeatedChangeWithAnOldRevisionDoesNotRegenerateIdsButAFurtherStaleEditConflicts() throws Exception {
		String id = create(); var request = edits(id);
		request.removeFirst(); request.add(new HashMap<>(Map.of("date", "2027-05-20")));
		String payload = body(0L, request);
		assertThat(send("PUT", path(id), payload, auth).statusCode()).isEqualTo(200);
		Document after = bson(id); clearInvocations(eventSpy);
		assertThat(send("PUT", path(id), payload, auth).statusCode()).isEqualTo(200);
		assertThat(bson(id)).isEqualTo(after); verify(eventSpy, never()).save(any());
		request.get(0).put("date", "2027-05-21");
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(409);
		assertThat(bson(id)).isEqualTo(after);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED"})
	void draftAndPublishedEventsAcceptCandidateChanges(EventStatus status) throws Exception {
		String id = create(); var request = edits(id); request.get(0).put("date", "2027-05-20");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("status", status.name())));
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(200);
		assertThat(bson(id).get("datePoll", Document.class).getLong("pollRevision")).isEqualTo(1L);
	}
	@Test void staleEditorsWithRemovedCandidateIdsReceive409() throws Exception {
		String id = create(); var before = edits(id); var replacement = edits(id);
		replacement.removeFirst(); replacement.add(new HashMap<>(Map.of("date", "2027-05-20")));
		assertThat(send("PUT", path(id), body(0L, replacement), auth).statusCode()).isEqualTo(200);
		Document saved = bson(id);
		assertThat(send("PUT", path(id), body(0L, before), auth).statusCode()).isEqualTo(409);
		assertThat(bson(id)).isEqualTo(saved);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsRejectChangesWithoutWriting(EventStatus status) throws Exception {
		String id = create(); var request = edits(id);
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("status", status.name())));
		Document before = bson(id);
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(409);
		assertThat(bson(id)).isEqualTo(before);
	}
	@ParameterizedTest @ValueSource(strings = {"missing-revision", "negative-revision", "null-list", "empty-list", "too-many", "unknown-id", "duplicate-date", "duplicate-id", "missing-date", "null-candidate"})
	void invalidCandidateConfigurationsReturn422AndPreserveTheWholeBson(String scenario) throws Exception {
		String id = create(); var request = edits(id); Long revision = 0L;
		switch (scenario) {
			case "missing-revision" -> revision = null;
			case "negative-revision" -> revision = -1L;
			case "null-list" -> request = null;
			case "empty-list" -> request.clear();
			case "too-many" -> request.add(new HashMap<>(Map.of("date", "2027-05-20")));
			case "unknown-id" -> request.get(0).put("id", "unknown-candidate");
			case "duplicate-date" -> request.get(0).put("date", request.get(1).get("date"));
			case "duplicate-id" -> request.get(0).put("id", request.get(1).get("id"));
			case "missing-date" -> request.get(0).remove("date");
			case "null-candidate" -> request.set(0, null);
		}
		Document before = bson(id);
		var response = send("PUT", path(id), body(revision, request), auth);
		assertThat(response.statusCode()).as(scenario).isEqualTo(422);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void fixedDateEventsRejectCandidateEditing() throws Exception {
		String fixed = REQUEST.replace("\"eventType\":\"DATE_POLL\"", "\"eventType\":\"FIXED_DATE\",\"startsAt\":\"2027-05-10T18:00:00\",\"endsAt\":\"2027-05-10T20:00:00\"");
		fixed = JsonPath.parse(fixed).delete("$.datePoll").jsonString();
		var created = send("POST", "/api/events", fixed, auth); assertThat(created.statusCode()).isEqualTo(201);
		String id = JsonPath.parse(created.body()).read("$.id"); Document before = bson(id);
		assertThat(send("PUT", path(id), body(0L, List.of()), auth).statusCode()).isEqualTo(409);
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void ownershipAuthenticationCsrfAndMalformedBodiesKeepTheirExistingPrecedence() throws Exception {
		String id = create(); String payload = body(0L, edits(id)); Document before = bson(id);
		String csrfCookie = auth.get("Cookie").split(";")[0];
		assertThat(send("PUT", path(id), payload, Map.of("Content-Type", "application/json", "Cookie", csrfCookie, "X-XSRF-TOKEN", auth.get("X-XSRF-TOKEN"))).statusCode()).isEqualTo(401);
		assertThat(send("PUT", path(id), payload, Map.of("Content-Type", "application/json", "Cookie", auth.get("Cookie"))).statusCode()).isEqualTo(403);
		assertThat(send("PUT", path(id), "{", auth).statusCode()).isEqualTo(400);
		assertThat(send("PUT", path(id), "", auth).statusCode()).isEqualTo(400);
		assertThat(bson(id)).isEqualTo(before);
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("hostId", "other-host")));
		Document foreign = bson(id);
		assertThat(send("PUT", path(id), body(null, null), auth).statusCode()).isEqualTo(404);
		assertThat(bson(id)).isEqualTo(foreign);
	}
	@Test void missingRevisionInOlderDocumentsMeansZeroWithoutReadMigration() throws Exception {
		String id = create(); mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$unset", new Document("datePoll.pollRevision", "")));
		Document before = bson(id);
		var response = send("GET", "/api/events/" + id, "", auth);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(response.body()).read("$.datePoll.pollRevision", Long.class)).isZero();
		assertThat(bson(id)).isEqualTo(before);
		var request = edits(id); request.get(0).put("date", "2027-05-20");
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(200);
		assertThat(bson(id).get("datePoll", Document.class).getLong("pollRevision")).isEqualTo(1L);
	}
	@Test void generalEditsAndDeadlineUpdatesPreserveTheIndependentRevisionAndSelection() throws Exception {
		String id = create(); String selected = (String) edits(id).get(1).get("id");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("datePoll.pollRevision", 7L).append("datePoll.selectedCandidateDateId", selected)));
		assertThat(send("PUT", "/api/events/" + id, REQUEST.replace("Cena", "Cena editada").replace("Europe/Madrid", "America/Bogota"), auth).statusCode()).isEqualTo(200);
		assertThat(send("PUT", "/api/events/" + id + "/response-deadline", "{\"responseDeadline\":\"2027-05-08T10:00:00\"}", auth).statusCode()).isEqualTo(200);
		var poll = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		assertThat(poll.pollRevision()).isEqualTo(7); assertThat(poll.selectedCandidateDateId()).isEqualTo(selected);
	}
	@Test void anExpiredPublishedPollStaysClosedAfterCandidateEditingUntilItsDeadlineIsChanged() throws Exception {
		String id = create(); var request = edits(id); request.get(0).put("date", "2027-05-20");
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("status", "PUBLISHED")
				.append("responseDeadline", java.util.Date.from(Instant.parse("2027-02-01T10:00:00Z")))));
		var deadline = events.findById(id).orElseThrow().getResponseDeadline();
		assertThat(send("PUT", path(id), body(0L, request), auth).statusCode()).isEqualTo(200);
		var event = events.findById(id).orElseThrow(); assertThat(event.getResponseDeadline()).isEqualTo(deadline);
		assertThat(event.acceptsResponses(Instant.parse("2027-03-01T10:00:00Z"))).isFalse();
		assertThat(send("PUT", "/api/events/" + id + "/response-deadline", "{\"responseDeadline\":\"2027-05-10T18:00:00\"}", auth).statusCode()).isEqualTo(200);
		assertThat(events.findById(id).orElseThrow().acceptsResponses(Instant.parse("2027-03-01T10:00:00Z"))).isTrue();
	}
	@Test void concurrentEditorsCannotOverwriteEachOthersRound() throws Exception {
		String id = create(); var first = edits(id); var second = edits(id);
		first.get(0).put("date", "2027-05-20"); second.get(0).put("date", "2027-05-21");
		CountDownLatch snapshots = new CountDownLatch(2); AtomicInteger reads = new AtomicInteger();
		doAnswer(invocation -> {
			Object snapshot = invocation.callRealMethod();
			if (reads.getAndIncrement() < 2) { snapshots.countDown(); if (!snapshots.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Concurrent reads did not overlap"); } }
			return snapshot;
		}).when(eventSpy).findById(id);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var one = executor.submit(() -> send("PUT", path(id), body(0L, first), auth));
			var two = executor.submit(() -> send("PUT", path(id), body(0L, second), auth));
			assertThat(List.of(one.get(30, TimeUnit.SECONDS).statusCode(), two.get(30, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 409);
		}
		assertThat(bson(id).getLong("version")).isEqualTo(1L);
		var poll = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		assertThat(poll.pollRevision()).isEqualTo(1);
		assertThat(poll.candidateDates().getFirst().date().toString()).isIn("2027-05-20", "2027-05-21");
	}
	@Test void openApiDocumentsTheNewOperationAndItsErrors() throws Exception {
		var response = send("GET", "/v3/api-docs", "", Map.of()); assertThat(response.statusCode()).isEqualTo(200);
		var operation = JsonPath.parse(response.body()).read("$.paths['/api/events/{id}/candidate-dates'].put.responses", Map.class);
		assertThat(operation).containsKeys("200", "400", "401", "403", "404", "409", "422");
	}
	private String create() throws Exception {
		var response = send("POST", "/api/events", REQUEST, auth); assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.parse(response.body()).read("$.id");
	}
	private String path(String id) { return "/api/events/" + id + "/candidate-dates"; }
	@SuppressWarnings("unchecked")
	private ArrayList<Map<String, Object>> edits(String id) throws Exception {
		var response = send("GET", "/api/events/" + id, "", auth); assertThat(response.statusCode()).isEqualTo(200);
		List<Map<String, Object>> values = JsonPath.parse(response.body()).read("$.datePoll.candidateDates");
		return new ArrayList<>(values.stream().map(candidate -> (Map<String, Object>) new HashMap<>(candidate)).toList());
	}
	private String body(Long revision, List<?> values) { return new Document("pollRevision", revision).append("candidateDates", values).toJson(); }
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
