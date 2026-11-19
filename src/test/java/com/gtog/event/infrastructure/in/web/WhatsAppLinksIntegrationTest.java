package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.*;
import com.gtog.event.infrastructure.out.persistence.*;
import com.gtog.user.infrastructure.out.persistence.*;
import com.jayway.jsonpath.JsonPath;
import static com.gtog.event.GuestTestFixtures.token;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "app.public-base-url=https://gtog.example.com", "app.invitations.max-whatsapp-links-per-request=100" })
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class WhatsAppLinksIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final UserMongoRepository users;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoSpyBean private GuestResponseRepositoryPort responses;
	@MockitoSpyBean private GuestEmailSendReservationPort reservations;
	@MockitoSpyBean private HostSendQuotaPort quotas;
	private Event event;
	private Map<String, String> auth;

	WhatsAppLinksIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, UserMongoRepository users, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.events = events; this.users = users; this.passwords = passwords;
	}
	@BeforeEach void setUp() throws Exception {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class); users.deleteAll();
		users.save(new UserDocument("host-1", "Anfitrión", "host@example.com", passwords.encode("password12"), "Europe/Madrid"));
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Reunión", null, phoneGuest(101));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var headers = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf);
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}", headers);
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}

	@Test void generationReturnsReadyAndDoesNotModifyAnyDocumentOrVersion(CapturedOutput output) throws Exception {
		var before = snapshot();
		var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200); assertThat(result.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
		assertThat(read(result, "$.results[0].status")).isEqualTo("READY");
		assertThat(read(result, "$.results[0].guestId")).isEqualTo("guest-101");
		assertThat(message(result)).contains("Hola Invitada 101", "Anfitrión te invita a Reunión", "Inicio: 01/01/2027 18:00",
				"Fin: 01/01/2027 20:00", "Zona horaria: Europe/Madrid", "Lugar: Sala — Madrid", "https://gtog.example.com/r/" + token(101))
				.doesNotContain("a través de gtog", "no admite respuestas");
		assertThat(output.getAll()).doesNotContain(token(101), (String) read(result, "$.results[0].url"), message(result));
		assertUnchanged(before);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "CANCELLED", "FINISHED" })
	void nonPublishedEventsRejectGenerationAndDraftLinksStayHidden(EventStatus status) throws Exception {
		event = seed("event-id", "host-1", status, "Evento", null, phoneGuest(101));
		var before = snapshot();
		assertThat(generate("{}").statusCode()).isEqualTo(409);
		if (status == EventStatus.DRAFT) { assertThat(send("GET", "/api/invitations/" + token(101), "", Map.of()).statusCode()).isEqualTo(404); }
		assertUnchanged(before);
	}
	@Test void missingPhoneIsSkippedWithoutAnyWrite() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null,
				Guest.reconstitute("guest-101", "Ana", "ana@example.com", null, token(101), InvitationStatus.NOT_SENT, null, null, null));
		assertSkippedWithoutWrites("NO_PHONE");
	}
	@Test void answeredGuestsAreSkippedWithoutAnyWrite() throws Exception {
		responses.insert(new com.gtog.event.domain.model.GuestResponse(event.getId(), token(101), "yes-id", null, "response-private@example.com", NOW, NOW));
		assertSkippedWithoutWrites("ALREADY_ANSWERED");
	}
	@Test void aPhoneCorruptedDirectlyInMongoIsSkippedAndTheBatchContinues() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, phoneGuest(101), phoneGuest(102));
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().set("guests.0.phoneE164", "not-an-e164-phone"), EventDocument.class);
		var before = snapshot(); var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].reason")).isEqualTo("INVALID_PHONE"); assertThat(read(result, "$.results[1].status")).isEqualTo("READY");
		assertUnchanged(before);
	}
	@ParameterizedTest @ValueSource(strings = { "Boda de Ana & Luis #1 - C++ 100% 'cena' \"comillas\"", "Reunión mañana 🎉: café y ñ" })
	void urlEncodingRoundTripsTheExactPreviewIncludingReservedAndUnicodeCharacters(String title, CapturedOutput output) throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, title, null, phoneGuest(101));
		var before = snapshot(); var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		String message = message(result); String url = (String) read(result, "$.results[0].url");
		URI uri = URI.create(url); String encoded = uri.getRawQuery().substring("text=".length());
		assertThat(uri.getScheme()).isEqualTo("https"); assertThat(uri.getHost()).isEqualTo("wa.me");
		assertThat(uri.getPath()).isEqualTo("/" + phoneGuest(101).getPhoneE164().substring(1));
		assertThat(encoded).doesNotContain("+").contains("%20", "%0A");
		if (title.contains("+")) { assertThat(encoded).contains("%26", "%23", "%25", "%2B", "%22"); }
		String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
		assertThat(decoded).isEqualTo(message).contains(title, "\n");
		assertThat(decoded.indexOf(token(101))).isGreaterThanOrEqualTo(0).isEqualTo(decoded.lastIndexOf(token(101)));
		assertThat(output.getAll()).doesNotContain(token(101)); assertUnchanged(before);
	}
	@Test void onlineInvitationsNeverExposeRoomSecrets() throws Exception {
		var access = new OnlineAccess("Zoom", "https://secret.example.com/room", "room-secret", "password-secret", "instructions-secret", LinkVisibility.ALWAYS, null);
		Guest other = phoneGuest(102);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento online", access, phoneGuest(101), other);
		var before = snapshot(); var result = generate(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(message(result)).contains("Plataforma: Zoom");
		assertThat(result.body()).doesNotContain(access.url(), access.password(), access.roomId(), access.instructions(),
				other.getId(), other.getName(), other.getEmail(), other.getPhoneE164(), other.getPhoneE164().substring(1), other.getToken());
		assertUnchanged(before);
	}
	@Test void aFiveHundredCharacterTitleIsAbbreviated() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "a".repeat(500), null, phoneGuest(101));
		var before = snapshot(); var result = generate("{}");
		assertThat(message(result)).contains("te invita a " + "a".repeat(97) + "...\n").doesNotContain("a".repeat(98)); assertUnchanged(before);
	}
	@ParameterizedTest @ValueSource(strings = { "👨‍👩‍👧‍👦", "🇪🇸", "👍🏽" })
	void compoundEmojiAtTheTruncationBoundaryRemainWholeInTheHttpPreview(String emoji) throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "a".repeat(96) + emoji + "b".repeat(10), null, phoneGuest(101));
		var before = snapshot(); var result = generate("{}");
		assertThat(message(result)).contains("te invita a " + "a".repeat(96) + emoji + "...\n"); assertUnchanged(before);
	}
	@Test void anOverlongEncodedUrlSkipsOnlyThatGuest() throws Exception {
		Guest longName = Guest.reconstitute("guest-101", "👨‍👩‍👧‍👦".repeat(100), "ana@example.com", phoneGuest(101).getPhoneE164(), token(101), InvitationStatus.NOT_SENT, null, null, null);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, longName, phoneGuest(102));
		var before = snapshot(); var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].reason")).isEqualTo("MESSAGE_TOO_LONG"); assertThat(read(result, "$.results[1].status")).isEqualTo("READY");
		assertThat(JsonPath.parse(result.body()).read("$.results[0]", Map.class)).doesNotContainKeys("url", "message"); assertUnchanged(before);
	}
	@Test void anEmojiTitleCanMakeEveryGuestMessageTooLong() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "👨‍👩‍👧‍👦".repeat(100), null, phoneGuest(101), phoneGuest(102));
		var before = snapshot(); var result = generate("{}");
		assertThat(read(result, "$.results[*].status")).isEqualTo(List.of("SKIPPED", "SKIPPED"));
		assertThat(read(result, "$.results[*].reason")).isEqualTo(List.of("MESSAGE_TOO_LONG", "MESSAGE_TOO_LONG"));
		assertUnchanged(before);
	}
	@Test void emptyIdsAreRejectedBeforeAnyProcessing() throws Exception { assertInvalidWithoutWrites("{\"guestIds\":[]}", 422); }
	@ParameterizedTest @ValueSource(ints = { 2000, 2001 })
	void theFullEncodedUrlIsAcceptedExactlyAtTheConservativeBoundary(int targetLength) throws Exception {
		Guest original = phoneGuest(101);
		Guest shortName = Guest.reconstitute(original.getId(), "A", original.getEmail(), original.getPhoneE164(), original.getToken(), InvitationStatus.NOT_SENT, null, null, null);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, shortName);
		int initialLength = ((String) read(generate("{}"), "$.results[0].url")).length();
		String emoji = "👨‍👩‍👧‍👦";
		int encodedEmojiLength = java.net.URLEncoder.encode(emoji, StandardCharsets.UTF_8).length();
		int count = (1902 - initialLength + encodedEmojiLength - 1) / encodedEmojiLength;
		int padding = targetLength - initialLength - count * encodedEmojiLength;
		assertThat(padding).isBetween(0, 99);
		Guest adjusted = Guest.reconstitute(original.getId(), "A" + "b".repeat(padding), original.getEmail(), original.getPhoneE164(), original.getToken(), InvitationStatus.NOT_SENT, null, null, null);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento" + emoji.repeat(count), null, adjusted);
		var before = snapshot(); var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		if (targetLength == 2000) {
			assertThat(read(result, "$.results[0].status")).isEqualTo("READY");
			assertThat(((String) read(result, "$.results[0].url")).length()).isEqualTo(2000);
		}
		else {
			assertThat(read(result, "$.results[0].status")).isEqualTo("SKIPPED");
			assertThat(read(result, "$.results[0].reason")).isEqualTo("MESSAGE_TOO_LONG");
		}
		assertUnchanged(before);
	}
	@Test void duplicateIdsAreRejectedBeforeAnyProcessing() throws Exception { assertInvalidWithoutWrites(ids("guest-101", "guest-101"), 422); }
	@Test void aForeignGuestRejectsTheWholeBatchBeforeAnyProcessing() throws Exception {
		seed("other-event", "host-1", EventStatus.PUBLISHED, "Otro", null, phoneGuest(202));
		assertInvalidWithoutWrites(ids("guest-101", "guest-202"), 404);
	}
	@Test void anotherHostsEventReturns404WithoutAnyWrite() throws Exception {
		event = seed("event-id", "other-host", EventStatus.PUBLISHED, "Ajeno", null, phoneGuest(101)); assertInvalidWithoutWrites("{}", 404);
	}
	@Test void theIndependentMaximumRejectsOneHundredAndOneGuests() throws Exception {
		Guest[] guests = IntStream.range(101, 202).mapToObj(WhatsAppLinksIntegrationTest::phoneGuest).toArray(Guest[]::new);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, guests); assertInvalidWithoutWrites("{}", 422);
	}
	@Test void exactlyOneHundredLinksAreAllowedAndReadResponsesOnce() throws Exception {
		Guest[] guests = IntStream.range(101, 201).mapToObj(WhatsAppLinksIntegrationTest::phoneGuest).toArray(Guest[]::new);
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, guests);
		var before = snapshot(); var result = generate("{}");
		assertThat(result.statusCode()).isEqualTo(200); assertThat((List<?>) read(result, "$.results")).hasSize(100);
		verify(responses, times(1)).findByEventIds(Set.of(event.getId()));
		verifyNoMoreInteractions(responses); assertUnchanged(before);
	}
	@Test void explicitSelectionPreservesItsOrder() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, phoneGuest(101), phoneGuest(102));
		var before = snapshot(); var result = generate(ids("guest-102", "guest-101"));
		assertThat(read(result, "$.results[*].guestId")).isEqualTo(List.of("guest-102", "guest-101")); assertUnchanged(before);
	}
	@Test void anAbsentBodySelectsAllWithoutWriting() throws Exception {
		var before = snapshot(); assertThat(generate("").statusCode()).isEqualTo(200); assertUnchanged(before);
	}
	@ParameterizedTest @EnumSource(value = InvitationStatus.class, names = { "SENT", "OPENED" })
	void generationPreservesPreviouslyRecordedSendAndOpenHistory(InvitationStatus status) throws Exception {
		Guest old = phoneGuest(101);
		Guest prior = Guest.reconstitute(old.getId(), old.getName(), old.getEmail(), old.getPhoneE164(), old.getToken(), status, DeliveryChannel.EMAIL,
				NOW.minusSeconds(100), status == InvitationStatus.OPENED ? NOW.minusSeconds(50) : null, 2, NOW.minusSeconds(100));
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, "Evento", null, prior);
		var before = snapshot(); assertThat(generate("{}").statusCode()).isEqualTo(200); assertUnchanged(before);
	}
	@Test void authenticationAndCsrfAreRequired() throws Exception {
		var before = snapshot();
		assertThat(send("POST", path(), "{}", Map.of()).statusCode()).isEqualTo(403);
		assertThat(send("POST", path(), "{}", Map.of("Cookie", auth.get("Cookie"), "Content-Type", "application/json")).statusCode()).isEqualTo(403);
		var anonymous = new HashMap<>(auth); anonymous.put("Cookie", "XSRF-TOKEN=" + auth.get("X-XSRF-TOKEN"));
		assertThat(send("POST", path(), "{}", anonymous).statusCode()).isEqualTo(401); assertUnchanged(before);
	}
	@Test void openApiDocumentsGenerationAndItsErrors() throws Exception {
		var result = send("GET", "/v3/api-docs", "", Map.of()); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(result.body()).read("$.paths['/api/events/{id}/invitations/whatsapp-links'].post.responses", Map.class))
				.containsKeys("200", "401", "403", "404", "409", "422");
	}

	private static Guest phoneGuest(int id) {
		return Guest.reconstitute("guest-" + id, "Invitada " + id, "guest" + id + "@example.com", "+346" + String.format("%08d", id), token(id), InvitationStatus.NOT_SENT, null, null, null);
	}
	private Event seed(String id, String hostId, EventStatus status, String title, OnlineAccess access, Guest... guests) {
		var original = com.gtog.event.InvitationTestFixtures.event(status, null);
		var current = events.findById(id);
		return events.save(Event.reconstituteBuilder().id(id).hostId(hostId).title(title).description("Descripción")
				.startsAt(original.getStartsAt()).endsAt(original.getEndsAt()).timeZone(original.getTimeZone()).status(status)
				.modality(access == null ? Modality.IN_PERSON : Modality.ONLINE).venue(access == null ? new Venue("Sala", "Madrid", 0.0, 0.0, "place", null) : null)
				.onlineAccess(access).responseOptions(original.getResponseOptions()).allowComment(true).allowResponseChange(true)
				.guests(List.of(guests)).version(current.map(Event::getVersion).orElse(null)).build());
	}
	private Map<String, List<Document>> snapshot() {
		var snapshot = new LinkedHashMap<String, List<Document>>();
		for (String collection : List.of("events", "responses", "users")) { snapshot.put(collection, mongo.getCollection(collection).find().into(new ArrayList<>())); }
		return snapshot;
	}
	private void assertUnchanged(Map<String, List<Document>> before) {
		assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(reservations, quotas);
	}
	private void assertSkippedWithoutWrites(String reason) throws Exception {
		var before = snapshot(); var result = generate("{}"); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].status")).isEqualTo("SKIPPED"); assertThat(read(result, "$.results[0].reason")).isEqualTo(reason);
		assertThat(JsonPath.parse(result.body()).read("$.results[0]", Map.class)).doesNotContainKeys("url", "message"); assertUnchanged(before);
	}
	private void assertInvalidWithoutWrites(String body, int status) throws Exception {
		var before = snapshot(); assertThat(generate(body).statusCode()).isEqualTo(status); verifyNoInteractions(responses); assertUnchanged(before);
	}
	private static Object read(HttpResponse<String> result, String path) { return JsonPath.parse(result.body()).read(path); }
	private static String message(HttpResponse<String> result) { return (String) read(result, "$.results[0].message"); }
	private static String ids(String... ids) { return "{\"guestIds\":[\"" + String.join("\",\"", ids) + "\"]}"; }
	private String path() { return "/api/events/" + event.getId() + "/invitations/whatsapp-links"; }
	private HttpResponse<String> generate(String body) throws Exception { return send("POST", path(), body, auth); }
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream()).filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
