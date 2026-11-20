package com.gtog.event.infrastructure.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.gtog.event.domain.model.EventStatus;
import com.gtog.event.domain.model.InvitationStatus;
import com.gtog.event.domain.model.LinkVisibility;
import com.gtog.event.domain.port.out.EventRepositoryPort;
import com.gtog.event.domain.port.out.GuestResponseRepositoryPort;
import com.gtog.event.infrastructure.out.persistence.EventMongoRepository;
import com.gtog.event.infrastructure.out.persistence.GuestResponseDocument;
import static com.gtog.event.InvitationTestFixtures.*;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.gtog.AuthBypassDisabled
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class InvitationCalendarControllerIntegrationTest {
	private final MockMvc mvc;
	private final MongoTemplate mongo;
	private final EventRepositoryPort events;
	private final EventMongoRepository eventDocuments;
	private final GuestResponseRepositoryPort responses;
	@MockitoBean private Clock clock;
	private String token;

	InvitationCalendarControllerIntegrationTest(MockMvc mvc, MongoTemplate mongo, EventRepositoryPort events,
			EventMongoRepository eventDocuments, GuestResponseRepositoryPort responses) {
		this.mvc = mvc;
		this.mongo = mongo;
		this.events = events;
		this.eventDocuments = eventDocuments;
		this.responses = responses;
	}

	@BeforeEach
	void setUp() {
		eventDocuments.deleteAll();
		mongo.remove(new Query(), GuestResponseDocument.class);
		fixed(NOW);
		token = responseEvent(EventStatus.PUBLISHED, true, true, null).getGuests().getFirst().getToken();
	}

	@Test
	void publiclyDownloadsThePhysicalEventWithoutSessionCsrfOrPrivateGuestFields(CapturedOutput output) throws Exception {
		var event = events.save(responseEvent(EventStatus.PUBLISHED, true, true, null));
		String body = download();
		assertThat(body).contains("SUMMARY:Public event\r\n", "DESCRIPTION:Public description\r\n",
				"LOCATION:Sala\\, Direccion\r\n", "DTSTART;TZID=Europe/Madrid:20270101T180000\r\n",
				"DTEND;TZID=Europe/Madrid:20270101T200000\r\n", "DTSTAMP:20270101T150000Z\r\n", "UID:event-id@gtog\r\n");
		assertThat(body).doesNotContain(token, "host-1", "hostId", "guests", "guest-101", "guest-202");
		for (var guest : event.getGuests()) {
			assertThat(body).doesNotContain(guest.getName(), guest.getEmail(), guest.getPhoneE164(), guest.getToken());
		}
		assertThat(output.getAll()).doesNotContain(token);
	}

	@Test
	void unknownAndDraftHaveTheSame404AsThePublicPageEvenWhenRequestingCalendar(CapturedOutput output) throws Exception {
		events.save(responseEvent(EventStatus.DRAFT, true, true, null));
		String draft = error(404);
		String known = token;
		token = "unknown-calendar-secret";
		String unknown = error(404);
		String publicPage = mvc.perform(get(invitation())).andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
		assertThat(draft).isEqualTo(unknown).isEqualTo(publicPage).doesNotContain(known, token);
		assertThat(output.getAll()).doesNotContain(known, token);
	}

	@Test
	void cancelledIs409WithProblemDetailConstantInstanceAndExactNoStore(CapturedOutput output) throws Exception {
		events.save(responseEvent(EventStatus.CANCELLED, true, true, null, LinkVisibility.ALWAYS));
		String body = error(409);
		assertThat(body).contains("Calendar is not available for a cancelled event").doesNotContain(token, URL, PASSWORD);
		assertThat(output.getAll()).doesNotContain(token);
	}

	@Test
	void finishedStillDownloadsWithoutAnOnlineMeetingLink() throws Exception {
		events.save(responseEvent(EventStatus.FINISHED, true, true, null, LinkVisibility.ALWAYS));
		responses.insert(new com.gtog.event.domain.model.GuestResponse("event-id", token, "yes-id", null, null, NOW, NOW));
		assertThat(download()).contains("UID:event-id@gtog").doesNotContain(URL, PASSWORD, ROOM, INSTRUCTIONS);
	}

	@ParameterizedTest
	@ValueSource(strings = {"yes-id", "no-id"})
	void confirmationVisibilityMatchesTheJsonAndRequiresAnAttendanceOption(String option) throws Exception {
		events.save(responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.ON_CONFIRMATION));
		answer(option);
		assertVisibility(option.equals("yes-id"));
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1})
	void hoursBeforeMatchesTheJsonBeforeAtAndAfterTheThreshold(int offset) throws Exception {
		events.save(responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.HOURS_BEFORE));
		fixed(NOW.plusSeconds(offset));
		assertVisibility(offset >= 0);
	}

	@Test
	void alwaysShowsTheUrlEvenWithoutAResponseLongBeforeTheEvent() throws Exception {
		events.save(responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.ALWAYS));
		fixed(NOW.minusSeconds(86400));
		assertVisibility(true);
	}

	@Test
	void changingAttendanceShowsAndHidesTheUrlWithoutExposingTheResponseContactOrComment(CapturedOutput output) throws Exception {
		events.save(responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.ON_CONFIRMATION));
		assertVisibility(false);
		answer("yes-id");
		assertVisibility(true);
		answer("no-id");
		assertVisibility(false);
		assertThat(output.getAll()).doesNotContain(token, "private-calendar-comment", "calendar-response@example.com");
	}

	@Test
	void anAbsentResponseOptionDoesNotConfirmOrBreakTheCalendar() throws Exception {
		events.save(responseEvent(EventStatus.PUBLISHED, true, true, null, LinkVisibility.ON_CONFIRMATION));
		responses.insert(new com.gtog.event.domain.model.GuestResponse("event-id", token, "removed-option", null, null, NOW, NOW));
		assertVisibility(false);
	}

	@Test
	void downloadingDoesNotRecordAnOpeningOrIncrementTheEventsVersion() throws Exception {
		var first = guest(101, InvitationStatus.NOT_SENT, null);
		events.save(event(EventStatus.PUBLISHED, LinkVisibility.ALWAYS, first));
		download();
		var stored = events.findById("event-id").orElseThrow();
		assertThat(stored.getGuest(first.getId()).getInvitationStatus()).isEqualTo(InvitationStatus.NOT_SENT);
		assertThat(stored.getGuest(first.getId()).getOpenedAt()).isNull();
		assertThat(stored.getVersion()).isZero();
	}

	@Test
	void documentsTheCalendarAndItsErrorMediaTypes() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}/calendar'].get.responses['200'].content['text/calendar']").exists())
				.andExpect(jsonPath("$.paths['/api/invitations/{token}/calendar'].get.responses['409'].content['application/problem+json']").exists());
	}

	private void assertVisibility(boolean visible) throws Exception {
		String body = download();
		String json = mvc.perform(get(invitation())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		if (visible) {
			assertThat(body).contains(URL);
			assertThat(json).contains(URL);
		} else {
			assertThat(body).doesNotContain(URL);
			assertThat(json).doesNotContain(URL, PASSWORD, ROOM, INSTRUCTIONS);
		}
		assertThat(body).doesNotContain(PASSWORD, ROOM, INSTRUCTIONS, token, "private-calendar-comment", "calendar-response@example.com");
	}
	private void answer(String option) throws Exception {
		mvc.perform(put(invitation() + "/response").contentType(MediaType.APPLICATION_JSON).content("""
				{"responseOptionId":"%s","comment":"private-calendar-comment","email":"calendar-response@example.com"}
				""".formatted(option))).andExpect(status().isOk());
	}
	private String download() throws Exception {
		return mvc.perform(get(path()).accept("text/calendar")).andExpect(status().isOk())
				.andExpect(content().contentType("text/calendar;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"event.ics\""))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
	}
	private String error(int code) throws Exception {
		MvcResult result = mvc.perform(get(path()).accept("text/calendar"))
				.andExpect(status().is(code)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().doesNotExist("Content-Disposition"))
				.andExpect(jsonPath("$.instance").value("/api/invitations")).andReturn();
		return result.getResponse().getContentAsString();
	}
	private void fixed(Instant now) { when(clock.instant()).thenReturn(Clock.fixed(now, ZoneOffset.UTC).instant()); }
	private String invitation() { return "/api/invitations/" + token; }
	private String path() { return invitation() + "/calendar"; }
}
