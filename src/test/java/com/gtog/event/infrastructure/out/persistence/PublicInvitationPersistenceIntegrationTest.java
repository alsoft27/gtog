package com.gtog.event.infrastructure.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.gtog.event.application.PublicInvitationService;
import com.gtog.event.domain.model.*;
import com.gtog.event.domain.port.in.JoinPublicInvitationCommand;
import com.gtog.event.domain.port.out.*;
import static com.gtog.event.GuestTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.auth.auto-verify-email=false")
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PublicInvitationPersistenceIntegrationTest {
	private static final Instant NOW = Instant.parse("2027-01-01T10:00:00Z");
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final EventGuestRepositoryPort guests;
	private final PublicInvitationService service;
	private final GuestMapper mapper;
	@MockitoBean private Clock clock;
	@MockitoSpyBean private PublicInvitationRepositoryAdapter invitations;
	private String joinToken;

	PublicInvitationPersistenceIntegrationTest(MongoTemplate mongo, EventRepositoryPort events, EventGuestRepositoryPort guests,
			PublicInvitationService service, GuestMapper mapper) {
		this.mongo = mongo; this.events = events; this.guests = guests; this.service = service; this.mapper = mapper;
	}
	@BeforeEach void setUp() {
		mongo.remove(new Query(), EventDocument.class); when(clock.instant()).thenReturn(NOW);
		events.save(event("join-event", EventStatus.PUBLISHED, null, guest(1)));
		joinToken = service.change("host-1", "join-event", true).orElseThrow().token();
		clearInvocations(invitations);
	}
	private Guest join(String email) { return service.join(new JoinPublicInvitationCommand(joinToken, null, "Ana", email, null)); }
	private Event current() { return events.findById("join-event").orElseThrow(); }
	private Document document() { return mongo.getCollection("events").find(new Document("_id", "join-event")).first(); }
	private void seed(List<Guest> values) { mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")),
			new Update().set("guests", values.stream().map(mapper::toDocument).toList()), EventDocument.class); }
	private Guest publicGuest(int i) { return Guest.joinPublicInvitation("Public " + i, "public" + i + "@example.com", null,
			token(1000 + i), value -> value, NOW); }

	@Test void anExistingContactAndANewContactUseExactlyOneReadAndOneWriteEach() {
		Guest normal = join("new@example.com");
		verify(invitations, times(1)).findByToken(joinToken); verify(invitations, times(1)).tryAddGuest(any(), eq(joinToken), any(), eq(NOW));
		clearInvocations(invitations);
		Guest conflict = service.join(new JoinPublicInvitationCommand(joinToken, null, "Ana", " GUEST1@EXAMPLE.COM ", "+34 612 345 678"));
		verify(invitations, times(1)).findByToken(joinToken); verify(invitations, times(1)).tryAddGuest(any(), eq(joinToken), any(), eq(NOW));
		assertThat(normal.getEmail()).isEqualTo("new@example.com"); assertThat(conflict.getEmail()).isNull(); assertThat(conflict.getPhoneE164()).isNull();
		assertThat(current().getGuests()).hasSize(3);
	}
	@Test void concurrentJoinsWithTheSameEmailRetryOnceAndKeepItOnOnlyOneGuest() throws Exception {
		var barrier = new CyclicBarrier(2); var attempts = new AtomicInteger();
		doAnswer(call -> { if (attempts.incrementAndGet() <= 2) { barrier.await(10, TimeUnit.SECONDS); } return call.callRealMethod(); })
				.when(invitations).tryAddGuest(any(), eq(joinToken), any(), any());
		List<Guest> joined = concurrent(() -> join(" SAME@EXAMPLE.COM "), () -> join("same@example.com"));
		assertThat(joined.stream().filter(guest -> guest.getEmail() != null).count()).isEqualTo(1);
		assertThat(current().getGuests().stream().filter(guest -> "same@example.com".equals(guest.getEmail())).count()).isEqualTo(1);
		assertThat(joined).extracting(Guest::getToken).doesNotHaveDuplicates();
		verify(invitations, times(3)).tryAddGuest(any(), eq(joinToken), any(), eq(NOW));
		verify(invitations, times(3)).findByToken(joinToken);
	}
	@Test void twoConcurrentJoinsCannotConsumeTheLastPublicPlaceTwice() throws Exception {
		seed(IntStream.range(0, 24).mapToObj(this::publicGuest).toList());
		assertThat(concurrent(() -> outcome(() -> join(null)), () -> outcome(() -> join(null))))
				.containsExactlyInAnyOrder("CREATED", "PUBLIC_LINK_FULL");
		assertThat(current().publicJoinedCount()).isEqualTo(25);
	}
	@Test void aHostAdditionAndAPublicJoinCannotStoreTheSameContactTwice() throws Exception {
		var gate = new CyclicBarrier(2);
		var attempts = new AtomicInteger();
		doAnswer(call -> { if (attempts.incrementAndGet() == 1) { gate.await(10, TimeUnit.SECONDS); } return call.callRealMethod(); })
				.when(invitations).tryAddGuest(any(), eq(joinToken), any(), any());
		var snapshot = current(); Guest hostGuest = Guest.create("Host added", "shared@example.com", null, token(9), value -> value);
		List<String> results = concurrent(() -> { join("shared@example.com"); return "PUBLIC_CREATED"; }, () -> {
			gate.await(10, TimeUnit.SECONDS);
			try { guests.addGuest("join-event", "host-1", snapshot.getVersion(), hostGuest); return "HOST_CREATED"; }
			catch (EventConcurrentModificationException exception) { return "HOST_CONFLICT"; }
		});
		assertThat(results).contains("PUBLIC_CREATED");
		assertThat(current().getGuests().stream().filter(guest -> "shared@example.com".equals(guest.getEmail())).count()).isEqualTo(1);
	}
	@Test void aJoinAndAFirstOpeningPreserveBothWritesAndVersionIncrements() throws Exception {
		Event before = current(); Guest existing = before.getGuests().getFirst();
		concurrent(() -> join(null), () -> guests.markGuestOpened("join-event", existing.getToken(), NOW).orElseThrow().getGuest(existing.getId()));
		Event after = current(); assertThat(after.getGuests()).hasSize(2);
		assertThat(after.getGuest(existing.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.OPENED);
		assertThat(after.getGuest(existing.getId()).getOpenedAt()).isEqualTo(NOW);
		assertThat(after.getVersion()).isEqualTo(before.getVersion() + 2);
	}
	@Test void thePublicLimitAllows24To25AndRejectsTheNextWithoutChangingBson() {
		seed(IntStream.range(0, 24).mapToObj(this::publicGuest).toList()); join(null); var before = document();
		assertThat(outcome(() -> join(null))).isEqualTo("PUBLIC_LINK_FULL"); assertThat(document()).isEqualTo(before);
	}
	@Test void theEventLimitAllows499To500AndRejectsTheNextWithoutChangingBson() {
		seed(IntStream.range(1, 500).mapToObj(com.gtog.event.GuestTestFixtures::guest).toList()); join(null); var before = document();
		assertThat(outcome(() -> join(null))).isEqualTo("CAPACITY_EXCEEDED"); assertThat(document()).isEqualTo(before);
	}
	@Test void thePublicLimitHasPriorityWhenBothLimitsAreExhausted() {
		var values = new ArrayList<Guest>(IntStream.range(0, 25).mapToObj(this::publicGuest).toList());
		values.addAll(IntStream.range(1, 476).mapToObj(com.gtog.event.GuestTestFixtures::guest).toList()); seed(values);
		assertThat(outcome(() -> join(null))).isEqualTo("PUBLIC_LINK_FULL");
	}
	@Test void activationIsIdempotentRevocationUnsetsAndReactivationUsesANewToken() {
		long version = current().getVersion();
		assertThat(service.change("host-1", "join-event", true).orElseThrow().token()).isEqualTo(joinToken);
		assertThat(current().getVersion()).isEqualTo(version);
		service.change("host-1", "join-event", false);
		assertThat(document().get("publicInvitation", Document.class)).containsEntry("enabled", false).doesNotContainKey("token");
		assertThat(invitations.findByToken(joinToken)).isEmpty();
		String replacement = service.change("host-1", "join-event", true).orElseThrow().token();
		assertThat(replacement).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(joinToken).isNotEqualTo(current().getId()).isNotEqualTo(current().getGuests().getFirst().getToken());
		assertThat(current().getVersion()).isEqualTo(version + 2);
		assertThatThrownBy(() -> join(null)).isInstanceOf(InvitationNotFoundException.class);
	}
	@Test void aCancellationWithTheVersionBeforeAToggleCannotRestoreTheLink() {
		Event stale = current(); service.change("host-1", "join-event", false); stale.cancel("Cancelado", NOW);
		assertThatThrownBy(() -> events.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(current().getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(current().getPublicInvitation().enabled()).isFalse(); assertThat(current().getPublicInvitation().token()).isNull();
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "DRAFT", "PUBLISHED" })
	void bothEditableStatesCanDisableAndEnableTheLink(EventStatus status) {
		if (status == EventStatus.DRAFT) { service.change("host-1", "join-event", false); }
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update().set("status", status.name()), EventDocument.class);
		service.change("host-1", "join-event", false);
		assertThat(current().getPublicInvitation().enabled()).isFalse(); assertThat(current().getPublicInvitation().token()).isNull();
		assertThat(service.change("host-1", "join-event", true)).isPresent();
		assertThat(current().getPublicInvitation().enabled()).isTrue();
		if (status == EventStatus.DRAFT) {
			assertThat(document().get("publicInvitation", Document.class)).containsEntry("enabled", true).doesNotContainKey("token");
		} else { assertThat(current().getPublicInvitation().token()).isNotNull(); }
	}
	@Test void aDeadlineClosedAfterTheSnapshotPreventsTheAtomicInsertion() {
		Event snapshot = current(); Guest candidate = Guest.joinPublicInvitation("Ana", null, null, token(700), value -> value, NOW);
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update()
				.set("responseDeadline", java.time.LocalDateTime.of(2027, 1, 1, 10, 59, 59)), EventDocument.class);
		Document before = document();
		assertThat(invitations.tryAddGuest(snapshot, joinToken, candidate, NOW)).isEqualTo(PublicJoinWriteResult.JOIN_CLOSED);
		assertThat(document()).isEqualTo(before);
	}
	@Test void hostAdditionBatchAndEditAllRejectAContactOwnedByAPublicGuest() {
		Guest pub = publicGuest(1); seed(List.of(guest(1), pub)); Document before = document();
		Guest duplicate = Guest.create("Other", pub.getEmail(), null, token(98), value -> value);
		assertThatThrownBy(() -> guests.addGuest("join-event", "host-1", current().getVersion(), duplicate)).isInstanceOf(EventConcurrentModificationException.class);
		assertThatThrownBy(() -> guests.addGuests("join-event", "host-1", List.of(duplicate))).isInstanceOf(GuestBatchRejectedException.class);
		Guest existing = current().getGuests().getFirst();
		assertThatThrownBy(() -> guests.updateGuest("join-event", "host-1", current().getVersion(),
				existing.editContact("Changed", pub.getEmail(), null, value -> value))).isInstanceOf(DuplicateGuestEmailException.class);
		assertThat(document()).isEqualTo(before);
	}
	@Test void aLegacyDocumentDefaultsToHostAndDisabledAndAnAbsentArrayCanReceiveAGuest() {
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update().unset("publicInvitation")
				.unset("guests.0.source").unset("guests.0.joinedAt"), EventDocument.class);
		assertThat(current().getGuests().getFirst().getSource()).isEqualTo(GuestSource.HOST);
		assertThat(current().getGuests().getFirst().getJoinedAt()).isNull(); assertThat(current().isPublicInvitationEnabled()).isFalse();
		joinToken = service.change("host-1", "join-event", true).orElseThrow().token();
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update().unset("guests"), EventDocument.class);
		Guest created = join(null); assertThat(current().getGuests()).extracting(Guest::getId).containsExactly(created.getId());
	}
	@Test void theIndexIsNamedUniquePartialAndAllowsMultipleDisabledEvents() {
		Document index = mongo.getCollection("events").listIndexes().into(new ArrayList<>()).stream()
				.filter(value -> "public_invitation_token".equals(value.getString("name"))).findFirst().orElseThrow();
		assertThat(index.getBoolean("unique")).isTrue();
		assertThat(index.get("partialFilterExpression", Document.class)).isEqualTo(new Document("publicInvitation.token", new Document("$type", 2)));
		events.save(event("disabled-1", EventStatus.PUBLISHED, null)); events.save(event("disabled-2", EventStatus.PUBLISHED, null));
		assertThat(mongo.getCollection("events").countDocuments(new Document("publicInvitation.token", null))).isEqualTo(2);
		mongo.updateFirst(Query.query(Criteria.where("id").is("disabled-1")), new Update()
				.set("publicInvitation.enabled", true).set("publicInvitation.token", token(99999)), EventDocument.class);
		assertThatThrownBy(() -> mongo.updateFirst(Query.query(Criteria.where("id").is("disabled-2")),
				new Update().set("publicInvitation.enabled", true).set("publicInvitation.token", token(99999)), EventDocument.class)).isInstanceOf(DuplicateKeyException.class);
	}
	@Test void theApplicationUsesTheInclusiveResponseDeadlineFromItsClock() {
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update()
				.set("responseDeadline", java.time.LocalDateTime.of(2027, 1, 1, 11, 0)), EventDocument.class);
		when(clock.instant()).thenReturn(NOW.minusNanos(1)); join(null);
		when(clock.instant()).thenReturn(NOW); join(null); Document before = document();
		when(clock.instant()).thenReturn(NOW.plusNanos(1)); assertThat(outcome(() -> join(null))).isEqualTo("JOIN_CLOSED");
		assertThat(document()).isEqualTo(before);
	}
	@Test void theApplicationRejectsTheExactEndAndAfterItFromItsClock() {
		Instant end = current().endsAtInstant(); when(clock.instant()).thenReturn(end.minusNanos(1)); join(null);
		Document before = document(); when(clock.instant()).thenReturn(end);
		assertThat(outcome(() -> join(null))).isEqualTo("JOIN_CLOSED");
		when(clock.instant()).thenReturn(end.plusNanos(1)); assertThat(outcome(() -> join(null))).isEqualTo("JOIN_CLOSED");
		assertThat(document()).isEqualTo(before);
	}
	@Test void aFullSavePreservesPublicCredentialsAndGuestOriginAndJoinedAt() {
		Guest joined = join("ana@example.com"); events.save(current()); Event reloaded = current();
		assertThat(reloaded.getPublicInvitation().token()).isEqualTo(joinToken);
		Guest saved = reloaded.getGuest(joined.getId()); assertThat(saved.getSource()).isEqualTo(GuestSource.PUBLIC_LINK);
		assertThat(saved.getJoinedAt()).isEqualTo(NOW); assertThat(saved.getEmail()).isEqualTo("ana@example.com");
		Guest edited = saved.editContact("Edited", null, null, value -> value);
		guests.updateGuest(reloaded.getId(), reloaded.getHostId(), reloaded.getVersion(), edited);
		assertThat(current().getGuest(saved.getId()).getSource()).isEqualTo(GuestSource.PUBLIC_LINK);
		assertThat(current().getGuest(saved.getId()).getJoinedAt()).isEqualTo(NOW);
	}
	@ParameterizedTest @EnumSource(value = EventStatus.class, names = { "CANCELLED", "FINISHED" })
	void closedStatesRejectBothSwitchDirectionsAndJoining(EventStatus status) {
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update().set("status", status.name()), EventDocument.class);
		Document before = document();
		assertThatThrownBy(() -> service.change("host-1", "join-event", true)).isInstanceOf(PublicInvitationNotEditableException.class);
		assertThatThrownBy(() -> service.change("host-1", "join-event", false)).isInstanceOf(PublicInvitationNotEditableException.class);
		assertThat(outcome(() -> join(null))).isEqualTo("JOIN_CLOSED"); assertThat(document()).isEqualTo(before);
	}
	@Test void aDraftPublicTokenIsIndistinguishableFromAnUnknownOne() {
		service.change("host-1", "join-event", false);
		mongo.updateFirst(Query.query(Criteria.where("id").is("join-event")), new Update().set("status", "DRAFT"), EventDocument.class);
		service.change("host-1", "join-event", true);
		assertThat(document().get("publicInvitation", Document.class)).containsEntry("enabled", true).doesNotContainKey("token");
		var draft = catchThrowableOfType(() -> join(null), InvitationNotFoundException.class);
		var unknown = catchThrowableOfType(() -> service.join(new JoinPublicInvitationCommand(token(888), null, "Ana", null, null)), InvitationNotFoundException.class);
		assertThat(draft.getMessage()).isEqualTo(unknown.getMessage());
	}
	private static String outcome(java.util.function.Supplier<?> call) {
		try { call.get(); return "CREATED"; } catch (PublicJoinConflictException exception) { return exception.getCode().name(); }
	}
	private static <T> List<T> concurrent(Callable<T> a, Callable<T> b) throws Exception {
		var start = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return a.call(); });
			var second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return b.call(); });
			return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
		}
	}
}
