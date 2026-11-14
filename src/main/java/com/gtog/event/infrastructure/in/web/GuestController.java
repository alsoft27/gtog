package com.gtog.event.infrastructure.in.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.event.domain.port.in.AddGuestCommand;
import com.gtog.event.domain.port.in.AddGuestUseCase;
import com.gtog.event.domain.port.in.UpdateGuestCommand;
import com.gtog.event.domain.port.in.UpdateGuestUseCase;
import com.gtog.event.domain.port.in.DeleteGuestCommand;
import com.gtog.event.domain.port.in.DeleteGuestUseCase;
import com.gtog.event.domain.port.in.ListGuestUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/events/{id}/guests")
@Tag(name = "Guests", description = "Gestion de invitados del evento por su anfitrion")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "No hay sesion autenticada", content = @Content),
		@ApiResponse(responseCode = "404", description = "Evento ajeno, evento inexistente o invitado inexistente",
				content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
						schema = @Schema(implementation = ProblemDetail.class))) })
public class GuestController {

	private final AddGuestUseCase addGuest;
	private final UpdateGuestUseCase updateGuest;
	private final DeleteGuestUseCase deleteGuest;
	private final ListGuestUseCase listGuests;

	public GuestController(AddGuestUseCase addGuest, UpdateGuestUseCase updateGuest,
			DeleteGuestUseCase deleteGuest, ListGuestUseCase listGuests) {
		this.addGuest = addGuest;
		this.updateGuest = updateGuest;
		this.deleteGuest = deleteGuest;
		this.listGuests = listGuests;
	}

	@Operation(summary = "Anade un invitado", description = "Permitido en DRAFT y PUBLISHED. Genera el token "
			+ "y devuelve invitationPath para copiar el enlace. Requiere CSRF.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Invitado creado con cabecera Location"),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o invalido", content = @Content),
			@ApiResponse(responseCode = "409", description = "Contacto o token duplicado, evento cerrado o cambio concurrente",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Nombre o contacto invalido",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping
	public ResponseEntity<GuestInvitationResponse> add(@PathVariable String id, @Valid @RequestBody GuestRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		var guest = addGuest.add(new AddGuestCommand(principal.getUserId(), id, request.name(), request.email(), request.phone()));
		return ResponseEntity.created(URI.create("/api/events/" + id + "/guests/" + guest.getId()))
				.body(GuestInvitationResponse.from(guest));
	}

	@Operation(summary = "Edita un invitado", description = "Reemplaza nombre y contactos en DRAFT o PUBLISHED; "
			+ "conserva identidad, token y estado de envio. No devuelve el enlace. Requiere CSRF.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Invitado actualizado"),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o invalido", content = @Content),
			@ApiResponse(responseCode = "409", description = "Contacto duplicado, evento cerrado o cambio concurrente",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Nombre o contacto invalido",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{guestId}")
	public GuestResponse update(@PathVariable String id, @PathVariable String guestId,
			@Valid @RequestBody GuestRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
		return GuestResponse.from(updateGuest.update(new UpdateGuestCommand(principal.getUserId(), id, guestId,
				request.name(), request.email(), request.phone())));
	}

	@Operation(summary = "Elimina un invitado", description = "Permitido en DRAFT y PUBLISHED. Una respuesta existente "
			+ "requiere confirmDeleteResponse=true. Primero se retira el invitado y despues su respuesta. Requiere CSRF.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Invitado eliminado", content = @Content),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o invalido", content = @Content),
			@ApiResponse(responseCode = "409", description = "Evento cerrado, cambio concurrente o falta confirmacion de cascada",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "500", description = "Fallo de persistencia; si falla la limpieza posterior, el invitado ya fue retirado",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@DeleteMapping("/{guestId}")
	public ResponseEntity<Void> delete(@PathVariable String id, @PathVariable String guestId,
			@RequestParam(defaultValue = "false") boolean confirmDeleteResponse,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		deleteGuest.delete(new DeleteGuestCommand(principal.getUserId(), id, guestId, confirmDeleteResponse));
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Lista los invitados", description = "Solo para el propietario. Incluye invitationPath "
			+ "para copiar enlaces, tambien tras recargar la pantalla. Disponible en todos los estados.")
	@ApiResponse(responseCode = "200", description = "Invitados del evento; lista vacia si no hay ninguno")
	@GetMapping
	public List<GuestInvitationResponse> list(@PathVariable String id,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return listGuests.list(principal.getUserId(), id).stream().map(GuestInvitationResponse::from).toList();
	}
}
