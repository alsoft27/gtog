package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import com.gtog.event.domain.port.in.DownloadInvitationCalendarUseCase;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.gtog.event.domain.port.in.RecordGuestResponseUseCase;
import com.gtog.event.domain.port.in.RecordGuestResponseCommand;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.event.domain.port.in.GetInvitationUseCase;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Consulta publica del invitado mediante su credencial de enlace")
public class InvitationController {

	private final GetInvitationUseCase getInvitation;
	private final RecordGuestResponseUseCase recordResponse;
	private final DownloadInvitationCalendarUseCase downloadCalendar;

	public InvitationController(GetInvitationUseCase getInvitation, RecordGuestResponseUseCase recordResponse,
			DownloadInvitationCalendarUseCase downloadCalendar) {
		this.getInvitation = getInvitation;
		this.recordResponse = recordResponse;
		this.downloadCalendar = downloadCalendar;
	}

	@Operation(summary = "Consulta una invitacion", description = "Sin sesion ni CSRF. Publicado, cancelado y finalizado "
			+ "devuelven 200. Borrador y token desconocido devuelven el mismo 404. Solo un evento publicado "
			+ "puede mostrar acceso online o registrar la primera apertura. DATE_POLL muestra candidatas y revisión sin fechas definitivas. Cache-Control: no-store.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Vista publica sin datos de contacto ni tokens"),
			@ApiResponse(responseCode = "404", description = "Invitacion no disponible",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping("/{token}")
	public ResponseEntity<InvitationResponse> get(@Parameter(description = "Credencial del enlace individual") @PathVariable String token) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(InvitationResponse.from(getInvitation.getInvitation(token)));
	}

	@Operation(summary = "Registra o cambia la asistencia de fecha fija", description = "Publico, sin sesion ni CSRF. Solo PUBLISHED, "
			+ "hasta el plazo inclusive y antes del fin. Los cambios requieren permiso. DATE_POLL devuelve 409; la disponibilidad se incorpora por separado. El correo se guarda sin enviar nada.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Invitacion con la respuesta actualizada",
			content = @Content(schema = @Schema(implementation = InvitationResponse.class))),
		@ApiResponse(responseCode = "400", description = "JSON invalido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
		@ApiResponse(responseCode = "404", description = "Invitacion no disponible", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
		@ApiResponse(responseCode = "409", description = "Estado, tipo de evento, plazo, fin o cambio no permitido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
		@ApiResponse(responseCode = "422", description = "Opcion, comentario o correo invalidos", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{token}/response")
	public ResponseEntity<InvitationResponse> respond(@PathVariable String token, @RequestBody RecordGuestResponseRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(InvitationResponse.from(
				recordResponse.recordResponse(new RecordGuestResponseCommand(token, request.responseOptionId(), request.comment(), request.email()))));
	}

	@Operation(summary = "Descarga el calendario del evento", description = "Publico, sin sesion ni CSRF. Archivo iCalendar "
			+ "con la zona del evento y enlace online solo si es visible para el invitado. CANCELLED devuelve 409; FINISHED con fecha fija, 200. DATE_POLL sin fecha definitiva devuelve 409. "
			+ "Borrador y token desconocido producen el mismo 404. No registra una apertura. Cache-Control: no-store.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Archivo event.ics",
				content = @Content(mediaType = "text/calendar", schema = @Schema(type = "string", format = "binary"))),
		@ApiResponse(responseCode = "404", description = "Invitacion no disponible",
				content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
		@ApiResponse(responseCode = "409", description = "Evento cancelado o sin fecha definitiva",
				content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping(value = "/{token}/calendar", produces = "text/calendar")
	public ResponseEntity<byte[]> calendar(@PathVariable String token) {
		return ResponseEntity.ok().contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("event.ics").build().toString())
				.cacheControl(CacheControl.noStore()).body(downloadCalendar.downloadCalendar(token));
	}
}
