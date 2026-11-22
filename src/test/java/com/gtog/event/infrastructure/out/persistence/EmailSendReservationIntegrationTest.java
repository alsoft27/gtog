package com.gtog.event.infrastructure.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import com.gtog.event.application.EmailSendReservationService;
import com.gtog.event.application.GuestService;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.UpdateGuestCommand;
import com.gtog.event.domain.port.out.*;
import com.gtog.user.infrastructure.out.persistence.*;
import static com.gtog.event.GuestTestFixtures.guest;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.gtog.AuthBypassDisabled
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class EmailSendReservationIntegrationTest {
	private final MongoTemplate mongo;
	@MockitoSpyBean private EventRepositoryPort events;
	private final MockMvc mvc;
	private final GuestEmailSendReservationPort guests;
	private final HostSendQuotaPort hosts;
	private final EventGuestRepositoryPort guestWrites;
	private final GuestService guestService;
	private final UserMapper userMapper;
	private final EmailSendPolicy policy = new EmailSendPolicy(3, Duration.ofHours(1), 100, 20);
	private Event event;

	EmailSendReservationIntegrationTest(MongoTemplate mongo, MockMvc mvc, GuestEmailSendReservationPort guests,
			HostSendQuotaPort hosts, EventGuestRepositoryPort guestWrites, GuestService guestService, UserMapper userMapper) {
		this.mongo = mongo; this.mvc = mvc; this.guests = guests; this.hosts = hosts;
		this.guestWrites = guestWrites; this.guestService = guestService; this.userMapper = userMapper;
	}
	@BeforeEach void setUp() {
		mongo.remove(new Query(), EventDocument.class); mongo.remove(new Query(), GuestResponseDocument.class); mongo.remove(new Query(), UserDocument.class);
		mongo.save(com.gtog.UserTestFixtures.verified("host-1", "Host", "host@example.com", "unused", "Europe/Madrid"));
		event = events.save(com.gtog.event.GuestTestFixtures.event("event-id", EventStatus.PUBLISHED, null, guest(101)));
	}
	private Guest currentGuest() { return events.findById(event.getId()).orElseThrow().getGuests().getFirst(); }
	private GuestEmailReservationResult reserve(Instant now) {
		Guest guest = currentGuest();
		return guests.reserve(event.getId(), "host-1", guest.getId(), guest.getEmail(), now, policy);
	}
	private EmailSendReservationService coordinator(int hostLimit) {
		return new EmailSendReservationService(guests, hosts, new EmailSendPolicy(3, Duration.ofHours(1), hostLimit, 20), Clock.fixed(NOW, ZoneOffset.UTC));
	}
	private void guestHistory(int count, Instant last) {
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())),
				new Update().set("guests.0.emailSendCount", count).set("guests.0.lastEmailSentAt", last), EventDocument.class);
	}
	private <T> List<T> parallel(IntFunction<T> action) throws Exception {
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return action.apply(0); });
			var second = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return action.apply(1); });
			return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
		}
	}

	@Test void twoConcurrentReservationsForOneGuestYieldExactlyOnePermit() throws Exception {
		var result = parallel(index -> reserve(NOW));
		assertThat(result.stream().filter(r -> r.reservation() != null).count()).isEqualTo(1);
		assertThat(result.stream().filter(r -> r.reason() == EmailSendSkipReason.THROTTLED).count()).isEqualTo(1);
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(1);
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(event.getVersion() + 1);
	}
	@Test void concurrentBatchesForOneHostWithQuotaOneAuthorizeExactlyOneSimulatedSend() throws Exception {
		Event other = events.save(com.gtog.event.GuestTestFixtures.event("event-2", EventStatus.PUBLISHED, null, guest(102)));
		var sends = new AtomicInteger(); var coordinator = coordinator(1);
		var result = parallel(index -> {
			Event candidate = index == 0 ? event : other;
			var reserved = coordinator.reserve(candidate, candidate.getGuests().getFirst(), false);
			if (reserved.reservation() != null) { sends.incrementAndGet(); }
			return reserved;
		});
		assertThat(sends.get()).isEqualTo(1);
		assertThat(result.stream().filter(r -> r.reason() == EmailSendSkipReason.DAILY_LIMIT).count()).isEqualTo(1);
		assertThat(hosts.read("host-1", NOW, 1).remaining()).isZero();
		assertThat(events.findByHostId("host-1").stream().mapToInt(e -> e.getGuests().getFirst().getEmailSendCount()).sum()).isEqualTo(1);
	}
	@Test void theThirdReservationExhaustsTheGuestLimit() {
		for (int i = 0; i < 3; i++) { assertThat(reserve(NOW.plusSeconds(i * 3600)).reservation()).isNotNull(); }
		assertThat(reserve(NOW.plusSeconds(10800)).reason()).isEqualTo(EmailSendSkipReason.LIMIT_REACHED);
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(3);
	}
	@ParameterizedTest @ValueSource(longs = { -1, 0, 1 })
	void theAtomicGuestIntervalIncludesTheExactBoundary(long offset) {
		guestHistory(1, NOW.minusSeconds(3600));
		var result = reserve(NOW.plusSeconds(offset));
		assertThat(result.reservation() != null).isEqualTo(offset >= 0);
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(offset < 0 ? 1 : 2);
	}
	@ParameterizedTest @ValueSource(longs = { -1, 0, 1 })
	void theAtomicHostWindowIncludesTheExact24HourBoundary(long offset) {
		mongo.updateFirst(Query.query(Criteria.where("id").is("host-1")),
				new Update().set("emailSendsInWindow", 100).set("windowStartedAt", NOW.minusSeconds(86400)), UserDocument.class);
		var result = hosts.reserve("host-1", NOW.plusSeconds(offset), 100);
		assertThat(result.isPresent()).isEqualTo(offset >= 0);
		var stored = mongo.findById("host-1", UserDocument.class);
		assertThat(stored.getEmailSendsInWindow()).isEqualTo(offset < 0 ? 100 : 1);
		assertThat(stored.getWindowStartedAt()).isEqualTo(offset < 0 ? NOW.minusSeconds(86400) : NOW.plusSeconds(offset));
	}
	@Test void absentFieldsReadAsZeroAndNullAndCanBeReserved() {
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().unset("guests.0.emailSendCount").unset("guests.0.lastEmailSentAt"), EventDocument.class);
		assertThat(currentGuest().getEmailSendCount()).isZero(); assertThat(currentGuest().getLastEmailSentAt()).isNull();
		assertThat(reserve(NOW).reservation()).isNotNull();
		assertThat(hosts.read("host-1", NOW, 100)).isEqualTo(new HostSendQuota(100, 100, null));
		assertThat(hosts.reserve("host-1", NOW, 100)).isPresent();
	}
	@Test void hostReadsReflectTheDocumentAndDoNotWrite() {
		hosts.reserve("host-1", NOW, 100);
		var before = mongo.getCollection("users").find().first();
		assertThat(hosts.read("host-1", NOW, 100)).isEqualTo(new HostSendQuota(100, 99, NOW.plusSeconds(86400)));
		assertThat(hosts.read("host-1", NOW.plusSeconds(86400), 100)).isEqualTo(new HostSendQuota(100, 100, null));
		assertThat(mongo.getCollection("users").find().first()).isEqualTo(before);
	}
	@Test void successfulCompensationRestoresBothReservations() {
		guestHistory(2, NOW.minusSeconds(7200));
		var result = coordinator(100).reserve(events.findById(event.getId()).orElseThrow(), currentGuest(), false);
		assertThat(coordinator(100).compensate(result.reservation())).isTrue();
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(2); assertThat(currentGuest().getLastEmailSentAt()).isEqualTo(NOW.minusSeconds(7200));
		assertThat(hosts.read("host-1", NOW, 100)).isEqualTo(new HostSendQuota(100, 100, null));
	}
	@Test void anAlreadyAnsweredGuestConsumesNeitherReservation() {
		var before = mongo.getCollection("events").find().first();
		assertThat(coordinator(100).reserve(event, currentGuest(), true).reason()).isEqualTo(EmailSendSkipReason.ALREADY_ANSWERED);
		assertThat(mongo.getCollection("events").find().first()).isEqualTo(before);
		assertThat(hosts.read("host-1", NOW, 100)).isEqualTo(new HostSendQuota(100, 100, null));
	}
	@Test void anOpeningConcurrentWithReservationPreservesBothChanges() throws Exception {
		String token = currentGuest().getToken();
		parallel(index -> index == 0 ? reserve(NOW) : guestWrites.markGuestOpened(event.getId(), token, NOW));
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(1); assertThat(currentGuest().getLastEmailSentAt()).isEqualTo(NOW);
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED); assertThat(currentGuest().getOpenedAt()).isEqualTo(NOW);
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(event.getVersion() + 2);
	}
	@Test void recordingSentNeverDowngradesAnOpenedInvitation() {
		var ticket = reserve(NOW).reservation();
		guestWrites.markGuestOpened(event.getId(), currentGuest().getToken(), NOW);
		assertThat(guests.recordSent(ticket, NOW)).isTrue();
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
	}
	@Test void recordingSentSetsTheFirstTimestampAndDoesNotRewriteIt() {
		var ticket = reserve(NOW).reservation();
		assertThat(guests.recordSent(ticket, NOW)).isTrue();
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(ticket.version() + 1);
		assertThat(guests.recordSent(ticket, NOW.plusSeconds(5))).isTrue();
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(ticket.version() + 1);
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
		assertThat(currentGuest().getDeliveryChannel()).isEqualTo(DeliveryChannel.EMAIL);
		assertThat(currentGuest().getSentAt()).isEqualTo(NOW);
	}
	@Test void changingEmailViaR6ResetsBothCountersAtomically() {
		guestHistory(3, NOW);
		guestService.update(new UpdateGuestCommand("host-1", event.getId(), currentGuest().getId(), "Guest", "new@example.com", null));
		assertThat(currentGuest().getEmailSendCount()).isZero(); assertThat(currentGuest().getLastEmailSentAt()).isNull();
		assertThat(reserve(NOW).reservation()).isNotNull();
	}
	@Test void anEquivalentEmailEditPreservesCounters() {
		guestHistory(2, NOW);
		guestService.update(new UpdateGuestCommand("host-1", event.getId(), currentGuest().getId(), "Guest", " GUEST101@EXAMPLE.COM ", null));
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(2); assertThat(currentGuest().getLastEmailSentAt()).isEqualTo(NOW);
	}
	@Test void aStaleR6EditCannotEraseAReservation() {
		// Ya no se rechaza por version: la edicion parcial debe preservar la reserva real en Mongo.
		var stale = currentGuest();
		var reservation = reserve(NOW).reservation();
		var reserved = currentGuest();
		var edited = stale.editContact("Changed after reservation", " " + stale.getEmail().toUpperCase(java.util.Locale.ROOT) + " ", null, phone -> phone);
		guestWrites.updateGuest(event.getId(), "host-1", event.getVersion(), edited);
		var stored = currentGuest();
		assertThat(stored.getName()).isEqualTo("Changed after reservation");
		assertThat(stored.getEmail()).isEqualTo(stale.getEmail());
		assertThat(stored.getEmailSendCount()).isEqualTo(reserved.getEmailSendCount()).isEqualTo(1);
		assertThat(stored.getLastEmailSentAt()).isEqualTo(reserved.getLastEmailSentAt()).isEqualTo(NOW);
		assertThat(reservation).isNotNull();
		assertThat(stored.getLastEmailSentAt()).isEqualTo(reservation.reservedAt());
	}
	@Test void aStaleR6EditChangingEmailResetsTheReservation() {
		var stale = currentGuest(); reserve(NOW);
		guestWrites.updateGuest(event.getId(), "host-1", event.getVersion(), stale.editContact("Changed email", "different@example.com", null, phone -> phone));
		var stored = currentGuest();
		assertThat(stored.getName()).isEqualTo("Changed email"); assertThat(stored.getEmail()).isEqualTo("different@example.com");
		assertThat(stored.getEmailSendCount()).isZero(); assertThat(stored.getLastEmailSentAt()).isNull();
	}
	@Test void aFailedCompensationAtTheLimitKeepsTheGuestBlockedAndLogsTheDiagnostic(CapturedOutput output) {
		guestHistory(2, NOW.minusSeconds(7200));
		var result = coordinator(100).reserve(events.findById(event.getId()).orElseThrow(), currentGuest(), false);
		guestWrites.markGuestOpened(event.getId(), currentGuest().getToken(), NOW);
		assertThat(coordinator(100).compensate(result.reservation())).isFalse();
		assertThat(reserve(NOW.plusSeconds(3600)).reason()).isEqualTo(EmailSendSkipReason.LIMIT_REACHED);
		assertThat(output.getAll()).contains("EMAIL_SEND_COMPENSATION_FAILED", "stage=GUEST", "correlationId=")
				.doesNotContain(currentGuest().getEmail(), currentGuest().getToken());
	}
	@Test void anOldHostCompensationCannotDecrementANewWindow() {
		var old = hosts.reserve("host-1", NOW, 100).orElseThrow();
		hosts.reserve("host-1", NOW.plusSeconds(86400), 100);
		assertThat(hosts.release(old)).isFalse();
		assertThat(hosts.read("host-1", NOW.plusSeconds(86400), 100).remaining()).isEqualTo(99);
	}
	@Test void userAndGuestMappersPreserveTheQuotaFields() {
		hosts.reserve("host-1", NOW, 100); reserve(NOW);
		var user = mongo.findById("host-1", UserDocument.class);
		var roundTrip = userMapper.toDocument(userMapper.toDomain(user));
		assertThat(roundTrip.getEmailSendsInWindow()).isEqualTo(1); assertThat(roundTrip.getWindowStartedAt()).isEqualTo(NOW);
		assertThat(events.save(events.findById(event.getId()).orElseThrow()).getGuests().getFirst().getEmailSendCount()).isEqualTo(1);
	}
	@Test void anOldCompensationCannotReleaseANewReservationAfterEmailChangesBack() {
		var old = reserve(NOW).reservation();
		String email = currentGuest().getEmail(); String id = currentGuest().getId();
		guestService.update(new UpdateGuestCommand("host-1", event.getId(), id, "Guest", "other@example.com", null));
		guestService.update(new UpdateGuestCommand("host-1", event.getId(), id, "Guest", email, null));
		var newer = reserve(NOW).reservation();
		assertThat(guests.release(old)).isFalse(); assertThat(currentGuest().getEmailSendCount()).isEqualTo(1);
		assertThat(guests.release(newer)).isTrue(); assertThat(currentGuest().getEmailSendCount()).isZero();
	}
	@Test void reservationReceiptsUseTheSameMillisecondPrecisionAsMongo() {
		Instant precise = NOW.plusNanos(123456789);
		var ticket = reserve(precise).reservation();
		assertThat(ticket.reservedAt()).isEqualTo(currentGuest().getLastEmailSentAt());
		guestWrites.markGuestOpened(event.getId(), currentGuest().getToken(), precise);
		assertThat(guests.recordSent(ticket, precise)).isTrue();
		assertThat(currentGuest().getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
	}
	@Test void recordingTheFirstEmailDoesNotReplaceAnExistingSentTimestamp() {
		mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().set("guests.0.sentAt", NOW.minusSeconds(7200)), EventDocument.class);
		assertThat(guests.recordSent(reserve(NOW).reservation(), NOW)).isTrue();
		assertThat(currentGuest().getSentAt()).isEqualTo(NOW.minusSeconds(7200));
	}
	@Test void aRawLegacyUserWithoutQuotaFieldsCanReserve() {
		mongo.remove(Query.query(Criteria.where("id").is("host-1")), UserDocument.class);
		mongo.getCollection("users").insertOne(new org.bson.Document("_id", "host-1").append("email", "host@example.com"));
		var before = mongo.getCollection("users").find().first();
		assertThat(before).doesNotContainKeys("emailSendsInWindow", "windowStartedAt");
		assertThat(hosts.reserve("host-1", NOW, 100)).isPresent();
		var after = mongo.findById("host-1", UserDocument.class);
		assertThat(after.getEmailSendsInWindow()).isEqualTo(1); assertThat(after.getWindowStartedAt()).isEqualTo(NOW);
	}
	@Test void twoConcurrentReservationsCannotExceedAnAlreadyReachedGuestLimit() throws Exception {
		guestHistory(3, NOW.minusSeconds(7200));
		long versionBefore = events.findById(event.getId()).orElseThrow().getVersion();
		var results = parallel(index -> reserve(NOW));
		assertThat(results).allSatisfy(result -> {
			assertThat(result.reservation()).isNull(); assertThat(result.reason()).isEqualTo(EmailSendSkipReason.LIMIT_REACHED);
		});
		assertThat(currentGuest().getEmailSendCount()).isEqualTo(3);
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(versionBefore);
	}
	@Test
	@WithUserDetails(value = "host@example.com", setupBefore = TestExecutionEvent.TEST_EXECUTION)
	void aCompensationMakesAnOlderEventSaveReturn409() throws Exception {
		var reservation = coordinator(100).reserve(event, currentGuest(), false).reservation();
		Event stale = events.findById(event.getId()).orElseThrow();
		assertThat(coordinator(100).compensate(reservation)).isTrue();
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isEqualTo(stale.getVersion() + 1);
		doReturn(java.util.Optional.of(stale)).when(events).findById(event.getId());
		mvc.perform(post("/api/events/" + event.getId() + "/cancel").with(csrf()).contentType("application/json").content("{}"))
				.andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
		assertThat(mongo.findById(event.getId(), EventDocument.class).getStatus()).isEqualTo("PUBLISHED");
	}
}
