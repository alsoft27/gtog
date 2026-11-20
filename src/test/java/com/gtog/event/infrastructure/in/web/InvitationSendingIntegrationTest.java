package com.gtog.event.infrastructure.in.web;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.out.*;
import com.gtog.event.infrastructure.out.mail.CapturingEmailSenderAdapter;
import com.gtog.event.infrastructure.out.persistence.*;
import com.gtog.user.infrastructure.out.persistence.*;
import com.jayway.jsonpath.JsonPath;

import static com.gtog.event.GuestTestFixtures.*;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@com.gtog.AuthBypassDisabled
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class InvitationSendingIntegrationTest {
	private final int port;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final UserMongoRepository users;
	private final PasswordEncoder passwords;
	private final CapturingEmailSenderAdapter mail;
	private final HttpClient client = HttpClient.newHttpClient();
	@MockitoBean private Clock clock;
	@MockitoSpyBean private GuestEmailSendReservationPort guestReservations;
	@MockitoSpyBean private GuestResponseRepositoryPort responses;
	private Event event;
	private Map<String, String> auth;

	InvitationSendingIntegrationTest(@Value("${local.server.port}") int port, MongoTemplate mongo,
			EventRepositoryPort events, UserMongoRepository users, PasswordEncoder passwords, CapturingEmailSenderAdapter mail) {
		this.port = port; this.mongo = mongo; this.events = events; this.users = users; this.passwords = passwords; this.mail = mail;
	}

	@BeforeEach void setUp() throws Exception {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class); users.deleteAll();
		mail.reset(); when(clock.instant()).thenReturn(NOW);
		users.save(com.gtog.UserTestFixtures.verified("host-1", "Anfitrión", "host@example.com", passwords.encode("password12"), "Europe/Madrid"));
		event = events.save(event("event-id", EventStatus.PUBLISHED, null, guest(101)));
		String csrf = cookie(send("GET", "/actuator/health", "", Map.of()), "XSRF-TOKEN");
		var headers = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf, "X-XSRF-TOKEN", csrf);
		var login = send("POST", "/api/auth/login", "{\"email\":\"host@example.com\",\"password\":\"password12\"}", headers);
		assertThat(login.statusCode()).isEqualTo(200);
		auth = Map.of("Content-Type", "application/json", "Cookie", "XSRF-TOKEN=" + csrf + "; JSESSIONID=" + cookie(login, "JSESSIONID"), "X-XSRF-TOKEN", csrf);
	}

	@Test void sendsAndRecordsOneInvitationAndReturnsTheFinalDailyQuota() throws Exception {
		var response = batch("{}");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(read(response, "$.results[0].status")).isEqualTo("SENT");
		assertThat(read(response, "$.results[0].recorded")).isEqualTo(true);
		assertThat(read(response, "$.dailyLimit.limit")).isEqualTo(100);
		assertThat(read(response, "$.dailyLimit.remaining")).isEqualTo(99);
		assertThat(read(response, "$.dailyLimit.resetsAt")).isEqualTo(NOW.plusSeconds(86400).toString());
		assertThat(mail.messages()).hasSize(1);
		assertThat(response.body()).doesNotContain(currentGuest().getToken(), currentGuest().getEmail(), "respuesta-privada@example.com");
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
		assertThat(currentGuest().getDeliveryChannel()).isEqualTo(DeliveryChannel.EMAIL);
		assertThat(currentGuest().getSentAt()).isEqualTo(NOW);
	}

	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "CANCELLED", "FINISHED" })
	void rejectsEveryNonPublishedStateWithoutReservingOrSending(EventStatus status) throws Exception {
		event = events.save(event("event-id", status, event.getVersion(), event.getGuests().getFirst()));
		assertThat(batch("{}").statusCode()).isEqualTo(409); assertUntouched();
	}

	@Test void anEmptyListReturns422WithoutAnySideEffects() throws Exception {
		assertThat(batch("{\"guestIds\":[]}").statusCode()).isEqualTo(422); assertUntouched();
	}
	@Test void duplicateIdsReturn422WithoutAnySideEffects() throws Exception {
		String id = currentGuest().getId();
		assertThat(batch("{\"guestIds\":[\"" + id + "\",\"" + id + "\"]}").statusCode()).isEqualTo(422); assertUntouched();
	}
	@Test void aGuestFromAnotherEventReturns404WithoutAnySideEffects() throws Exception {
		var other = events.save(event("other-event", EventStatus.PUBLISHED, null, guest(102)));
		assertThat(batch("{\"guestIds\":[\"" + currentGuest().getId() + "\",\"" + other.getGuests().getFirst().getId() + "\"]}").statusCode()).isEqualTo(404);
		assertUntouched();
	}
	@Test void theBatchMaximumIsCheckedBeforeAnyReservation() throws Exception {
		seedGuests(21);
		assertThat(batch("{}").statusCode()).isEqualTo(422); assertUntouched();
	}
	@Test void anotherHostsEventReturns404WithoutAnySideEffects() throws Exception {
		Event ownedByOther = Event.reconstituteBuilder().id(event.getId()).hostId("other-host").title("Event")
				.startsAt(event.getStartsAt()).endsAt(event.getEndsAt()).timeZone(event.getTimeZone()).modality(event.getModality())
				.status(EventStatus.PUBLISHED).venue(event.getVenue()).responseOptions(event.getResponseOptions()).guests(event.getGuests()).version(event.getVersion()).build();
		event = events.save(ownedByOther);
		assertThat(batch("{}").statusCode()).isEqualTo(404); assertUntouched();
	}
	@Test void theEndpointRequiresAuthenticationAndRealCsrf() throws Exception {
		assertThat(send("POST", path(event.getId()), "{}", Map.of()).statusCode()).isEqualTo(403);
		assertThat(send("POST", path(event.getId()), "{}", Map.of("Cookie", auth.get("Cookie"), "Content-Type", "application/json")).statusCode()).isEqualTo(403);
		var anonymous = new HashMap<>(auth); anonymous.put("Cookie", "XSRF-TOKEN=" + auth.get("X-XSRF-TOKEN"));
		assertThat(send("POST", path(event.getId()), "{}", anonymous).statusCode()).isEqualTo(401); assertUntouched();
	}
	@Test void missingEmailAndAnExistingResponseAreSkippedWithoutConsumingQuota() throws Exception {
		Guest noEmail = Guest.create("Sin correo", null, "+34612345678", token(110), phone -> phone);
		Guest answered = guest(111);
		event = events.save(event("event-id", EventStatus.PUBLISHED, event.getVersion(), noEmail, answered));
		answer(answered);
		var response = batch("{}");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(read(response, "$.results[*].reason")).isEqualTo(List.of("NO_EMAIL", "ALREADY_ANSWERED"));
		assertThat(read(response, "$.dailyLimit.remaining")).isEqualTo(100);
		assertThat(read(response, "$.dailyLimit.resetsAt")).isNull(); assertUntouched();
	}
	@Test void anOpenedGuestCanReceiveEmailWithoutDowngradingItsInvitation() throws Exception {
		assertThat(batch("{}").statusCode()).isEqualTo(200);
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().set("guests.0.invitationStatus", "OPENED").set("guests.0.openedAt", NOW.minusSeconds(60)), EventDocument.class);
		when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
		assertThat(batch("{}").statusCode()).isEqualTo(200);
		assertThat(mail.messages()).hasSize(2); assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(2); assertThat(currentGuest().getSentAt()).isEqualTo(NOW);
		assertThat(currentGuest().getOpenedAt()).isEqualTo(NOW.minusSeconds(60));
	}
	@Test void capturedMessagesNeverContainRoomSecretsOrOtherGuestsContacts() throws Exception {
		Guest first = guest(101), other = guest(102);
		mongo.remove(new Query(), EventDocument.class);
		event = events.save(com.gtog.event.InvitationTestFixtures.event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, first, other));
		assertThat(batch("{\"guestIds\":[\"" + first.getId() + "\"]}").statusCode()).isEqualTo(200);
		var captured = mail.messages().getFirst();
		assertThat(captured.recipient()).isEqualTo(first.getEmail());
		assertThat(captured.body()).contains("Plataforma: Zoom").doesNotContain(com.gtog.event.InvitationTestFixtures.URL,
				com.gtog.event.InvitationTestFixtures.PASSWORD, other.getEmail(), other.getToken(), other.getName());
		assertThat(captured.body()).contains("Inicio: 01/01/2027 18:00", "Fin: 01/01/2027 20:00", "Zona horaria: Europe/Madrid",
				"Enviado por Anfitrión a través de gtog. Si no esperabas esta invitación, ignórala.");
	}
	@Test void aTitleWithLineBreaksProducesASingleLineSubject() throws Exception {
		Event changed = Event.reconstituteBuilder().id(event.getId()).hostId("host-1").title("Mañana\r\nFiesta 🎉")
				.startsAt(event.getStartsAt()).endsAt(event.getEndsAt()).timeZone(event.getTimeZone()).modality(event.getModality())
				.status(EventStatus.PUBLISHED).venue(event.getVenue()).responseOptions(event.getResponseOptions()).guests(event.getGuests()).version(event.getVersion()).build();
		event = events.save(changed);
		assertThat(batch("{}").statusCode()).isEqualTo(200);
		assertThat(mail.messages().getFirst().subject()).isEqualTo("Mañana  Fiesta 🎉").doesNotContain("\r", "\n");
	}
	@Test void aRejectedRecipientFailsAndCompensatesBothReservations() throws Exception {
		mail.enqueueResult(EmailDeliveryResult.failure(EmailSendFailureCode.RECIPIENT_REJECTED));
		var result = batch("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].status")).isEqualTo("FAILED");
		assertThat(read(result, "$.results[0].code")).isEqualTo("RECIPIENT_REJECTED");
		assertQuotaAndGuestCountersAreZero(); assertThat(mail.messages()).isEmpty();
	}
	@ParameterizedTest @EnumSource(value = EmailSendFailureCode.class, names = { "PROVIDER_UNAVAILABLE", "EMAIL_NOT_CONFIGURED" })
	void threeProviderFailuresCompensateAndStopTheRemainingThreeGuests(EmailSendFailureCode code) throws Exception {
		seedGuests(6);
		for (int i = 0; i < 6; i++) { mail.enqueueResult(EmailDeliveryResult.failure(code)); }
		var result = batch("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[*].status")).isEqualTo(List.of("FAILED", "FAILED", "FAILED", "SKIPPED", "SKIPPED", "SKIPPED"));
		assertThat(read(result, "$.results[0].code")).isEqualTo(code.name());
		assertThat(read(result, "$.results[3].reason")).isEqualTo("PROVIDER_UNAVAILABLE");
		assertThat(read(result, "$.results[4].reason")).isEqualTo("PROVIDER_UNAVAILABLE");
		assertThat(read(result, "$.results[5].reason")).isEqualTo("PROVIDER_UNAVAILABLE");
		assertThat(mail.attempts()).hasSize(3); assertThat(mail.messages()).isEmpty(); assertQuotaAndGuestCountersAreZero();
	}
	@Test void recipientRejectionBreaksTheStreakOfProviderFailures() throws Exception {
		seedGuests(6);
		for (var code : List.of(EmailSendFailureCode.PROVIDER_UNAVAILABLE, EmailSendFailureCode.PROVIDER_UNAVAILABLE,
				EmailSendFailureCode.RECIPIENT_REJECTED, EmailSendFailureCode.PROVIDER_UNAVAILABLE, EmailSendFailureCode.PROVIDER_UNAVAILABLE)) {
			mail.enqueueResult(EmailDeliveryResult.failure(code));
		}
		var result = batch("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[5].status")).isEqualTo("SENT");
		assertThat(mail.attempts()).hasSize(6); assertThat(mail.messages()).hasSize(1);
	}
	@Test void exhaustedDailyQuotaSkipsTheWholeRemainderAndRestoresTheGuest() throws Exception {
		seedGuests(3); quota(100);
		var result = batch("{}");
		assertThat(read(result, "$.results[*].reason")).isEqualTo(List.of("DAILY_LIMIT", "DAILY_LIMIT", "DAILY_LIMIT"));
		assertThat(read(result, "$.dailyLimit.remaining")).isEqualTo(0);
		assertThat(mail.attempts()).isEmpty(); assertThat(currentGuest().getEmailSendCount()).isZero();
	}
	@Test void simultaneousRequestsSendExactlyOneMessageToTheGuest() throws Exception {
		var pair = parallel(event.getId(), event.getId());
		assertThat(pair).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
		assertThat(mail.messages()).hasSize(1);
		assertThat(pair.stream().map(response -> read(response, "$.results[0].status")).toList()).containsExactlyInAnyOrder("SENT", "SKIPPED");
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(1);
		assertThat(users.findById("host-1").orElseThrow().getEmailSendsInWindow()).isEqualTo(1);
	}
	@Test void theFourthSendIsSkippedEvenAfterTheMinimumInterval() throws Exception {
		for (int index = 0; index < 3; index++) {
			when(clock.instant()).thenReturn(NOW.plusSeconds(index * 3600L));
			assertThat(read(batch("{}"), "$.results[0].status")).isEqualTo("SENT");
		}
		when(clock.instant()).thenReturn(NOW.plusSeconds(10800));
		assertThat(read(batch("{}"), "$.results[0].reason")).isEqualTo("LIMIT_REACHED");
		assertThat(mail.messages()).hasSize(3); assertThat(currentGuest().getEmailSendCount()).isEqualTo(3);
		assertThat(users.findById("host-1").orElseThrow().getEmailSendsInWindow()).isEqualTo(3);
	}
	@Test void simultaneousBatchesWithOneRemainingHostPermitSendExactlyOneMessage() throws Exception {
		quota(99);
		Event other = events.save(event("other-event", EventStatus.PUBLISHED, null, guest(102)));
		var pair = parallel(event.getId(), other.getId());
		assertThat(pair).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
		assertThat(mail.messages()).hasSize(1);
		assertThat(users.findById("host-1").orElseThrow().getEmailSendsInWindow()).isEqualTo(100);
		int used = events.findById(event.getId()).orElseThrow().getGuests().getFirst().getEmailSendCount()
				+ events.findById(other.getId()).orElseThrow().getGuests().getFirst().getEmailSendCount();
		assertThat(used).isEqualTo(1);
	}
	@ParameterizedTest @ValueSource(booleans = { false, true })
	void acceptedEmailWithRecordingFailureKeepsReservationsAndReportsItWithoutLeakingSecrets(boolean throwsException, CapturedOutput output) throws Exception {
		if (throwsException) { doThrow(new IllegalStateException(currentGuest().getEmail() + currentGuest().getToken())).when(guestReservations).recordSent(any(), any()); }
		else { doReturn(false).when(guestReservations).recordSent(any(), any()); }
		var result = batch("{}");
		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(read(result, "$.results[0].status")).isEqualTo("SENT"); assertThat(read(result, "$.results[0].recorded")).isEqualTo(false);
		assertThat(mail.messages()).hasSize(1); assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(1);
		assertThat(users.findById("host-1").orElseThrow().getEmailSendsInWindow()).isEqualTo(1);
		assertThat(output.getAll()).contains("ERROR", "EMAIL_SENT_RECORDING_FAILED",
				throwsException ? IllegalStateException.class.getName() : EventConcurrentModificationException.class.getName(), "correlationId=")
				.doesNotContain(currentGuest().getEmail(), currentGuest().getToken());
		assertThat(read(batch("{}"), "$.results[0].reason")).isEqualTo("THROTTLED"); assertThat(mail.messages()).hasSize(1);
	}
	@Test void eligibilityUsesOneBatchReadAndNeverTheResponseEmail() throws Exception {
		seedGuests(3); Guest answered = event.getGuests().getFirst(); answer(answered);
		var result = batch("{}");
		assertThat(result.statusCode()).isEqualTo(200); verify(responses, times(1)).findByEventIds(Set.of(event.getId()));
		assertThat(mail.messages()).hasSize(2);
		assertThat(mail.attempts()).allSatisfy(message -> {
			assertThat(message.recipient()).isNotEqualTo("respuesta-privada@example.com");
			assertThat(message.body()).doesNotContain("respuesta-privada@example.com");
		});
	}
	@Test void aResponseAppearingAfterTheBatchReadIsRecheckedAndCompensated() throws Exception {
		Guest guest = currentGuest();
		doReturn(Optional.of(new com.gtog.event.domain.model.GuestResponse(event.getId(), guest.getToken(), event.getResponseOptions().getFirst().id(), null, null, NOW, NOW)))
				.when(responses).find(event.getId(), guest.getToken());
		var result = batch("{}");
		assertThat(read(result, "$.results[0].reason")).isEqualTo("ALREADY_ANSWERED");
		assertThat(mail.attempts()).isEmpty(); assertQuotaAndGuestCountersAreZero();
	}

	private void seedGuests(int count) {
		Guest[] guests = IntStream.range(101, 101 + count).mapToObj(index -> guest(index)).toArray(Guest[]::new);
		event = events.save(event("event-id", EventStatus.PUBLISHED, event.getVersion(), guests));
	}
	private void answer(Guest guest) {
		responses.insert(new com.gtog.event.domain.model.GuestResponse(event.getId(), guest.getToken(), event.getResponseOptions().getFirst().id(), null, "respuesta-privada@example.com", NOW, NOW));
	}
	private void quota(int count) {
		mongo.updateFirst(Query.query(Criteria.where("id").is("host-1")), new Update().set("emailSendsInWindow", count).set("windowStartedAt", NOW), UserDocument.class);
	}
	private Guest currentGuest() { return events.findById(event.getId()).orElseThrow().getGuests().getFirst(); }
	private void assertUntouched() {
		assertThat(mail.attempts()).isEmpty(); assertQuotaAndGuestCountersAreZero();
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(event.getVersion());
		assertThat(events.findByHostId("host-1").stream().flatMap(candidate -> candidate.getGuests().stream()).toList())
				.allSatisfy(guest -> { assertThat(guest.getEmailSendCount()).isZero(); assertThat(guest.getLastEmailSentAt()).isNull(); });
	}
	private void assertQuotaAndGuestCountersAreZero() {
		UserDocument host = users.findById("host-1").orElseThrow();
		assertThat(host.getEmailSendsInWindow()).isZero(); assertThat(host.getWindowStartedAt()).isNull();
		assertThat(events.findById(event.getId()).orElseThrow().getGuests()).allSatisfy(guest -> {
			assertThat(guest.getEmailSendCount()).isZero(); assertThat(guest.getLastEmailSentAt()).isNull();
		});
	}
	private static Object read(HttpResponse<String> response, String path) { return JsonPath.parse(response.body()).read(path); }
	private static String path(String eventId) { return "/api/events/" + eventId + "/invitations/send"; }
	private HttpResponse<String> batch(String body) throws Exception { return send("POST", path(event.getId()), body, auth); }
	private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)); headers.forEach(request::header);
		return client.send(request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static String cookie(HttpResponse<?> response, String name) {
		return response.headers().allValues("Set-Cookie").stream().flatMap(header -> HttpCookie.parse(header).stream())
				.filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
	}
	private List<HttpResponse<String>> parallel(String firstId, String secondId) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { barrier.await(); return send("POST", path(firstId), "{}", auth); });
			var second = executor.submit(() -> { barrier.await(); return send("POST", path(secondId), "{}", auth); });
			return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
		}
	}
}
