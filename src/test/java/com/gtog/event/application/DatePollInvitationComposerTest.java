package com.gtog.event.application;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.gtog.event.domain.model.*;
import static com.gtog.event.DatePollTestFixtures.*;
import static com.gtog.event.InvitationTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

class DatePollInvitationComposerTest {
	private final Guest guest = guest(101, InvitationStatus.NOT_SENT, null);
	@Test void emailListsDatesOptionalTimesAndDeadlineWithoutAnInventedEnd() {
		var mail = new InvitationEmailComposer("https://gtog.example.com").compose(published(null), guest, "Anfitrión");
		assertThat(mail.body()).contains("Fecha por definir", "10/05/2027 a las 20:00", "11/05/2027 (sin hora definida)",
				"12/05/2027 a las 21:00", "Zona horaria: Europe/Madrid", "Plazo para responder: 09/05/2027 18:00",
				"Lugar: Sala — Dirección", "Este mensaje no admite respuestas: responde desde el enlace.", "Enviado por Anfitrión a través de gtog.")
				.doesNotContain("Inicio:", "Fin:", "00:00");
	}
	@Test void emailForAnOnlinePollOnlyIncludesThePlatform() {
		var mail = new InvitationEmailComposer("https://gtog.example.com").compose(published(LinkVisibility.ALWAYS), guest, "Anfitrión");
		assertThat(mail.body()).contains("Plataforma: Zoom", "Fechas propuestas").doesNotContain(URL, ROOM, PASSWORD, INSTRUCTIONS);
	}
	@Test void whatsappReusesTheSamePublicScheduleAndPreservesUtf8WhenDecoded() {
		var link = new WhatsAppInvitationComposer("https://gtog.example.com").compose(published(null), guest, "Anfitrión ñ 🎉");
		String query = URI.create(link.url()).getRawQuery();
		assertThat(URLDecoder.decode(query.substring("text=".length()), StandardCharsets.UTF_8)).isEqualTo(link.message());
		assertThat(query).doesNotContain("+");
		assertThat(link.message()).contains("10/05/2027 a las 20:00", "11/05/2027 (sin hora definida)", "09/05/2027 18:00", "Anfitrión ñ 🎉")
				.doesNotContain("Fin:", "Inicio:", "00:00", "no admite respuestas", "a través de gtog");
	}
	@Test void whatsappDoesNotExposeTheRoomEvenWhenItsVisibilityIsAlways() {
		var link = new WhatsAppInvitationComposer("https://gtog.example.com").compose(published(LinkVisibility.ALWAYS), guest, "Host");
		assertThat(link.message()).contains("Plataforma: Zoom").doesNotContain(URL, ROOM, PASSWORD, INSTRUCTIONS);
	}
	@Test void publicViewsDoNotTreatAttendanceDocumentsAsPollAvailability() {
		Event event = published(null); event.addGuest(guest);
		var view = InvitationViewFactory.create(event, guest, Instant.parse("2027-03-01T10:00:00Z"),
				new GuestResponse(event.getId(), guest.getToken(), "old-option", "private-comment", "private-email@example.com", Instant.EPOCH, Instant.EPOCH));
		assertThat(view.datePoll().candidateDates()).hasSize(3);
		assertThat(view.myResponse()).isNull(); assertThat(view.canRespond()).isFalse(); assertThat(view.canChangeResponse()).isFalse();
	}
}
