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
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
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
	@MockitoSpyBean private GuestResponseRepositoryPort responseRepository;

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
		assertThat(json.read("$.canRespond", Boolean.class)).isTrue();
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
	@Test void availabilityStoresMultipleDatesAndThenExplicitNoneWithoutCreatingAnotherDocument(CapturedOutput output) throws Exception {
		String id = create(null, true);
		var guest = add(id, "Ana", "ana@example.com", null);
		publish(id);
		var firstView = JsonPath.parse(send("GET", invitation(guest), "", Map.of()).body());
		String first = firstView.read("$.datePoll.candidateDates[0].id");
		String second = firstView.read("$.datePoll.candidateDates[1].id");
		var initial = availability(guest, 0, List.of(second, first));
		assertThat(initial.statusCode()).isEqualTo(200);
		noStore(initial);
		assertThat(JsonPath.parse(initial.body()).<List<String>>read("$.myAvailability.candidateDateIds")).containsExactly(first, second);
		assertThat(initial.body()).doesNotContain(guestToken(guest), "ana@example.com");
		var now = Instant.parse("2027-03-01T10:05:00Z");
		when(clock.instant()).thenReturn(now);
		var none = availability(guest, 0, List.of());
		assertThat(none.statusCode()).isEqualTo(200);
		var get = JsonPath.parse(send("GET", invitation(guest), "", Map.of()).body());
		assertThat(get.<List<String>>read("$.myAvailability.candidateDateIds")).isEmpty();
		assertThat(get.<String>read("$.myAvailability.answeredAt")).isEqualTo("2027-03-01T10:00:00Z");
		assertThat(get.<String>read("$.myAvailability.updatedAt")).isEqualTo(now.toString());
		assertThat(mongo.getCollection("responses").countDocuments(new Document("eventId", id))).isEqualTo(1);
		assertThat(output.getAll()).doesNotContain(guestToken(guest));
	}

	@Test void aNewRoundAllowsAnAnswerEvenWhenChangesAreForbidden() throws Exception {
		String id = create(null, false);
		var guest = add(id, "Ana", "ana@example.com", null);
		publish(id);
		assertThat(availability(guest, 0, List.of()).statusCode()).isEqualTo(200);
		problem(availability(guest, 0, List.of()), guestToken(guest));
		String replacement = """
				{"pollRevision":0,"candidateDates":[{"date":"2027-05-20"},{"date":"2027-05-21"}]}
				""";
		assertThat(send("PUT", "/api/events/" + id + "/candidate-dates", replacement, auth).statusCode()).isEqualTo(200);
		var get = JsonPath.parse(send("GET", invitation(guest), "", Map.of()).body());
		assertThat(get.<Boolean>read("$.canRespond")).isTrue();
		assertThat(get.<Map<String, Object>>read("$")).doesNotContainKey("myAvailability");
		assertThat(availability(guest, 1, List.of()).statusCode()).isEqualTo(200);
		problem(availability(guest, 0, List.of()), guestToken(guest));
		assertThat(mongo.getCollection("responses").countDocuments(new Document("eventId", id))).isEqualTo(1);
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void simultaneousAvailabilityWritesRespectTheChangePolicy(boolean allowChanges) throws Exception {
		String id = create(null, allowChanges);
		var guest = add(id, "Ana", "ana@example.com", null);
		publish(id);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var start = new java.util.concurrent.CyclicBarrier(2);
			java.util.concurrent.Callable<Integer> submit = () -> {
				start.await(15, java.util.concurrent.TimeUnit.SECONDS);
				return availability(guest, 0, List.of()).statusCode();
			};
			var first = executor.submit(submit);
			var second = executor.submit(submit);
			assertThat(List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
					second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, allowChanges ? 200 : 409);
		}
		assertThat(mongo.getCollection("responses").countDocuments(new Document("eventId", id))).isEqualTo(1);
	}

	@ParameterizedTest @ValueSource(longs = {-1, 0, 1})
	void availabilityUsesTheInclusiveDeadline(long offset) throws Exception {
		String id = create(null);
		var guest = add(id, "Ana", "ana@example.com", null);
		publish(id);
		Instant deadline = LocalDateTime.of(2027, 5, 9, 18, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant();
		when(clock.instant()).thenReturn(deadline.plusNanos(offset));
		var response = availability(guest, 0, List.of());
		assertThat(response.statusCode()).isEqualTo(offset <= 0 ? 200 : 409);
		noStore(response);
	}

	@Test void anInFlightOlderRoundCannotOverwriteTheNewRound(CapturedOutput output) throws Exception {
		String id = create(null, true);
		var guest = add(id, "Ana", "ana@example.com", null);
		publish(id);
		String token = guestToken(guest);
		doAnswer(invocation -> {
			String replacement = """
					{"pollRevision":0,"candidateDates":[{"date":"2027-05-20"},{"date":"2027-05-21"}]}
					""";
			assertThat(send("PUT", "/api/events/" + id + "/candidate-dates", replacement, auth).statusCode()).isEqualTo(200);
			assertThat(availability(guest, 1, List.of()).statusCode()).isEqualTo(200);
			return invocation.callRealMethod();
		}).when(responseRepository).saveAvailability(eq(id), eq(token), argThat(value -> value.pollRevision() == 0), eq(true));

		problem(availability(guest, 0, List.of()), token);
		var response = mongo.getCollection("responses").find(new Document("eventId", id)).first();
		assertThat(response.get("availability", Document.class).getLong("pollRevision")).isEqualTo(1);
		assertThat(output.getAll()).doesNotContain(token);
	}

	@Test void invitationBatchesAndDeletionOnlyRecognizeAnswersFromTheCurrentRound() throws Exception {
		String id = create(null);
		var guest = add(id, "Ana", "ana@example.com", "+34612345678");
		publish(id);
		assertThat(availability(guest, 0, List.of()).statusCode()).isEqualTo(200);
		String root = "/api/events/" + id;
		var links = send("POST", root + "/invitations/whatsapp-links", "", auth);
		assertThat(JsonPath.parse(links.body()).<String>read("$.results[0].reason")).isEqualTo("ALREADY_ANSWERED");
		var email = send("POST", root + "/invitations/send", "", auth);
		assertThat(JsonPath.parse(email.body()).<String>read("$.results[0].reason")).isEqualTo("ALREADY_ANSWERED");
		assertThat(mail.attempts()).isEmpty();
		String guestPath = root + "/guests/" + guest.getString("id");
		assertThat(send("DELETE", guestPath, "", auth).statusCode()).isEqualTo(409);
		String replacement = """
				{"pollRevision":0,"candidateDates":[{"date":"2027-05-20"},{"date":"2027-05-21"}]}
				""";
		assertThat(send("PUT", root + "/candidate-dates", replacement, auth).statusCode()).isEqualTo(200);
		links = send("POST", root + "/invitations/whatsapp-links", "", auth);
		assertThat(JsonPath.parse(links.body()).<String>read("$.results[0].status")).isEqualTo("READY");
		assertThat(send("DELETE", guestPath, "", auth).statusCode()).isEqualTo(204);
		assertThat(mongo.getCollection("responses").countDocuments(new Document("eventId", id))).isZero();
	}

	@Test void dashboardCountsCurrentAvailabilityPerCandidateWithoutExposingAdditionalCredentials() throws Exception {
		String id = create(null);
		var ana = add(id, "Ana", "ana@example.com", null);
		var bea = add(id, "Bea", "bea@example.com", null);
		var carla = add(id, "Carla", "carla@example.com", null);
		var diana = add(id, "Diana", "diana@example.com", null);
		publish(id);

		var prior = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		assertThat(availability(diana, 0, List.of(prior.candidateDates().getFirst().id())).statusCode()).isEqualTo(200);
		String root = "/api/events/" + id;
		String replacement = """
				{"pollRevision":0,"candidateDates":[{"date":"2027-05-20"},{"date":"2027-05-21","startTime":"19:30"},{"date":"2027-05-22"}]}
				""";
		assertThat(send("PUT", root + "/candidate-dates", replacement, auth).statusCode()).isEqualTo(200);
		var poll = (DatePollSchedule) events.findById(id).orElseThrow().getSchedule();
		String first = poll.candidateDates().getFirst().id();
		String second = poll.candidateDates().get(1).id();
		assertThat(availability(ana, 1, List.of(first, second)).statusCode()).isEqualTo(200);
		assertThat(availability(bea, 1, List.of(second)).statusCode()).isEqualTo(200);
		assertThat(availability(carla, 1, List.of()).statusCode()).isEqualTo(200);
		var now = Instant.parse("2027-03-01T10:00:00Z");
		responseRepository.saveAvailability(id, "orphan-token", new GuestAvailability(1, List.of(first), now, now), false);
		var before = bson(id);
		var stored = mongo.getCollection("responses").find(new Document("eventId", id)).into(new java.util.ArrayList<>());

		var response = send("GET", root + "/dashboard", "", auth);
		assertThat(response.statusCode()).isEqualTo(200);
		noStore(response);
		var view = JsonPath.parse(response.body());
		assertThat(view.<Map<String, Integer>>read("$.totals")).containsExactlyInAnyOrderEntriesOf(
				Map.of("guests", 4, "answered", 3, "pending", 1, "attending", 0));
		assertThat(view.<List<?>>read("$.byOption")).isEmpty();
		assertThat(view.<Integer>read("$.availability.pollRevision")).isEqualTo(1);
		assertThat(view.<List<Integer>>read("$.availability.byCandidate[*].count")).containsExactly(1, 2, 0);
		assertThat(view.<List<String>>read("$.availability.byCandidate[*].candidateDateId"))
				.containsExactlyElementsOf(poll.candidateDates().stream().map(CandidateDate::id).toList());
		assertThat(view.<List<String>>read("$.availability.byCandidate[0].guests[*].name")).containsExactly("Ana");
		assertThat(view.<List<String>>read("$.availability.byCandidate[1].guests[*].name")).containsExactly("Ana", "Bea");
		assertThat(view.<List<String>>read("$.availability.pendingGuests[*].name")).containsExactly("Diana");
		assertThat(view.<List<String>>read("$.availability.unavailableGuests[*].name")).containsExactly("Carla");
		assertThat(view.<List<Boolean>>read("$.guests[*].answered")).containsExactly(true, true, true, false);
		assertThat(view.<Map<String, String>>read("$.availability.byCandidate[1].guests[0]"))
				.containsExactlyInAnyOrderEntriesOf(Map.of("id", ana.getString("id"), "name", "Ana"));
		assertThat(view.<Map<String, Object>>read("$.availability.byCandidate[0]")).containsEntry("startTime", null);
		assertThat(view.<String>read("$.availability.byCandidate[1].startTime")).isEqualTo("19:30:00");
		assertThat(response.body()).doesNotContain("orphan-token");
		for (Document guest : List.of(ana, bea, carla, diana)) {
			assertThat(response.body()).containsOnlyOnce(guestToken(guest));
		}
		assertThat(view.<List<String>>read("$.guests[*].invitationPath"))
				.containsExactlyElementsOf(List.of(ana, bea, carla, diana).stream()
						.map(guest -> guest.getString("invitationPath")).toList());
		assertThat(bson(id)).isEqualTo(before);
		assertThat(mongo.getCollection("responses").find(new Document("eventId", id)).into(new java.util.ArrayList<>()))
				.containsExactlyElementsOf(stored);
	}

	@Test void mixedListingLoadsAvailabilitiesInOneBatchAndPreservesFixedDateAttendance() throws Exception {
		String first = create(null);
		String second = create(null);
		var answered = add(first, "Ana", "ana@example.com", null);
		add(first, "Bea", "bea@example.com", null);
		add(second, "Carla", "carla@example.com", null);
		publish(first);
		publish(second);
		assertThat(availability(answered, 0, List.of()).statusCode()).isEqualTo(200);
		var fixed = events.save(com.gtog.event.GuestTestFixtures.event("mixed-fixed", EventStatus.PUBLISHED, null,
				com.gtog.event.GuestTestFixtures.guest(8)));
		mongo.getCollection("events").updateOne(new Document("_id", fixed.getId()),
				new Document("$set", new Document("hostId", "host-id-1")));
		var fixedGuest = fixed.getGuests().getFirst();
		responseRepository.insert(com.gtog.event.AttendanceTestFixtures.response(fixed, 0,
				fixed.getResponseOptions().getFirst().id()));
		clearInvocations(responseRepository);

		var result = send("GET", "/api/events", "", auth);
		assertThat(result.statusCode()).isEqualTo(200);
		List<Map<String, Object>> summaries = JsonPath.parse(result.body()).read("$");
		assertThat(summaries).hasSize(3);
		var byId = summaries.stream().collect(java.util.stream.Collectors.toMap(summary -> summary.get("id"), summary -> summary));
		assertThat(byId.get(first)).containsEntry("totalGuests", 2).containsEntry("answered", 1).containsEntry("pending", 1).containsEntry("attending", 0);
		assertThat(byId.get(second)).containsEntry("answered", 0).containsEntry("pending", 1).containsEntry("attending", 0);
		assertThat(byId.get(fixed.getId())).containsEntry("answered", 1).containsEntry("pending", 0).containsEntry("attending", 1);
		assertThat(result.body()).doesNotContain("byCandidate", guestToken(answered), fixedGuest.getToken());
		verify(responseRepository).findAvailabilityByEventIds(java.util.Set.of(first, second));
		verify(responseRepository).findByEventIds(java.util.Set.of(fixed.getId()));
		verifyNoMoreInteractions(responseRepository);
	}

	private HttpResponse<String> availability(Document guest, long revision, List<String> ids) throws Exception {
		return send("PUT", invitation(guest) + "/availability",
				new Document("pollRevision", revision).append("candidateDateIds", ids).toJson(),
				Map.of("Content-Type", "application/json"));
	}

	private String create(LinkVisibility visibility) throws Exception {
		return create(visibility, false);
	}

	private String create(LinkVisibility visibility, boolean allowChanges) throws Exception {
		String request = JsonPath.parse(REQUEST).put("$", "allowResponseChange", allowChanges).jsonString();
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
