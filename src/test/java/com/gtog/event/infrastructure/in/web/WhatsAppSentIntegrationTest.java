package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
		properties = "app.invitations.max-whatsapp-links-per-request=100")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WhatsAppSentIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final UserMongoRepository users;
	private final PasswordEncoder passwords;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoBean private Clock clock;
	@MockitoSpyBean private EventRepositoryPort events;
	@MockitoSpyBean private GuestWhatsAppConfirmationPort confirmations;
	@MockitoSpyBean private GuestResponseRepositoryPort responses;
	@MockitoSpyBean private GuestEmailSendReservationPort reservations;
	@MockitoSpyBean private HostSendQuotaPort quotas;
	private Event event;
	private Map<String, String> auth;

	WhatsAppSentIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			UserMongoRepository users, PasswordEncoder passwords) {
		this.port = port; this.mongo = mongo; this.users = users; this.passwords = passwords;
	}
	@BeforeEach void setUp() throws Exception {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class); users.deleteAll();
		when(clock.instant()).thenReturn(NOW);
		users.save(new UserDocument("host-1", "Anfitrión", "host@example.com", passwords.encode("password12"), "Europe/Madrid"));
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, InvitationStatus.NOT_SENT, null));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}",
				Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf));
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
		clearInvocations(events);
	}

	@Test void aNotSentGuestIsMarkedWithoutGeneratingFirstAndThePanelShowsWhatsApp() throws Exception {
		long version = current().getVersion();
		var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(result.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
		assertThat(read(result, "$.results[0].status")).isEqualTo("MARKED");
		assertThat(read(result, "$.results[0].guestId")).isEqualTo("guest-101");
		assertSent(InvitationStatus.SENT); assertThat(current().getVersion()).isEqualTo(version + 1);
		var panel = send("GET", "/api/events/event-id/dashboard", "", auth);
		assertThat(panel.statusCode()).isEqualTo(200);
		assertThat(read(panel, "$.guests[0].invitationStatus")).isEqualTo("SENT");
		assertThat(read(panel, "$.guests[0].deliveryChannel")).isEqualTo("WHATSAPP");
		verifyNoInteractions(reservations, quotas); verify(events, never()).save(any());
	}
	@Test void openingFirstThenConfirmingCompletesSendHistoryWithoutDowngradingOpened() throws Exception {
		long version = current().getVersion(); assertThat(open().statusCode()).isEqualTo(200);
		var result = confirm(ids("guest-101")); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].status")).isEqualTo("MARKED");
		assertSent(InvitationStatus.OPENED); assertThat(currentGuest().getOpenedAt()).isEqualTo(NOW);
		assertThat(current().getVersion()).isEqualTo(version + 2);
	}
	@Test void confirmingFirstThenOpeningPreservesSendHistory() throws Exception {
		long version = current().getVersion(); assertThat(confirm(ids("guest-101")).statusCode()).isEqualTo(200);
		when(clock.instant()).thenReturn(NOW.plusSeconds(30)); assertThat(open().statusCode()).isEqualTo(200);
		assertSent(InvitationStatus.OPENED); assertThat(currentGuest().getOpenedAt()).isEqualTo(NOW.plusSeconds(30));
		assertThat(current().getVersion()).isEqualTo(version + 2);
	}
	@Test void anOpenedGuestWithSendHistoryIsNeverDowngradedOrRewritten() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, InvitationStatus.OPENED, NOW.minusSeconds(60)));
		var before = snapshot(); var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(read(result, "$.results[0].status")).isEqualTo("ALREADY_OPENED"); assertThat(snapshot()).isEqualTo(before);
	}
	@Test void repeatingASentConfirmationPreservesTheFirstTimestampAndVersion() throws Exception {
		assertThat(confirm(ids("guest-101")).statusCode()).isEqualTo(200); var before = snapshot();
		when(clock.instant()).thenReturn(NOW.plusSeconds(60)); var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200); assertThat(read(result, "$.results[0].status")).isEqualTo("ALREADY_SENT");
		assertThat(snapshot()).isEqualTo(before); assertSent(InvitationStatus.SENT);
	}
	@Test void repeatingAnOpenedConfirmationPreservesTheFirstTimestampAndVersion() throws Exception {
		assertThat(open().statusCode()).isEqualTo(200); assertThat(confirm(ids("guest-101")).statusCode()).isEqualTo(200);
		var before = snapshot(); when(clock.instant()).thenReturn(NOW.plusSeconds(60));
		var result = confirm(ids("guest-101")); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].status")).isEqualTo("ALREADY_OPENED"); assertThat(snapshot()).isEqualTo(before);
	}
	@Test void aPriorEmailSendKeepsItsChannelAndTimestamp() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, InvitationStatus.SENT, NOW.minusSeconds(60)));
		var before = snapshot(); var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200); assertThat(read(result, "$.results[0].status")).isEqualTo("ALREADY_SENT");
		assertThat(snapshot()).isEqualTo(before);
	}
	@ParameterizedTest @EnumSource(InvitationStatus.class)
	void aGuestWithoutPhoneIsNotMarkedAndTheEntireDocumentIsIntact(InvitationStatus status) throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, status, null));
		mongo.updateFirst(eventQuery(), new Update().unset("guests.0.phoneE164"), EventDocument.class);
		var before = snapshot(); var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200); assertThat(read(result, "$.results[0].status")).isEqualTo("NO_PHONE");
		assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(reservations, quotas);
	}
	@ParameterizedTest @ValueSource(strings = { "null", "\"\"", "\"  \"" })
	void aNullEmptyOrBlankStoredPhoneCannotMatchEitherUpdate(String phone) throws Exception {
		mongo.updateFirst(eventQuery(), new Update().set("guests.0.phoneE164", phone.equals("null") ? null : phone.substring(1, phone.length() - 1)), EventDocument.class);
		var before = snapshot(); var result = confirm(ids("guest-101"));
		assertThat(result.statusCode()).isEqualTo(200); assertThat(read(result, "$.results[0].status")).isEqualTo("NO_PHONE");
		assertThat(snapshot()).isEqualTo(before);
	}
	@Test void twoConcurrentConfirmationsProduceOneTransitionAndTwoSuccessfulResponses() throws Exception {
		long version = current().getVersion(); var result = parallel(false);
		assertThat(result).extracting(HttpResponse::statusCode).containsExactly(200, 200);
		assertThat(result.stream().map(r -> read(r, "$.results[0].status")).toList()).containsExactlyInAnyOrder("MARKED", "ALREADY_SENT");
		assertSent(InvitationStatus.SENT); assertThat(current().getVersion()).isEqualTo(version + 1);
	}
	@Test void aConcurrentOpeningAndConfirmationKeepOpenedAndSendHistory() throws Exception {
		long version = current().getVersion(); var result = parallel(true);
		assertThat(result).extracting(HttpResponse::statusCode).containsExactly(200, 200);
		assertSent(InvitationStatus.OPENED); assertThat(currentGuest().getOpenedAt()).isEqualTo(NOW);
		assertThat(current().getVersion()).isEqualTo(version + 2);
	}
	@Test void savingTheVersionBeforeConfirmationReturns409AndDoesNotOverwriteIt() throws Exception {
		Event stale = current(); assertThat(confirm(ids("guest-101")).statusCode()).isEqualTo(200);
		var before = snapshot(); doReturn(Optional.of(stale)).when(events).findById("event-id");
		var result = send("POST", "/api/events/event-id/cancel", "{}", auth);
		assertThat(result.statusCode()).isEqualTo(409); assertThat(result.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
		assertThat(snapshot()).isEqualTo(before);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "CANCELLED", "FINISHED" })
	void nonPublishedStatesRejectConfirmationWithoutWriting(EventStatus status) throws Exception {
		event = seed("event-id", "host-1", status, guest(101, InvitationStatus.NOT_SENT, null)); assertInvalid(ids("guest-101"), 409);
	}
	@ParameterizedTest @ValueSource(strings = { "{}", "{\"guestIds\":null}", "{\"guestIds\":[]}", "{\"guestIds\":[\"guest-101\",\"guest-101\"]}", "{\"guestIds\":[null]}", "{\"guestIds\":[\" \" ]}" })
	void invalidSelectionIsRejectedBeforeAnyWrite(String body) throws Exception { assertInvalid(body, 422); }
	@Test void aMissingBodyIs400WithoutAnyWrite() throws Exception { assertInvalid("", 400); }
	@Test void aForeignGuestRejectsTheEntireSelectionBeforeMarkingAnyone() throws Exception {
		seed("other-event", "host-1", EventStatus.PUBLISHED, guest(202, InvitationStatus.NOT_SENT, null));
		assertInvalid(ids("guest-101", "guest-202"), 404);
	}
	@Test void anotherHostsEventIs404WithoutAnyWrite() throws Exception {
		event = seed("event-id", "other-host", EventStatus.PUBLISHED, guest(101, InvitationStatus.NOT_SENT, null)); assertInvalid(ids("guest-101"), 404);
	}
	@Test void exceedingTheMaximumIs422BeforeAnyWrite() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, IntStream.range(101, 202).mapToObj(id -> guest(id, InvitationStatus.NOT_SENT, null)).toArray(Guest[]::new));
		assertInvalid(ids(event.getGuests().stream().map(Guest::getId).toArray(String[]::new)), 422);
	}
	@Test void aGuestRemovedAfterValidationReturnsNotFoundAndTheBatchContinues() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, InvitationStatus.NOT_SENT, null), guest(102, InvitationStatus.NOT_SENT, null));
		doAnswer(invocation -> {
			mongo.updateFirst(eventQuery(), new Update().pull("guests", new Document("id", "guest-101")).inc("version", 1), EventDocument.class);
			return invocation.callRealMethod();
		}).when(confirmations).confirm("event-id", "host-1", "guest-101", NOW);
		var result = confirm(ids("guest-101", "guest-102")); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[*].status")).isEqualTo(List.of("NOT_FOUND", "MARKED"));
		assertThat(current().getGuest("guest-102").getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
	}
	@Test void cancellationMidBatchReturns409AndKeepsEarlierMarks() throws Exception {
		event = seed("event-id", "host-1", EventStatus.PUBLISHED, guest(101, InvitationStatus.NOT_SENT, null), guest(102, InvitationStatus.NOT_SENT, null));
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			mongo.updateFirst(eventQuery(), new Update().set("status", "CANCELLED").inc("version", 1), EventDocument.class);
			return result;
		}).when(confirmations).confirm("event-id", "host-1", "guest-101", NOW);
		assertThat(confirm(ids("guest-101", "guest-102")).statusCode()).isEqualTo(409);
		assertThat(current().getGuest("guest-101").getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
		assertThat(current().getGuest("guest-102").getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
	}
	@Test void confirmationDoesNotReadResponsesOrUseEmailReservationsOrQuotas() throws Exception {
		assertThat(confirm(ids("guest-101")).statusCode()).isEqualTo(200); verifyNoInteractions(responses, reservations, quotas);
	}
	@Test void authenticationAndCsrfAreRequired() throws Exception {
		var before = snapshot();
		assertThat(send("POST", path(), ids("guest-101"), Map.of()).statusCode()).isEqualTo(403);
		assertThat(send("POST", path(), ids("guest-101"), Map.of("Cookie", auth.get("Cookie"), "Content-Type", "application/json")).statusCode()).isEqualTo(403);
		var anonymous = new HashMap<>(auth); anonymous.put("Cookie", "XSRF-TOKEN=" + auth.get("X-XSRF-TOKEN"));
		assertThat(send("POST", path(), ids("guest-101"), anonymous).statusCode()).isEqualTo(401); assertThat(snapshot()).isEqualTo(before);
	}
	@Test void openApiDocumentsConfirmationAndItsErrors() throws Exception {
		var result = send("GET", "/v3/api-docs", "", Map.of()); assertThat(result.statusCode()).isEqualTo(200);
		assertThat(JsonPath.parse(result.body()).read("$.paths['/api/events/{id}/invitations/whatsapp-sent'].post.responses", Map.class))
				.containsKeys("200", "400", "401", "403", "404", "409", "422");
	}

	private static Guest guest(int id, InvitationStatus status, java.time.Instant sentAt) {
		return Guest.reconstitute("guest-" + id, "Invitada " + id, "guest" + id + "@example.com", "+346" + String.format("%08d", id), token(id),
				status, sentAt == null ? null : DeliveryChannel.EMAIL, sentAt, status == InvitationStatus.OPENED ? NOW.minusSeconds(120) : null);
	}
	private Event seed(String id, String hostId, EventStatus status, Guest... guests) {
		var original = com.gtog.event.InvitationTestFixtures.event(status, null, guests);
		return events.save(Event.reconstituteBuilder().id(id).hostId(hostId).title(original.getTitle())
				.startsAt(original.getStartsAt()).endsAt(original.getEndsAt()).timeZone(original.getTimeZone()).status(status)
				.modality(Modality.IN_PERSON).venue(original.getVenue()).responseOptions(original.getResponseOptions()).allowComment(true).allowResponseChange(true)
				.guests(List.of(guests)).version(events.findById(id).map(Event::getVersion).orElse(null)).build());
	}
	private Event current() { return events.findById("event-id").orElseThrow(); }
	private Guest currentGuest() { return current().getGuest("guest-101"); }
	private Query eventQuery() { return Query.query(Criteria.where("id").is("event-id")); }
	private Map<String, List<Document>> snapshot() {
		var result = new LinkedHashMap<String, List<Document>>();
		for (String collection : List.of("events", "responses", "users")) { result.put(collection, mongo.getCollection(collection).find().into(new ArrayList<>())); }
		return result;
	}
	private void assertInvalid(String body, int status) throws Exception {
		var before = snapshot(); assertThat(confirm(body).statusCode()).isEqualTo(status); assertThat(snapshot()).isEqualTo(before);
		verifyNoInteractions(confirmations, responses, reservations, quotas);
	}
	private void assertSent(InvitationStatus status) {
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(status); assertThat(currentGuest().getDeliveryChannel()).isEqualTo(DeliveryChannel.WHATSAPP);
		assertThat(currentGuest().getSentAt()).isEqualTo(NOW);
	}
	private List<HttpResponse<String>> parallel(boolean open) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { barrier.await(); return confirm(ids("guest-101")); });
			var second = executor.submit(() -> { barrier.await(); return open ? open() : confirm(ids("guest-101")); });
			return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
		}
	}
	private static Object read(HttpResponse<String> result, String path) { return JsonPath.parse(result.body()).read(path); }
	private static String ids(String... ids) { return "{\"guestIds\":[\"" + String.join("\",\"", ids) + "\"]}"; }
	private String path() { return "/api/events/event-id/invitations/whatsapp-sent"; }
	private HttpResponse<String> confirm(String body) throws Exception { return send("POST", path(), body, auth); }
	private HttpResponse<String> open() throws Exception { return send("GET", "/api/invitations/" + token(101), "", Map.of()); }
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream()).filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
}
