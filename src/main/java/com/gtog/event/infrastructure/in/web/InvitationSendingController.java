package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.gtog.event.domain.port.in.SendInvitationsCommand;
import com.gtog.event.domain.port.in.SendInvitationsUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/invitations")
@Tag(name = "Invitation sending", description = "Envío de invitaciones por correo por el anfitrión")
public class InvitationSendingController {
	private final SendInvitationsUseCase sendInvitations;
	public InvitationSendingController(SendInvitationsUseCase sendInvitations) { this.sendInvitations = sendInvitations; }

	@Operation(summary = "Envía invitaciones por correo", description = "Síncrono, solo PUBLISHED, autenticado y con CSRF. "
			+ "Omitir guestIds selecciona todos. Resultado individual, sin reintentos; SENT significa aceptado por el proveedor, no entregado. "
			+ "Corte tras tres fallos consecutivos de proveedor o al agotar cuota diaria. Quien ya respondió no recibe correo.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Resultado por invitado y cuota final; Cache-Control: no-store"),
			@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "Evento ajeno o inexistente, o invitado ajeno", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "Evento no publicado o cambio concurrente", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Lote vacío, ids repetidos o máximo superado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/send")
	public ResponseEntity<SendInvitationsResponse> send(@PathVariable String id,
			@RequestBody(required = false) SendInvitationsRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
		var result = sendInvitations.send(new SendInvitationsCommand(principal.getUserId(), principal.getName(), id,
				request == null ? null : request.guestIds()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SendInvitationsResponse.from(result));
	}
}
