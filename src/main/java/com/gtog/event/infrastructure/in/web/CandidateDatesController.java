package com.gtog.event.infrastructure.in.web;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.gtog.event.domain.port.in.ReplaceCandidateDatesCommand;
import com.gtog.event.domain.port.in.ReplaceCandidateDatesUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/events/{id}/candidate-dates")
@Tag(name = "Events")
public class CandidateDatesController {
	private final ReplaceCandidateDatesUseCase useCase;
	public CandidateDatesController(ReplaceCandidateDatesUseCase useCase) { this.useCase = useCase; }

	@Operation(summary = "Reemplaza las candidatas de una consulta", description = "DRAFT o PUBLISHED, con sesión y CSRF. Un cambio real incrementa pollRevision y quita la selección; repetir o reordenar no invalida. Un reintento idéntico no escribe. La propiedad se comprueba en aplicación.")
	@ApiResponse(responseCode = "200", description = "Evento actualizado", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EventResponse.class)))
	@ApiResponse(responseCode = "400", description = "Cuerpo ausente o formato ilegible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "CSRF inválido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Evento inexistente o de otro anfitrión", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Estado o tipo incompatible, revisión obsoleta o conflicto de versión", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Revisión ausente o negativa, candidatas inválidas, duplicadas o ids desconocidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping
	public EventResponse replace(@PathVariable String id, @RequestBody ReplaceCandidateDatesRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return EventResponse.from(useCase.replaceCandidateDates(new ReplaceCandidateDatesCommand(principal.getUserId(), id,
				request.pollRevision(), request.toDomain())));
	}
}
