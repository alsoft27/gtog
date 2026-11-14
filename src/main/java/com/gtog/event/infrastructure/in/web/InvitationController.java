package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.event.domain.port.in.GetInvitationUseCase;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Consulta publica del invitado mediante su credencial de enlace")
public class InvitationController {

	private final GetInvitationUseCase getInvitation;

	public InvitationController(GetInvitationUseCase getInvitation) {
		this.getInvitation = getInvitation;
	}

	@Operation(summary = "Consulta una invitacion", description = "Sin sesion ni CSRF. Publicado, cancelado y finalizado "
			+ "devuelven 200. Borrador y token desconocido devuelven el mismo 404. Solo un evento publicado "
			+ "puede mostrar acceso online o registrar la primera apertura. Cache-Control: no-store.")
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
}
