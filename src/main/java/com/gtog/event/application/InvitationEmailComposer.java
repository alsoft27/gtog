package com.gtog.event.application;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.Guest;
import com.gtog.event.domain.model.InvitationEmail;
import com.gtog.event.domain.model.Modality;
import com.gtog.event.domain.model.Venue;

public final class InvitationEmailComposer {
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("es"));
	private final String publicBaseUrl;

	public InvitationEmailComposer(String publicBaseUrl) {
		URI base = URI.create(publicBaseUrl);
		if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null
				|| base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
			throw new IllegalArgumentException("Invalid public base URL");
		}
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	public InvitationEmail compose(Event event, Guest guest, String hostName) {
		String host = singleLine(hostName);
		String title = singleLine(event.getTitle());
		String platform = event.getModality() == Modality.ONLINE ? event.getOnlineAccess().platform() : null;
		return new InvitationEmail(guest.getEmail(), title, renderBody(host, singleLine(guest.getName()), title,
				event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), event.getVenue(), platform,
				publicBaseUrl + "/r/" + guest.getToken()));
	}

	// El renderizador solo recibe la plataforma: no puede acceder a los secretos de OnlineAccess.
	private static String renderBody(String host, String guestName, String title, LocalDateTime startsAt,
			LocalDateTime endsAt, String timeZone, Venue venue, String platform, String invitationUrl) {
		StringBuilder body = new StringBuilder("Hola ").append(guestName).append(",\n\n")
				.append(host).append(" te invita a ").append(title).append(".\n")
				.append("Inicio: ").append(startsAt.format(DATE_TIME)).append("\n")
				.append("Fin: ").append(endsAt.format(DATE_TIME)).append("\n")
				.append("Zona horaria: ").append(timeZone).append("\n");
		if (venue != null) {
			body.append("Lugar: ").append(singleLine(venue.placeName())).append(" — ")
					.append(singleLine(venue.address())).append("\n");
		}
		else {
			// El correo nunca recibe el acceso de la sala, aunque fuera visible para el invitado.
			body.append("Plataforma: ").append(singleLine(platform)).append("\n");
		}
		body.append("\nResponde desde: ").append(invitationUrl).append("\n\n")
				.append("Este mensaje no admite respuestas: responde desde el enlace.\n\n")
				.append("Enviado por ").append(host).append(" a través de gtog. Si no esperabas esta invitación, ignórala.");
		return body.toString();
	}

	private static String singleLine(String value) {
		return value.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip();
	}
}
