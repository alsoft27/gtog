package com.gtog.event.infrastructure.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.event.domain.port.in.GetEventDashboardUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/dashboard")
@Tag(name = "Events")
public class EventDashboardController {
	private final GetEventDashboardUseCase dashboard;

	public EventDashboardController(GetEventDashboardUseCase dashboard) { this.dashboard = dashboard; }

	@Operation(summary = "Consulta el panel del anfitrion", description = "Solo el propietario, en cualquier estado. "
			+ "Contadores derivados de invitados vivos, filas en orden de alta y opciones en su orden. "
			+ "Conserva invitationPath como GET /guests; no expone el email de respuesta. No escribe ni cambia version. "
			+ "Solo el 200 fija Cache-Control: no-store exacto.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Panel privado", content = @Content(schema = @Schema(implementation = EventDashboardResponse.class))),
		@ApiResponse(responseCode = "401", description = "Sin sesion", content = @Content),
		@ApiResponse(responseCode = "404", description = "Evento inexistente o de otro anfitrion", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping
	public ResponseEntity<EventDashboardResponse> get(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(EventDashboardResponse.from(dashboard.getDashboard(principal.getUserId(), id)));
	}
}
