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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.PublicInvitationRepositoryPort;
import com.gtog.event.infrastructure.out.mail.CapturingEmailSenderAdapter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.mockito.Mockito.*;
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

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.auth.auto-verify-email=false", "app.events.max-candidate-dates=3"})
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DatePollPublicationIntegrationTest {
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
	private final CapturingEmailSenderAdapter mail;
	private final HttpClient client = HttpClient.newHttpClient();
	private Map<String, String> auth;
	@MockitoBean private Clock clock;
	@MockitoSpyBean private PublicInvitationRepositoryPort joinRepository;

	DatePollPublicationIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, PasswordEncoder passwords, CapturingEmailSenderAdapter mail) {
		this.port = port; this.mongo = mongo; this.events = events; this.passwords = passwords; this.mail = mail;
	}

	@BeforeEach void setUp() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2027-03-01T10:00:00Z"));
		mongo.remove(new Query(), EventDocument.class);
		mongo.remove(new Query(), UserDocument.class);
		mongo.getCollection("responses").deleteMany(new Document()); mail.reset();
		mongo.insert(UserTestFixtures.verified("host-id-1", "Anfitrión", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"),
				"X-XSRF-TOKEN", csrf);
	}

	@AfterEach void close() { client.close(); }

	@Test void publicationCreatesThePublicLinkAtomicallyWithoutFillingDatesOrChangingTheRound() throws Exception {
		String id = create(null); var guest = add(id, "NombreInvitadoUno", "one@example.com", "+34612345678");
		Document before = bson(id);
		assertThat(before.get("publicInvitation", Document.class)).doesNotContainKey("token");
		assertThat(send("GET", invitation(guest), "", Map.of()).statusCode()).isEqualTo(404);
		publish(id);
		Document after = bson(id);
		assertThat(after.getString("status")).isEqualTo("PUBLISHED");
		assertThat(after.getLong("version")).isEqualTo(before.getLong("version") + 1);
		assertThat(after.get("datePoll", Document.class)).isEqualTo(before.get("datePoll", Document.class));
		assertThat(after.getList("guests", Document.class)).isEqualTo(before.getList("guests", Document.class));
		assertThat(after).doesNotContainKeys("startsAt", "endsAt");
		String token = joinToken(id);
		assertThat(token).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(id).isNotEqualTo(guestToken(guest));
	}
	@Test void thePublicViewOnlyExposesTheInvitedNameAndCandidateProjectionAndDoesNotLogTokens(CapturedOutput output) throws Exception {
		String id = create(null); var first = add(id, "NombreInvitadoUno", "one@example.com", "+34612345678");
		var other = add(id, "NombreInvitadoDos", "two@example.com", "+34612345679"); publish(id);
		var response = send("GET", invitation(first), "", Map.of()); assertThat(response.statusCode()).isEqualTo(200); noStore(response);
		var json = JsonPath.parse(response.body());
		assertThat(json.read("$.guest", Map.class)).containsOnlyKeys("name");
		assertThat(json.read("$.datePoll.candidateDates", List.class)).hasSize(3);
		assertThat(json.read("$.datePoll.candidateDates[1].startTime", String.class)).isNull();
		assertThat(json.read("$.datePoll.pollRevision", Long.class)).isZero();
		assertThat(json.read("$.responseOptions", List.class)).isEmpty();
		assertThat(json.read("$.canRespond", Boolean.class)).isFalse();
		assertThat(json.read("$.canChangeResponse", Boolean.class)).isFalse();
		assertThat(response.body()).doesNotContain(guestToken(first), guestToken(other), "host-id-1", "one@example.com", "two@example.com", "+34612345678", "+34612345679", "NombreInvitadoDos",
				"countsAsAttendance", "\"hostId\"", "\"guests\"", "\"version\"", "\"startsAt\"", "\"endsAt\"", "\"startsAtInstant\"", "\"endsAtInstant\"", "\"myResponse\"");
		var opened = events.findById(id).orElseThrow().getGuests().getFirst();
		assertThat(opened.getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(opened.getOpenedAt()).isEqualTo(Instant.parse("2027-03-01T10:00:00Z"));
		Document after = bson(id); assertThat(send("GET", invitation(first), "", Map.of()).statusCode()).isEqualTo(200);
		assertThat(bson(id)).isEqualTo(after);
		assertThat(output.getAll()).doesNotContain(guestToken(first), guestToken(other), joinToken(id), "one@example.com", "two@example.com", "NombreInvitadoUno", "NombreInvitadoDos");
	}
	@ParameterizedTest @EnumSource(LinkVisibility.class)
	void roomSecretsRemainFilteredWhenDefinitiveDatesAreMissing(LinkVisibility visibility) throws Exception {
		String id = create(visibility); var guest = add(id, "Ana", "ana@example.com", null); publish(id);
		var response = send("GET", invitation(guest), "", Map.of()); assertThat(response.statusCode()).isEqualTo(200); noStore(response);
		assertThat(JsonPath.parse(response.body()).read("$.onlineAccessInfo.platform", String.class)).isEqualTo("Zoom");
		if (visibility == LinkVisibility.ALWAYS) {
			assertThat(response.body()).contains(com.gtog.event.InvitationTestFixtures.URL, com.gtog.event.InvitationTestFixtures.PASSWORD);
		} else {
			assertThat(response.body()).doesNotContain(com.gtog.event.InvitationTestFixtures.URL, com.gtog.event.InvitationTestFixtures.PASSWORD,
					com.gtog.event.InvitationTestFixtures.ROOM, com.gtog.event.InvitationTestFixtures.INSTRUCTIONS, "\"onlineAccess\"");
		}
	}
	@Test void draftsAndUnknownTokensStillReturnTheSame404() throws Exception {
		String id = create(null); var guest = add(id, "Ana", "ana@example.com", null);
		var draft = send("GET", invitation(guest), "", Map.of()); var unknown = send("GET", "/api/invitations/" + "N".repeat(43), "", Map.of());
		assertThat(draft.statusCode()).isEqualTo(404); assertThat(draft.body()).isEqualTo(unknown.body()); noStore(draft); noStore(unknown);
		assertThat(send("GET", invitation(guest) + "/calendar", "", Map.of()).statusCode()).isEqualTo(404);
	}
	@Test void calendarAndTheAttendancePutRejectPollsWithSanitized409AndDoNotWrite() throws Exception {
		String id = create(null); var guest = add(id, "Ana", "ana@example.com", null); publish(id); Document before = bson(id);
		var calendar = send("GET", invitation(guest) + "/calendar", "", Map.of()); problem(calendar, guestToken(guest));
		assertThat(JsonPath.parse(calendar.body()).read("$.detail", String.class)).isEqualTo("Calendar is not available without a definitive date");
		var answer = send("PUT", invitation(guest) + "/response", "{\"responseOptionId\":\"invented-option\"}", Map.of("Content-Type", "application/json"));
		problem(answer, guestToken(guest));
		assertThat(mongo.getCollection("responses").countDocuments()).isZero(); assertThat(bson(id)).isEqualTo(before);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedPollViewsKeepTheirCandidatesAndHideTheRoomWithoutMarkingOpened(EventStatus status) throws Exception {
		String id = create(LinkVisibility.ALWAYS); var guest = add(id, "Ana", "ana@example.com", null); publish(id); String token = joinToken(id);
		if (status == EventStatus.CANCELLED) { assertThat(send("POST", "/api/events/" + id + "/cancel", "{\"reason\":\"Cancelado\"}", auth).statusCode()).isEqualTo(200); }
		else { mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("status", "FINISHED"))); }
		Document before = bson(id);
		var invitation = send("GET", invitation(guest), "", Map.of()); assertThat(invitation.statusCode()).isEqualTo(200); noStore(invitation);
		assertThat(JsonPath.parse(invitation.body()).read("$.status", String.class)).isEqualTo(status.name());
		assertThat(invitation.body()).doesNotContain(com.gtog.event.InvitationTestFixtures.URL, com.gtog.event.InvitationTestFixtures.PASSWORD);
		var preview = send("GET", join(token), "", Map.of()); assertThat(preview.statusCode()).isEqualTo(200); noStore(preview);
		assertThat(JsonPath.parse(preview.body()).read("$.canJoin", Boolean.class)).isFalse();
		assertThat(JsonPath.parse(preview.body()).read("$.datePoll.candidateDates", List.class)).hasSize(3);
		var rejected = send("POST", join(token), "{\"name\":\"Otra persona\"}", Map.of("Content-Type", "application/json")); problem(rejected, token);
		assertThat(JsonPath.parse(rejected.body()).read("$.code", String.class)).isEqualTo("JOIN_CLOSED");
		problem(send("GET", invitation(guest) + "/calendar", "", Map.of()), guestToken(guest));
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void publicJoiningUsesTheSamePreviewCookieAndSourceRulesWithoutAnyDefinitiveDates() throws Exception {
		String id = create(null); publish(id); String token = joinToken(id);
		var preview = send("GET", join(token), "", Map.of()); assertThat(preview.statusCode()).isEqualTo(200); noStore(preview);
		assertThat(JsonPath.parse(preview.body()).read("$.canJoin", Boolean.class)).isTrue();
		assertThat(preview.body()).doesNotContain("host-id-1", token, "\"guest\"", "\"startsAt\"", "\"endsAt\"", "\"startsAtInstant\"");
		var joined = send("POST", join(token), "{\"name\":\"Invitada enlace\"}", Map.of("Content-Type", "application/json"));
		assertThat(joined.statusCode()).isEqualTo(201); noStore(joined);
		String path = JsonPath.parse(joined.body()).read("$.invitationPath");
		String cookie = joined.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
				.filter(value -> value.getName().startsWith("gtog_join_")).map(value -> value.getName() + "=" + value.getValue()).findFirst().orElseThrow();
		var repeat = send("POST", join(token), "{\"name\":\"Otra\"}", Map.of("Content-Type", "application/json", "Cookie", cookie)); problem(repeat, token);
		assertThat(JsonPath.parse(repeat.body()).read("$.code", String.class)).isEqualTo("ALREADY_JOINED");
		var previous = send("GET", join(token), "", Map.of("Cookie", cookie));
		assertThat(JsonPath.parse(previous.body()).read("$.alreadyJoined", Boolean.class)).isTrue();
		assertThat(JsonPath.parse(previous.body()).read("$.invitationPath", String.class)).isEqualTo(path);
		var guest = events.findById(id).orElseThrow().getGuests().getFirst();
		assertThat(guest.getSource()).isEqualTo(GuestSource.PUBLIC_LINK); assertThat(guest.getJoinedAt()).isEqualTo(Instant.parse("2027-03-01T10:00:00Z"));
		assertThat(send("GET", "/api/invitations/" + path.substring(3), "", Map.of()).statusCode()).isEqualTo(200);
	}
	@ParameterizedTest @ValueSource(longs = {-1, 0, 1})
	void admissionUsesThePollDeadlineInclusivelyWithoutAnEndInstant(long offset) throws Exception {
		String id = create(null); publish(id); String token = joinToken(id);
		Instant threshold = LocalDateTime.of(2027, 5, 9, 18, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant();
		when(clock.instant()).thenReturn(threshold.plusNanos(offset));
		var preview = send("GET", join(token), "", Map.of()); assertThat(preview.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(preview.body()).read("$.canJoin", Boolean.class)).isEqualTo(offset <= 0);
		var result = send("POST", join(token), "{\"name\":\"Invitada\"}", Map.of("Content-Type", "application/json"));
		assertThat(result.statusCode()).isEqualTo(offset <= 0 ? 201 : 409); noStore(result);
	}
	@Test void emailAndWhatsappIncludeThePollScheduleButNeverTheRoomOrAnotherGuestsContact() throws Exception {
		String id = create(LinkVisibility.ALWAYS); var first = add(id, "Ana", "one@example.com", "+34612345678");
		add(id, "NombrePrivadoDos", "two@example.com", "+34612345679"); publish(id);
		String guestId = first.getString("id");
		var sent = send("POST", "/api/events/" + id + "/invitations/send", new Document("guestIds", List.of(guestId)).toJson(), auth);
		assertThat(sent.statusCode()).isEqualTo(200); assertThat(JsonPath.parse(sent.body()).read("$.results[0].status", String.class)).isEqualTo("SENT");
		assertThat(mail.messages()).hasSize(1);
		assertThat(mail.messages().getFirst().body()).contains("Fecha por definir", "10/05/2027 a las 20:00", "11/05/2027 (sin hora definida)", "09/05/2027 18:00", "Plataforma: Zoom")
				.doesNotContain(com.gtog.event.InvitationTestFixtures.URL, com.gtog.event.InvitationTestFixtures.PASSWORD, "two@example.com", "NombrePrivadoDos", "Fin:", "Inicio:", "00:00");
		Document before = bson(id);
		var links = send("POST", "/api/events/" + id + "/invitations/whatsapp-links", new Document("guestIds", List.of(guestId)).toJson(), auth);
		assertThat(links.statusCode()).isEqualTo(200); noStore(links);
		String message = JsonPath.parse(links.body()).read("$.results[0].message"); String url = JsonPath.parse(links.body()).read("$.results[0].url");
		assertThat(URLDecoder.decode(URI.create(url).getRawQuery().substring(5), StandardCharsets.UTF_8)).isEqualTo(message);
		assertThat(message).contains("Fechas propuestas", "11/05/2027 (sin hora definida)").doesNotContain(com.gtog.event.InvitationTestFixtures.URL, "two@example.com", "NombrePrivadoDos", "Fin:", "Inicio:");
		assertThat(bson(id)).isEqualTo(before);
	}
	@Test void aPublicContactsEmailIsStillUnverifiedWhileWhatsappRemainsAvailable() throws Exception {
		String id = create(null); publish(id); String token = joinToken(id);
		var joined = send("POST", join(token), "{\"name\":\"Invitada\",\"email\":\"public@example.com\",\"phone\":\"+34612345678\"}", Map.of("Content-Type", "application/json"));
		assertThat(joined.statusCode()).isEqualTo(201); Document before = bson(id);
		var skipped = send("POST", "/api/events/" + id + "/invitations/send", "", auth); assertThat(skipped.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(skipped.body()).read("$.results[0].reason", String.class)).isEqualTo("UNVERIFIED_CONTACT");
		assertThat(mail.attempts()).isEmpty(); assertThat(bson(id)).isEqualTo(before);
		var links = send("POST", "/api/events/" + id + "/invitations/whatsapp-links", "", auth);
		assertThat(links.statusCode()).isEqualTo(200); assertThat(JsonPath.parse(links.body()).read("$.results[0].status", String.class)).isEqualTo("READY");
	}
	@ParameterizedTest @ValueSource(strings = {"revision", "selection", "deadline"})
	void admissionRejectsAConfigurationChangedBetweenReadingAndWriting(String changed) throws Exception {
		String id = create(null); publish(id); String token = joinToken(id);
		var candidateList = JsonPath.parse(send("GET", "/api/events/" + id, "", auth).body()).read("$.datePoll.candidateDates", List.class);
		doAnswer(invocation -> {
			if (changed.equals("revision")) {
				var values = new java.util.ArrayList<java.util.Map<String, Object>>();
				for (Object value : candidateList) { values.add(new java.util.HashMap<>((Map<String, Object>) value)); }
				values.getFirst().put("date", "2027-05-20");
				assertThat(send("PUT", "/api/events/" + id + "/candidate-dates", new Document("pollRevision", 0).append("candidateDates", values).toJson(), auth).statusCode()).isEqualTo(200);
			} else if (changed.equals("selection")) {
				String selected = (String) ((Map<?, ?>) candidateList.getFirst()).get("id");
				mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$set", new Document("datePoll.selectedCandidateDateId", selected)).append("$inc", new Document("version", 1)));
			} else {
				assertThat(send("PUT", "/api/events/" + id + "/response-deadline", "{\"responseDeadline\":\"2027-02-01T10:00:00\"}", auth).statusCode()).isEqualTo(200);
			}
			return invocation.callRealMethod();
		}).when(joinRepository).tryAddGuest(any(), eq(token), any(), any());
		var result = send("POST", join(token), "{\"name\":\"Invitada\"}", Map.of("Content-Type", "application/json")); problem(result, token);
		assertThat(JsonPath.parse(result.body()).read("$.code", String.class)).isEqualTo("JOIN_CLOSED");
		assertThat(events.findById(id).orElseThrow().getGuests()).isEmpty();
	}
	@Test void admissionDoesNotLoseAnOpeningAndDoesNotCompareTheAggregateVersion() throws Exception {
		String id = create(null); var guest = add(id, "Ana", "ana@example.com", null); publish(id); String token = joinToken(id);
		doAnswer(invocation -> { assertThat(send("GET", invitation(guest), "", Map.of()).statusCode()).isEqualTo(200); return invocation.callRealMethod(); })
				.when(joinRepository).tryAddGuest(any(), eq(token), any(), any());
		assertThat(send("POST", join(token), "{\"name\":\"Nueva\"}", Map.of("Content-Type", "application/json")).statusCode()).isEqualTo(201);
		var event = events.findById(id).orElseThrow(); assertThat(event.getGuests()).hasSize(2);
		assertThat(event.getGuest(guest.getString("id")).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
	}
	@Test void legacyZeroRevisionStillAllowsPublicJoiningWithoutMigratingTheSchedule() throws Exception {
		String id = create(null); publish(id); String token = joinToken(id);
		mongo.getCollection("events").updateOne(new Document("_id", id), new Document("$unset", new Document("datePoll.pollRevision", "")));
		assertThat(send("POST", join(token), "{\"name\":\"Invitada\"}", Map.of("Content-Type", "application/json")).statusCode()).isEqualTo(201);
		assertThat(bson(id).get("datePoll", Document.class)).doesNotContainKey("pollRevision");
	}
	private String create(LinkVisibility visibility) throws Exception {
		String request = REQUEST;
		if (visibility != null) {
			var access = new java.util.HashMap<String, Object>(); access.put("platform", "Zoom"); access.put("url", com.gtog.event.InvitationTestFixtures.URL);
			access.put("password", com.gtog.event.InvitationTestFixtures.PASSWORD); access.put("roomId", com.gtog.event.InvitationTestFixtures.ROOM);
			access.put("instructions", com.gtog.event.InvitationTestFixtures.INSTRUCTIONS); access.put("linkVisibility", visibility.name());
			if (visibility == LinkVisibility.HOURS_BEFORE) { access.put("hoursBefore", 2); }
			request = JsonPath.parse(request).set("$.modality", "ONLINE").delete("$.venue").put("$", "onlineAccess", access).jsonString();
		}
		var result = send("POST", "/api/events", request, auth); assertThat(result.statusCode()).isEqualTo(201); return JsonPath.parse(result.body()).read("$.id");
	}
	private void publish(String id) throws Exception { var response = send("POST", "/api/events/" + id + "/publish", "", auth); assertThat(response.statusCode()).isEqualTo(200); }
	private String joinToken(String id) throws Exception {
		var response = send("GET", "/api/events/" + id + "/public-invitation", "", auth); assertThat(response.statusCode()).isEqualTo(200);
		String path = JsonPath.parse(response.body()).read("$.path"); return path.substring(3);
	}
	private Document add(String id, String name, String email, String phone) throws Exception {
		var response = send("POST", "/api/events/" + id + "/guests", new Document("name", name).append("email", email).append("phone", phone).toJson(), auth);
		assertThat(response.statusCode()).isEqualTo(201); return Document.parse(response.body());
	}
	private String guestToken(Document guest) { return guest.getString("invitationPath").substring(3); }
	private String invitation(Document guest) { return "/api/invitations/" + guestToken(guest); }
	private String join(String token) { return "/api/invitations/join/" + token; }
	private void noStore(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store"); }
	private void problem(HttpResponse<String> response, String token) {
		assertThat(response.statusCode()).isEqualTo(409); noStore(response);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/invitations");
		assertThat(response.body()).doesNotContain(token, "/api/invitations/" + token);
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
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
