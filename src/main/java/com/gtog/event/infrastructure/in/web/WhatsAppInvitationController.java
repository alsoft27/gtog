package com.gtog.event.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.gtog.event.domain.port.in.GenerateWhatsAppLinksCommand;
import com.gtog.event.domain.port.in.GenerateWhatsAppLinksUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/invitations")
@Tag(name = "WhatsApp invitations", description = "Envío asistido desde la cuenta de WhatsApp del anfitrión")
public class WhatsAppInvitationController {
	private final GenerateWhatsAppLinksUseCase links;
	public WhatsAppInvitationController(GenerateWhatsAppLinksUseCase links) { this.links = links; }

	@Operation(summary = "Genera enlaces de WhatsApp", description = "Solo PUBLISHED, autenticado y con CSRF. No escribe datos ni confirma envío. "
			+ "Sin guestIds selecciona todos. Texto en español, URL máxima conservadora de 2000 caracteres; READY o SKIPPED por invitado. "
			+ "La respuesta contiene credenciales: no registrar ni cachear. Abrir de uno en uno tras un gesto del usuario.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Enlaces y textos o motivos de salto; no-store exacto"),
			@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "Evento ajeno, inexistente o invitado ajeno", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "Evento no publicado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Lista vacía, repetidos o máximo superado", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/whatsapp-links")
	public ResponseEntity<WhatsAppLinksResponse> generate(@PathVariable String id, @RequestBody(required = false) WhatsAppLinksRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(WhatsAppLinksResponse.from(links.generate(
				new GenerateWhatsAppLinksCommand(principal.getUserId(), principal.getName(), id, request == null ? null : request.guestIds()))));
	}
}
