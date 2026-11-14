package com.gtog.event.infrastructure.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.domain.model.ResponseChangeNotAllowedException;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import static com.gtog.event.InvitationTestFixtures.NOW;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class GuestResponseControllerIntegrationTest {
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 1, 1, 17, 0);
	@Autowired private MockMvc mvc;
	@Autowired private MongoTemplate mongo;
	@Autowired private EventRepositoryPort events;
	@Autowired private EventMongoRepository eventDocuments;
	@Autowired private GuestResponseRepositoryPort responses;
	@MockitoBean private Clock clock;
	private String token;

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		mongo.remove(new Query(), GuestResponseDocument.class);
		fixed(NOW);
		token = responseEvent(EventStatus.PUBLISHED, true, false, null).getGuests().getFirst().getToken();
	}

	@Test
	void acceptsAPublicPutWithoutSessionOrCsrfAndDoesNotExposeEmailOrOtherGuests(CapturedOutput output) throws Exception {
		Event event = seed(EventStatus.PUBLISHED, true, false, null);
		var result = respond("yes-id", "Private comment", "response-only@example.com");
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		var stored = responses.find(event.getId(), token).orElseThrow();
		assertThat(stored.email()).isEqualTo("response-only@example.com");
		assertThat(stored.comment()).isEqualTo("Private comment");
		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("yes-id", "Private comment", "answeredAt", "updatedAt");
		assertThat(body).doesNotContain(token, "response-only@example.com", "host-1",
				event.getGuests().get(1).getToken(), event.getGuests().get(1).getEmail(), event.getGuests().get(1).getName());
		String getBody = mvc.perform(get(invitationPath())).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.myResponse.responseOptionId").value("yes-id"))
				.andExpect(jsonPath("$.myResponse.comment").value("Private comment"))
				.andExpect(jsonPath("$.myResponse.answeredAt").value(NOW.toString()))
				.andExpect(jsonPath("$.myResponse.updatedAt").value(NOW.toString()))
				.andExpect(jsonPath("$.myResponse.email").doesNotExist())
				.andExpect(jsonPath("$.canChangeResponse").value(false)).andReturn().getResponse().getContentAsString();
		assertThat(getBody).doesNotContain(token, "response-only@example.com", "host-1",
				event.getGuests().get(1).getToken(), event.getGuests().get(1).getEmail(), event.getGuests().get(1).getName());
		assertThat(output.getAll()).doesNotContain(token, "Private comment", "response-only@example.com");
		assertThat(events.findById(event.getId()).orElseThrow().getVersion()).isZero();
	}

	@Test
	void aDuplicateReturns409WithoutLeakingItsTokenInTheBodyOrLogs(CapturedOutput output) throws Exception {
		seed(EventStatus.PUBLISHED, true, false, null);
		assertThat(respond("yes-id", "First secret comment", null).getResponse().getStatus()).isEqualTo(200);
		var duplicate = respond("no-id", "Second secret comment", null);
		assertProblem(duplicate, 409);
		assertThat(duplicate.getResponse().getContentAsString()).contains("Response changes are not allowed")
				.doesNotContain(token, "First secret comment", "Second secret comment", "E11000");
		assertThat(output.getAll()).doesNotContain(token, "First secret comment", "Second secret comment", "E11000");
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isEqualTo(1);
		assertThat(responses.find("event-id", token).orElseThrow().responseOptionId()).isEqualTo("yes-id");
	}

	@Test
	void duplicateTranslationDoesNotRetainTheDriverCauseOrItsMessage() {
		Event event = seed(EventStatus.PUBLISHED, true, false, null);
		var r = com.gtog.event.domain.model.GuestResponse.create(event, token, "yes-id", null, null, NOW);
		responses.insert(r);
		assertThatThrownBy(() -> responses.insert(r)).isInstanceOf(ResponseChangeNotAllowedException.class)
				.hasNoCause().hasMessage("Response changes are not allowed");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void aDuplicateOnAnotherIndexIsNotMisreportedAsAResponseConflict(boolean allowChange, CapturedOutput output) throws Exception {
		seed(EventStatus.PUBLISHED, true, allowChange, null);
		mongo.indexOps(GuestResponseDocument.class).createIndex(new Index().on("responseOptionId", Sort.Direction.ASC).unique().named("test_other_unique"));
		try {
			assertThat(respond("yes-id", null, null).getResponse().getStatus()).isEqualTo(200);
			token = responseEvent(EventStatus.PUBLISHED, true, false, null).getGuests().get(1).getToken();
			var otherDuplicate = respond("yes-id", null, null);
			assertProblem(otherDuplicate, 500);
			assertThat(otherDuplicate.getResponse().getContentAsString()).doesNotContain(token, "E11000");
			assertThat(output.getAll()).doesNotContain(token, "E11000");
		} finally { mongo.indexOps(GuestResponseDocument.class).dropIndex("test_other_unique"); }
	}

	@Test
	void allowedChangesPreserveAnsweredAtAndUpdateTheRestAtomically() throws Exception {
		seed(EventStatus.PUBLISHED, true, true, null);
		assertThat(respond("yes-id", "First", "first@example.com").getResponse().getStatus()).isEqualTo(200);
		fixed(NOW.plusSeconds(1));
		assertThat(respond("no-id", " ", null).getResponse().getStatus()).isEqualTo(200);
		var stored = responses.find("event-id", token).orElseThrow();
		assertThat(stored.responseOptionId()).isEqualTo("no-id");
		assertThat(stored.comment()).isNull();
		assertThat(stored.email()).isNull();
		assertThat(stored.answeredAt()).isEqualTo(NOW);
		assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isEqualTo(1);
		mvc.perform(get(invitationPath())).andExpect(status().isOk()).andExpect(jsonPath("$.canChangeResponse").value(true));
	}

	@Test
	void getWithoutAResponseOmitsMyResponseAndDoesNotOfferAChange() throws Exception {
		seed(EventStatus.PUBLISHED, true, true, null);
		mvc.perform(get(invitationPath())).andExpect(status().isOk()).andExpect(jsonPath("$.myResponse").doesNotExist())
				.andExpect(jsonPath("$.canChangeResponse").value(false));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void simultaneousPutsRespectTheAtomicWritePolicy(boolean allowChange) throws Exception {
		seed(EventStatus.PUBLISHED, true, allowChange, null);
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return respond("yes-id", null, null).getResponse().getStatus(); });
			var b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return respond("no-id", null, null).getResponse().getStatus(); });
			assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrderElementsOf(allowChange ? List.of(200, 200) : List.of(200, 409));
		}
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void deadlineAppliesToTheFirstResponseAndIncludesTheExactThreshold(int offset) throws Exception {
		seed(EventStatus.PUBLISHED, true, true, DEADLINE);
		fixed(Instant.parse("2027-01-01T16:00:00Z").plusSeconds(offset));
		var result = respond("yes-id", null, null);
		assertThat(result.getResponse().getStatus()).isEqualTo(offset <= 0 ? 200 : 409);
		if (offset > 0) { assertProblem(result, 409); }
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isEqualTo(offset <= 0 ? 1 : 0);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void deadlineAppliesToChangesAndToCanChangeResponse(int offset) throws Exception {
		seed(EventStatus.PUBLISHED, true, true, DEADLINE);
		assertThat(respond("yes-id", null, null).getResponse().getStatus()).isEqualTo(200);
		fixed(Instant.parse("2027-01-01T16:00:00Z").plusSeconds(offset));
		mvc.perform(get(invitationPath())).andExpect(status().isOk())
				.andExpect(jsonPath("$.canChangeResponse").value(offset <= 0));
		assertThat(respond("no-id", null, null).getResponse().getStatus()).isEqualTo(offset <= 0 ? 200 : 409);
		assertThat(responses.find("event-id", token).orElseThrow().responseOptionId()).isEqualTo(offset <= 0 ? "no-id" : "yes-id");
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void theEndOfTheEventIsExclusiveEvenWithoutADeadline(int offset) throws Exception {
		Event event = seed(EventStatus.PUBLISHED, true, true, null);
		fixed(event.endsAtInstant().plusSeconds(offset));
		assertThat(respond("yes-id", null, null).getResponse().getStatus()).isEqualTo(offset < 0 ? 200 : 409);
	}

	@Test
	void aChangeAtTheExactEndIsRejectedAndCanChangeIsFalse() throws Exception {
		Event event = seed(EventStatus.PUBLISHED, true, true, null);
		assertThat(respond("yes-id", null, null).getResponse().getStatus()).isEqualTo(200);
		fixed(event.endsAtInstant());
		assertProblem(respond("no-id", null, null), 409);
		mvc.perform(get(invitationPath())).andExpect(jsonPath("$.canChangeResponse").value(false));
		assertThat(responses.find("event-id", token).orElseThrow().responseOptionId()).isEqualTo("yes-id");
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedEventsRejectResponses(EventStatus state) throws Exception {
		seed(state, true, true, null);
		assertProblem(respond("yes-id", null, null), 409);
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isZero();
		mvc.perform(get(invitationPath())).andExpect(status().isOk()).andExpect(jsonPath("$.canChangeResponse").value(false));
	}

	@Test
	void draftAndUnknownProduceExactlyTheSame404WithoutWritingOrLeaking(CapturedOutput output) throws Exception {
		seed(EventStatus.DRAFT, true, true, null);
		var draft = respond("yes-id", null, null);
		assertProblem(draft, 404);
		String known = token;
		token = "unknown-secret";
		var unknown = respond("yes-id", null, null);
		assertProblem(unknown, 404);
		assertThat(draft.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString())
				.doesNotContain(known, token);
		assertThat(output.getAll()).doesNotContain(known, token);
		assertThat(mongo.count(new Query(), GuestResponseDocument.class)).isZero();
	}

	@Test
	void foreignOptionIs422() throws Exception {
		seed(EventStatus.PUBLISHED, true, true, null);
		assertProblem(respond("foreign", null, null), 422);
	}

	@Test
	void commentWithContentIs422WhenDisabled(CapturedOutput output) throws Exception {
		seed(EventStatus.PUBLISHED, false, true, null);
		assertProblem(respond("yes-id", "private-rejected-comment", null), 422);
		assertThat(output.getAll()).doesNotContain(token, "private-rejected-comment");
	}

	@Test
	void whitespaceCommentIsStoredAsNullWhenCommentsAreDisabled() throws Exception {
		seed(EventStatus.PUBLISHED, false, true, null);
		assertThat(respond("yes-id", "  ", null).getResponse().getStatus()).isEqualTo(200);
		assertThat(responses.find("event-id", token).orElseThrow().comment()).isNull();
	}

	@Test
	void commentLongerThan500Is422() throws Exception {
		seed(EventStatus.PUBLISHED, true, true, null);
		assertProblem(respond("yes-id", "a".repeat(501), null), 422);
	}

	@Test
	void invalidEmailIs422() throws Exception {
		seed(EventStatus.PUBLISHED, true, true, null);
		assertProblem(respond("yes-id", null, "invalid-email"), 422);
	}

	@Test
	void malformedJsonHasASanitized400AndDoesNotLogTheComment(CapturedOutput output) throws Exception {
		var result = mvc.perform(put(responsePath()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"comment\":\"private-malformed-comment\", invalid}")).andReturn();
		assertProblem(result, 400);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "private-malformed-comment");
		assertThat(output.getAll()).doesNotContain(token, "private-malformed-comment");
	}

	@Test
	void corsPreflightAllowsPutAndContentTypeWithoutSessionOrCsrf() throws Exception {
		mvc.perform(options(responsePath()).header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "PUT").header("Access-Control-Request-Headers", "Content-Type"))
				.andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
				.andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PUT")))
				.andExpect(header().string("Access-Control-Allow-Headers", "Content-Type"));
	}

	@Test
	void createsBothResponseIndexesAtStartup() {
		var indexes = mongo.indexOps(GuestResponseDocument.class).getIndexInfo();
		assertThat(indexes).anySatisfy(index -> {
			assertThat(index.isUnique()).isTrue();
			assertThat(index.getIndexFields()).extracting(field -> field.getKey()).containsExactly("eventId", "guestToken");
		});
		assertThat(indexes).anySatisfy(index -> assertThat(index.getIndexFields()).extracting(field -> field.getKey()).containsExactly("eventId"));
	}

	@Test
	void documentsThePublicPutAndItsErrors() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}/response'].put.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}/response'].put.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}/response'].put.responses['422']").exists());
	}

	private Event seed(EventStatus state, boolean comments, boolean changes, LocalDateTime deadline) {
		return events.save(responseEvent(state, comments, changes, deadline));
	}
	private void fixed(Instant now) { when(clock.instant()).thenReturn(Clock.fixed(now, ZoneOffset.UTC).instant()); }
	private String invitationPath() { return "/api/invitations/" + token; }
	private String responsePath() { return invitationPath() + "/response"; }
	private MvcResult respond(String option, String comment, String email) throws Exception {
		String body = "{\"responseOptionId\":\"" + option + "\",\"comment\":" + text(comment) + ",\"email\":" + text(email) + "}";
		return mvc.perform(put(responsePath()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}
	private String text(String value) { return value == null ? "null" : "\"" + value + "\""; }
	private void assertProblem(MvcResult result, int status) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
		assertThat(result.getResponse().getContentAsString()).contains("\"instance\":\"/api/invitations\"").doesNotContain(token);
	}
}
