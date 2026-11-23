package com.gtog.event.infrastructure.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.event.domain.model.PublicInvitation;
import com.gtog.event.domain.port.in.ChangePublicInvitationUseCase;
import com.gtog.event.domain.port.in.GetPublicInvitationUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/public-invitation")
@Tag(name = "Events")
@ApiResponses({@ApiResponse(responseCode = "200", description = "Intención y enlace, null antes de publicar o desactivado"),
		@ApiResponse(responseCode = "401", description = "Sin sesión"),
		@ApiResponse(responseCode = "404", description = "Evento inexistente o ajeno")})
public class PublicInvitationController {
	private final GetPublicInvitationUseCase query;
	private final ChangePublicInvitationUseCase change;
	public PublicInvitationController(GetPublicInvitationUseCase query, ChangePublicInvitationUseCase change) {
		this.query = query; this.change = change;
	}
	public record Request(@Schema(description = "Activar o desactivar la admisión por enlace") @NotNull Boolean enabled) {}
	@GetMapping
	@Operation(summary = "Consulta el enlace público, también si quedó inerte al cerrar el evento")
	public ResponseEntity<PublicInvitationResponse> get(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return response(query.get(principal.getUserId(), id));
	}
	@PutMapping
	@Operation(summary = "Cambia la intención en borrador o activa/revoca el enlace publicado")
	@ApiResponses({@ApiResponse(responseCode = "400", description = "Cuerpo inválido"),
			@ApiResponse(responseCode = "403", description = "CSRF inválido"),
			@ApiResponse(responseCode = "409", description = "Evento cerrado o cambio concurrente de estado")})
	public ResponseEntity<PublicInvitationResponse> put(@PathVariable String id, @Valid @RequestBody Request request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return response(change.change(principal.getUserId(), id, request.enabled()).orElse(new PublicInvitation(false, null)));
	}
	private ResponseEntity<PublicInvitationResponse> response(PublicInvitation invitation) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PublicInvitationResponse.from(invitation));
	}
}
