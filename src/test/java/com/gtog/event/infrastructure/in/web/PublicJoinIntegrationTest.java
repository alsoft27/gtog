package com.gtog.event.infrastructure.in.web;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.PublishEventUseCase;
import com.gtog.event.domain.port.out.*;
import com.gtog.event.infrastructure.out.persistence.*;
import com.gtog.user.infrastructure.out.persistence.UserDocument;
import com.jayway.jsonpath.JsonPath;
import static com.gtog.event.GuestTestFixtures.token;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class PublicJoinIntegrationTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final PublishEventUseCase publisher;
	private final PublicJoinCookie cookies;
	private final GuestMapper guestMapper;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	private Event event;
	@MockitoBean private Clock clock;
	PublicJoinIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo, EventRepositoryPort events,
			PublishEventUseCase publisher, PublicJoinCookie cookies, GuestMapper guestMapper, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.publisher = publisher; this.cookies = cookies;
		this.guestMapper = guestMapper; this.passwords = passwords;
	}
	@BeforeEach void setUp() {
		when(clock.instant()).thenReturn(NOW);
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class);
		mongo.remove(new Query(), UserDocument.class);
		event = published();
	}
	@AfterEach void close() { client.close(); }
	private Event published() {
		Event draft = events.save(Event.builder().hostId("host-1").title("Fiesta").description("Celebración")
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("UTC")
				.modality(Modality.IN_PERSON).venue(new Venue("Sala", "Dirección", 0.0, 0.0, "place", null))
				.publicInvitationEnabled(true).build());
		return publisher.publish("host-1", draft.getId());
	}
	private String path() { return "/api/invitations/join/" + event.getPublicInvitation().token(); }
	private Document bson() { return mongo.getCollection("events").find(new Document("_id", event.getId())).first(); }
	private HttpResponse<String> post(String body) throws Exception { return send("POST", path(), body, Map.of("Content-Type", "application/json")); }
	private HttpResponse<String> get() throws Exception { return send("GET", path(), "", Map.of()); }
	private String invitationPath(HttpResponse<String> response) { return JsonPath.parse(response.body()).read("$.invitationPath"); }
	private String guestToken(HttpResponse<String> response) { return invitationPath(response).substring(3); }
	private List<Guest> currentGuests() { return events.findById(event.getId()).orElseThrow().getGuests(); }
	private void noStore(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control")).contains("no-store"); }
	private void error(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status); noStore(response);
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(JsonPath.parse(response.body()).read("$.instance", String.class)).isEqualTo("/api/invitations");
		assertThat(response.body()).doesNotContain(event.getPublicInvitation().token());
		if (code != null) { assertThat(JsonPath.parse(response.body()).read("$.code", String.class)).isEqualTo(code); }
	}
	@ParameterizedTest @ValueSource(strings = {"", ",\"email\":\" ANA@Example.com \"", ",\"phone\":\"+34 612 345 678\"", ",\"email\":\" ANA@Example.com \",\"phone\":\"+34 612 345 678\""})
	void joinsWithoutSessionOrCsrfWithEveryContactCombination(String contacts, CapturedOutput output) throws Exception {
		var created = post("{\"name\":\" Ana \"" + contacts + "}");
		assertThat(created.statusCode()).isEqualTo(201); noStore(created);
		Guest guest = currentGuests().getFirst();
		assertThat(guest.getName()).isEqualTo("Ana"); assertThat(guest.getSource()).isEqualTo(GuestSource.PUBLIC_LINK);
		assertThat(guest.getJoinedAt()).isEqualTo(NOW); assertThat(guest.getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(guest.getEmail()).isEqualTo(contacts.contains("email") ? "ana@example.com" : null);
		assertThat(guest.getPhoneE164()).isEqualTo(contacts.contains("phone") ? "+34612345678" : null);
		assertThat(guestToken(created)).isEqualTo(guest.getToken()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(event.getPublicInvitation().token());
		assertThat(created.headers().firstValue("Location")).contains(path());
		assertThat(output.getAll()).doesNotContain("ana@example.com", "+34612345678", guest.getToken(), event.getPublicInvitation().token());
		assertThat(get().statusCode()).isEqualTo(200); noStore(get());
	}
	@Test void previewIsAReadWithoutIdentitiesOrRoomSecretsEvenWhenAlwaysVisible() throws Exception {
		Event online = events.save(Event.builder().hostId("host-1").title("En línea").description("Sesión")
				.startsAt(LocalDateTime.of(2027, 1, 1, 18, 0)).endsAt(LocalDateTime.of(2027, 1, 1, 20, 0)).timeZone("UTC")
				.modality(Modality.ONLINE).publicInvitationEnabled(true).onlineAccess(new OnlineAccess("Jitsi", "https://secret.example/room", "room-secret", "password-secret", "instructions-secret", LinkVisibility.ALWAYS, null)).build());
		event = publisher.publish("host-1", online.getId()); Document before = bson();
		var response = get(); assertThat(response.statusCode()).isEqualTo(200); noStore(response);
		assertThat(response.body()).doesNotContain("host-1", "secret.example", "room-secret", "password-secret", "instructions-secret", "guest", "countsAsAttendance", event.getPublicInvitation().token());
		var json = JsonPath.parse(response.body());
		assertThat(json.read("$.onlineAccessInfo.platform", String.class)).isEqualTo("Jitsi");
		assertThat(json.read("$.canJoin", Boolean.class)).isTrue(); assertThat(json.read("$.alreadyJoined", Boolean.class)).isFalse();
		assertThat(json.read("$.startsAtInstant", String.class)).isEqualTo("2027-01-01T18:00:00Z");
		assertThat(bson()).isEqualTo(before);
	}
	@Test void duplicatedContactDiscardsBothAndIsIndistinguishableFromANonConflictingRegistration() throws Exception {
		var existing = post("{\"name\":\"Original\",\"email\":\"ana@example.com\"}");
		var duplicate = post("{\"name\":\"Otra\",\"email\":\" ANA@EXAMPLE.COM \",\"phone\":\"+34 612 345 678\"}");
		var normal = post("{\"name\":\"Nueva\",\"email\":\"new@example.com\"}");
		assertThat(duplicate.statusCode()).isEqualTo(normal.statusCode()).isEqualTo(201);
		assertThat(headersWithoutDateAndCookie(duplicate)).isEqualTo(headersWithoutDateAndCookie(normal));
		assertThat(JsonPath.parse(duplicate.body()).read("$", Map.class).keySet()).containsExactlyElementsOf(JsonPath.parse(normal.body()).read("$", Map.class).keySet());
		assertThat(invitationPath(duplicate)).startsWith("/r/").isNotEqualTo(invitationPath(existing)).isNotEqualTo(invitationPath(normal));
		Guest discarded = currentGuests().get(1); assertThat(discarded.getEmail()).isNull(); assertThat(discarded.getPhoneE164()).isNull();
	}
	private Map<String, List<String>> headersWithoutDateAndCookie(HttpResponse<?> response) {
		var headers = new TreeMap<>(response.headers().map()); headers.remove("date"); headers.remove("set-cookie"); return headers;
	}
	@Test void cookieIsHttpOnlyScopedHashedAndIssuedOnlyOnCreationAndRecognizesOnlyItsOwnGuest(CapturedOutput output) throws Exception {
		var created = post("{\"name\":\"Ana\"}");
		String rawCookie = created.headers().firstValue("Set-Cookie").orElseThrow();
		HttpCookie cookie = HttpCookie.parse(rawCookie).getFirst();
		assertThat(cookie.getName()).isEqualTo(cookies.name(event.getPublicInvitation().token())).matches("gtog_join_[0-9a-f]{16}").doesNotContain(event.getPublicInvitation().token(), guestToken(created));
		assertThat(cookie.getValue()).isEqualTo(guestToken(created)); assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/api/invitations/join"); assertThat(cookie.getMaxAge()).isEqualTo(2592000L);
		assertThat(rawCookie).contains("SameSite=Lax"); assertThat(cookie.getSecure()).isFalse();
		Map<String, String> headers = Map.of("Content-Type", "application/json", "Cookie", cookie.getName() + "=" + cookie.getValue());
		error(send("POST", path(), "{\"name\":\"Otra\"}", headers), 409, "ALREADY_JOINED");
		var preview = send("GET", path(), "", headers); noStore(preview);
		assertThat(preview.headers().allValues("Set-Cookie")).isEmpty();
		assertThat(JsonPath.parse(preview.body()).read("$.alreadyJoined", Boolean.class)).isTrue();
		assertThat(JsonPath.parse(preview.body()).read("$.canJoin", Boolean.class)).isFalse();
		assertThat(invitationPath(preview)).isEqualTo(invitationPath(created));
		assertThat(post("{\"name\":\"Sin cookie\"}").statusCode()).isEqualTo(201);
		assertThat(send("POST", path(), "{\"name\":\"Otro nombre de cookie\"}", Map.of("Content-Type", "application/json", "Cookie", "wrong=" + cookie.getValue())).statusCode()).isEqualTo(201);
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$pull", new Document("guests", new Document("token", guestToken(created)))));
		assertThat(send("POST", path(), "{\"name\":\"Cookie borrada\"}", headers).statusCode()).isEqualTo(201);
		Event other = published();
		assertThat(send("POST", "/api/invitations/join/" + other.getPublicInvitation().token(), "{\"name\":\"Cookie ajena\"}",
				Map.of("Content-Type", "application/json", "Cookie", cookies.name(other.getPublicInvitation().token()) + "=" + cookie.getValue())).statusCode()).isEqualTo(201);
		assertThat(output.getAll()).doesNotContain(cookie.getValue(), event.getPublicInvitation().token());
	}
	static Stream<Arguments> precedenceCases() {
		return Stream.of(Arguments.of("malformedUnknown", 400, null), Arguments.of("unknown", 404, null),
				Arguments.of("disabled", 404, null), Arguments.of("draft", 404, null),
				Arguments.of("cookieClosed", 409, "ALREADY_JOINED"), Arguments.of("closedInvalid", 409, "JOIN_CLOSED"),
				Arguments.of("fullInvalid", 422, null));
	}
	@ParameterizedTest @MethodSource("precedenceCases")
	void publicPostChecksErrorsInTheApprovedOrder(String scenario, int status, String code, CapturedOutput output) throws Exception {
		String target = path(), body = "{\"name\":\"\"}", cookieToken = null;
		var headers = new HashMap<String, String>(); headers.put("Content-Type", "application/json");
		switch (scenario) {
			case "malformedUnknown" -> { target = "/api/invitations/join/" + token(9999); body = "{"; }
			case "unknown" -> target = "/api/invitations/join/" + token(9999);
			case "disabled" -> mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("publicInvitation.enabled", false)).append("$unset", new Document("publicInvitation.token", "")));
			case "draft" -> mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", "DRAFT")).append("$unset", new Document("publicInvitation.token", "")));
			case "cookieClosed" -> {
				cookieToken = guestToken(post("{\"name\":\"Ya inscrita\"}"));
				headers.put("Cookie", cookies.name(event.getPublicInvitation().token()) + "=" + cookieToken);
				mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", "CANCELLED")));
			}
			case "closedInvalid" -> mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", "CANCELLED")));
			case "fullInvalid" -> seedGuests(25, true);
			default -> throw new IllegalArgumentException("Unknown test scenario");
		}
		Document before = bson(); var response = send("POST", target, body, headers); error(response, status, code);
		if (scenario.equals("fullInvalid")) { assertThat(JsonPath.parse(response.body()).read("$.errors[0].code", String.class)).isEqualTo("BLANK_NAME"); }
		assertThat(bson()).isEqualTo(before); assertThat(output.getAll()).doesNotContain(event.getPublicInvitation().token());
		if (cookieToken != null) { assertThat(output.getAll()).doesNotContain(cookieToken); assertThat(response.body()).doesNotContain(cookieToken); }
	}
	@ParameterizedTest @ValueSource(strings = {"host", "foreignPublic"})
	void onlyAPublicGuestOfThisEventIsRecognizedByTheCookie(String kind) throws Exception {
		String previous;
		if (kind.equals("host")) {
			Guest hostGuest = Guest.create("Host guest", "hostguest@example.com", null, token(60003), value -> value);
			event.addGuest(hostGuest); events.save(event); previous = hostGuest.getToken();
		} else {
			Event other = published();
			previous = guestToken(send("POST", "/api/invitations/join/" + other.getPublicInvitation().token(), "{\"name\":\"Otra invitada\"}", Map.of("Content-Type", "application/json")));
		}
		var headers = Map.of("Content-Type", "application/json", "Cookie", cookies.name(event.getPublicInvitation().token()) + "=" + previous);
		var preview = send("GET", path(), "", headers);
		assertThat(preview.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(preview.body()).read("$.alreadyJoined", Boolean.class)).isFalse();
		assertThat(preview.body()).doesNotContain(previous, "invitationPath");
		var created = send("POST", path(), "{\"name\":\"Nueva\"}", headers);
		assertThat(created.statusCode()).isEqualTo(201); assertThat(guestToken(created)).isNotEqualTo(previous);
	}
	@Test void aHostGuestCookieIsIgnored() throws Exception {
		event.addGuest(Guest.create("Host guest", "hostguest@example.com", null, token(60001), value -> value)); events.save(event);
		var response = send("POST", path(), "{\"name\":\"Nueva\"}", Map.of("Content-Type", "application/json", "Cookie", cookies.name(event.getPublicInvitation().token()) + "=" + token(60001)));
		assertThat(response.statusCode()).isEqualTo(201); assertThat(guestToken(response)).isNotEqualTo(token(60001));
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsKeepThePreviewAndExistingInvitationsButRejectJoining(EventStatus status) throws Exception {
		var created = post("{\"name\":\"Ana\"}");
		if (status == EventStatus.CANCELLED) {
			assertThat(send("POST", "/api/events/" + event.getId() + "/cancel", "{\"reason\":\"Cancelado\"}", login()).statusCode()).isEqualTo(200);
		} else {
			// Aún no existe una transición del producto a FINISHED.
			mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", status.name())));
		}
		var preview = get(); assertThat(preview.statusCode()).isEqualTo(200); noStore(preview);
		assertThat(JsonPath.parse(preview.body()).read("$.status", String.class)).isEqualTo(status.name());
		assertThat(JsonPath.parse(preview.body()).read("$.canJoin", Boolean.class)).isFalse();
		error(post("{\"name\":\"Otra\"}"), 409, "JOIN_CLOSED");
		error(send("POST", path(), "{\"name\":\"Otra\"}", Map.of("Content-Type", "application/json", "Cookie",
				cookies.name(event.getPublicInvitation().token()) + "=" + guestToken(created))), 409, "ALREADY_JOINED");
		var invitation = send("GET", "/api/invitations/" + guestToken(created), "", Map.of());
		assertThat(invitation.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(invitation.body()).read("$.status", String.class)).isEqualTo(status.name());
	}
	@ParameterizedTest @ValueSource(strings = {"deadline", "end"})
	void theSameResponseAcceptancePolicyClosesPreviewAndPost(String boundary) throws Exception {
		if (boundary.equals("deadline")) {
			mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(event.getId())),
					new org.springframework.data.mongodb.core.query.Update().set("responseDeadline", LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC)), EventDocument.class);
		} else { when(clock.instant()).thenReturn(event.endsAtInstant()); }
		assertThat(JsonPath.parse(get().body()).read("$.canJoin", Boolean.class)).isFalse(); error(post("{\"name\":\"Ana\"}"), 409, "JOIN_CLOSED");
	}
	@Test void revokedUnknownDisabledAndDraftLinksHaveTheSame404() throws Exception {
		String oldPath = path();
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("publicInvitation.enabled", false)).append("$unset", new Document("publicInvitation.token", "")));
		var revoked = get(); var unknown = send("GET", "/api/invitations/join/" + token(9999), "", Map.of());
		error(revoked, 404, null); assertThat(revoked.body()).isEqualTo(unknown.body());
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("publicInvitation.enabled", true).append("publicInvitation.token", token(9998))));
		assertThat(send("GET", oldPath, "", Map.of()).body()).isEqualTo(unknown.body());
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("status", "DRAFT")).append("$unset", new Document("publicInvitation.token", "")));
		var draft = send("GET", "/api/invitations/join/" + token(9998), "", Map.of()); assertThat(draft.statusCode()).isEqualTo(404); assertThat(draft.body()).isEqualTo(unknown.body());
	}
	private void seedGuests(int count, boolean publicGuests) {
		var documents = IntStream.range(0, count).mapToObj(index -> publicGuests
				? Guest.joinPublicInvitation("Guest " + index, null, null, token(20000 + index), value -> value, NOW)
				: Guest.create("Guest " + index, "guest" + index + "@example.com", null, token(20000 + index), value -> value))
				.map(guestMapper::toDocument).map(value -> { Document bson = new Document(); mongo.getConverter().write(value, bson); return bson; }).toList();
		mongo.getCollection("events").updateOne(new Document("_id", event.getId()), new Document("$set", new Document("guests", documents)));
	}
	@Test void publicCapacityAcceptsThe25thAndRejectsThe26th() throws Exception {
		seedGuests(24, true); assertThat(post("{\"name\":\"Última\"}").statusCode()).isEqualTo(201);
		Document before = bson(); error(post("{\"name\":\"Exceso\"}"), 409, "PUBLIC_LINK_FULL"); assertThat(bson()).isEqualTo(before);
	}
	@Test void totalCapacityAcceptsThe500thAndRejectsThe501st() throws Exception {
		seedGuests(499, false); assertThat(post("{\"name\":\"Última\"}").statusCode()).isEqualTo(201);
		Document before = bson(); error(post("{\"name\":\"Exceso\"}"), 409, "CAPACITY_EXCEEDED"); assertThat(bson()).isEqualTo(before);
	}
	static Stream<Arguments> invalidBodies() {
		return Stream.of(Arguments.of("{\"name\":\" \"}", "BLANK_NAME"), Arguments.of("{\"name\":\"" + "x".repeat(101) + "\"}", "NAME_TOO_LONG"),
				Arguments.of("{\"name\":\"PrivateName\\n\"}", "INVALID_NAME"), Arguments.of("{\"name\":\"PrivateName\",\"email\":\"private@@example.com\"}", "INVALID_EMAIL"),
				Arguments.of("{\"name\":\"PrivateName\",\"phone\":\"private-phone\"}", "INVALID_PHONE"));
	}
	@ParameterizedTest @MethodSource("invalidBodies")
	void validationListsOnlyFieldCodesAndDoesNotLogOrEchoInput(String body, String code, CapturedOutput output) throws Exception {
		Document before = bson(); var response = post(body); error(response, 422, null);
		assertThat(JsonPath.parse(response.body()).read("$.errors[*].code", List.class)).contains(code);
		assertThat(response.body()).doesNotContain("PrivateName", "private@@example.com", "private-phone");
		assertThat(output.getAll()).doesNotContain("PrivateName", "private@@example.com", "private-phone", event.getPublicInvitation().token());
		assertThat(bson()).isEqualTo(before);
	}
	@Test void malformedAndMissingBodiesAre400BeforeLookingUpAnUnknownLink() throws Exception {
		error(send("POST", "/api/invitations/join/" + token(9999), "{", Map.of("Content-Type", "application/json")), 400, null);
		error(post(""), 400, null);
	}
	@ParameterizedTest @ValueSource(strings = {"GET", "POST"})
	void corsAllowsAnExplicitFrontendOriginAndCredentialsWithoutChangingItsHandling(String method) throws Exception {
		var response = send("OPTIONS", path(), "", Map.of("Origin", "http://localhost:4200", "Access-Control-Request-Method", method, "Access-Control-Request-Headers", "Content-Type"));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
		assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
		assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow()).contains(method);
		assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase()).contains("content-type");
	}
	@Test void twoRealConcurrentRequestsForTheLastPlaceCreateExactlyOneGuest() throws Exception {
		seedGuests(24, true); var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Callable<Integer> task = () -> { barrier.await(); return post("{\"name\":\"Concurrente\"}").statusCode(); };
			var first = executor.submit(task); var second = executor.submit(task);
			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
		}
		assertThat(currentGuests()).hasSize(25);
	}
	@Test void aJoinedGuestCanRespondAppearInThePanelAndBeDeletedWithTheExistingConfirmedCascade() throws Exception {
		var created = post("{\"name\":\"Ana\"}"); String token = guestToken(created); Guest guest = currentGuests().getFirst();
		String option = event.getResponseOptions().getFirst().id();
		var response = send("PUT", "/api/invitations/" + token + "/response", "{\"responseOptionId\":\"" + option + "\"}", Map.of("Content-Type", "application/json"));
		assertThat(response.statusCode()).isEqualTo(200);
		Map<String, String> auth = login(); String base = "/api/events/" + event.getId();
		var dashboard = send("GET", base + "/dashboard", "", auth);
		assertThat(JsonPath.parse(dashboard.body()).read("$.guests[0].source", String.class)).isEqualTo("PUBLIC_LINK");
		assertThat(JsonPath.parse(dashboard.body()).read("$.totals.answered", Integer.class)).isEqualTo(1);
		assertThat(send("DELETE", base + "/guests/" + guest.getId(), "", auth).statusCode()).isEqualTo(409);
		assertThat(send("DELETE", base + "/guests/" + guest.getId() + "?confirmDeleteResponse=true", "", auth).statusCode()).isEqualTo(204);
		assertThat(currentGuests()).isEmpty(); assertThat(mongo.getCollection("responses").countDocuments(new Document("eventId", event.getId()))).isZero();
	}
	private Map<String, String> login() throws Exception {
		mongo.insert(com.gtog.UserTestFixtures.verified("host-1", "Host", "host@example.com", passwords.encode("password12"), "UTC"));
		String csrf = HttpCookie.parse(send("GET", "/actuator/health", "", Map.of()).headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow()).getFirst().getValue();
		var response = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}", Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		String session = response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream()).filter(value -> value.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
		return Map.of("Content-Type", "application/json", "Cookie", "JSESSIONID=" + session + "; XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf);
	}
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
