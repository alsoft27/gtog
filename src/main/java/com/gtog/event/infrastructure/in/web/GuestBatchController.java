package com.gtog.event.infrastructure.in.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.event.domain.model.GuestInput;
import com.gtog.event.domain.port.in.AddGuestsUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/guests/batch")
@Tag(name = "Guests")
public class GuestBatchController {
	private final AddGuestsUseCase addGuests;
	public GuestBatchController(AddGuestsUseCase addGuests) { this.addGuests = addGuests; }
	@Operation(summary = "Añade invitados en una sola operación", description = "Todo o nada, en DRAFT y PUBLISHED. JSON ilegible → 400 antes de comprobar propiedad. Con JSON válido: propiedad, estado, tamaño, validación completa y finalmente duplicados/capacidad. No exige correo verificado. No envía invitaciones.")
	@ApiResponses({ @ApiResponse(responseCode = "201", description = "Lista creada en el mismo orden, Location de la colección y no-store exacto"),
			@ApiResponse(responseCode = "400", description = "Cuerpo ausente o ilegible", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "Evento inexistente o ajeno", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "Estado cerrado, errors con index/code=DUPLICATE_IN_EVENT o code=CAPACITY_EXCEEDED sin errors", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Lista vacía, null o excesiva; errors con index de base cero y BLANK_NAME, NO_CONTACT, INVALID_EMAIL, INVALID_PHONE o DUPLICATE_IN_BATCH", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping public ResponseEntity<List<GuestInvitationResponse>> add(@PathVariable String id,
			@RequestBody GuestBatchRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
		var inputs = request.guests() == null ? null : request.guests().stream()
				.map(guest -> guest == null ? null : new GuestInput(guest.name(), guest.email(), guest.phone())).toList();
		var created = addGuests.add(principal.getUserId(), id, inputs);
		return ResponseEntity.created(URI.create("/api/events/" + id + "/guests")).cacheControl(CacheControl.noStore())
				.body(created.stream().map(GuestInvitationResponse::from).toList());
	}
}
