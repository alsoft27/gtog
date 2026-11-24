package com.gtog.event.infrastructure.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.EventEdit;
import com.gtog.event.domain.model.OnlineAccess;
import com.gtog.event.domain.model.ResponseOptionDraft;
import com.gtog.event.domain.model.ResponseOptionEdit;
import com.gtog.event.domain.model.Venue;
import com.gtog.event.domain.port.in.CancelEventUseCase;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineUseCase;
import com.gtog.event.domain.port.in.ChangeResponseDeadlineCommand;
import com.gtog.event.domain.port.in.CreateEventCommand;
import com.gtog.event.domain.port.in.CreateEventUseCase;
import com.gtog.event.domain.port.in.GetEventByIdUseCase;
import com.gtog.event.domain.port.in.ListEventsByHostUseCase;
import com.gtog.event.domain.port.in.PublishEventUseCase;
import com.gtog.event.domain.port.in.ReplaceOnlineAccessCommand;
import com.gtog.event.domain.port.in.ReplaceOnlineAccessUseCase;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsCommand;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsUseCase;
import com.gtog.event.domain.port.in.ReplaceVenueCommand;
import com.gtog.event.domain.port.in.ReplaceVenueUseCase;
import com.gtog.event.domain.port.in.UpdateEventCommand;
import com.gtog.event.domain.port.in.UpdateEventUseCase;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Creacion y consulta de eventos por parte del anfitrion")
public class EventController {

	private final CreateEventUseCase createEventUseCase;
	private final GetEventByIdUseCase getEventByIdUseCase;
	private final ListEventsByHostUseCase listEventsByHostUseCase;
	private final ReplaceResponseOptionsUseCase replaceResponseOptionsUseCase;
	private final ReplaceVenueUseCase replaceVenueUseCase;
	private final ReplaceOnlineAccessUseCase replaceOnlineAccessUseCase;
	private final PublishEventUseCase publishEventUseCase;
	private final CancelEventUseCase cancelEventUseCase;
	private final UpdateEventUseCase updateEventUseCase;
	private final ChangeResponseDeadlineUseCase changeResponseDeadlineUseCase;

	public EventController(CreateEventUseCase createEventUseCase, GetEventByIdUseCase getEventByIdUseCase,
			ListEventsByHostUseCase listEventsByHostUseCase,
			ReplaceResponseOptionsUseCase replaceResponseOptionsUseCase, ReplaceVenueUseCase replaceVenueUseCase,
			ReplaceOnlineAccessUseCase replaceOnlineAccessUseCase, PublishEventUseCase publishEventUseCase,
			CancelEventUseCase cancelEventUseCase, UpdateEventUseCase updateEventUseCase,
			ChangeResponseDeadlineUseCase changeResponseDeadlineUseCase) {
		this.createEventUseCase = createEventUseCase;
		this.getEventByIdUseCase = getEventByIdUseCase;
		this.listEventsByHostUseCase = listEventsByHostUseCase;
		this.replaceResponseOptionsUseCase = replaceResponseOptionsUseCase;
		this.replaceVenueUseCase = replaceVenueUseCase;
		this.replaceOnlineAccessUseCase = replaceOnlineAccessUseCase;
		this.publishEventUseCase = publishEventUseCase;
		this.cancelEventUseCase = cancelEventUseCase;
		this.updateEventUseCase = updateEventUseCase;
		this.changeResponseDeadlineUseCase = changeResponseDeadlineUseCase;
	}

	@Operation(summary = "Crea un evento", description = "El anfitrion crea un evento nuevo. El evento se crea en "
			+ "estado DRAFT. FIXED_DATE usa inicio y fin; DATE_POLL usa datePoll.candidateDates y un plazo obligatorio, sin fechas definitivas. Requiere correo verificado, comprobado en la base de datos en cada creación.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Evento creado. La cabecera Location apunta al recurso.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "403", description = "CSRF inválido o correo no verificado (code=EMAIL_NOT_VERIFIED).", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "400", description = "La peticion no cumple el formato esperado: falta un "
					+ "campo obligatorio o tiene un tipo invalido.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "La peticion tiene el formato correcto pero viola una "
					+ "regla de negocio, por ejemplo que la fecha de fin no sea posterior a la de inicio o que la "
					+ "zona horaria no exista.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping
	public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		List<ResponseOptionDraft> responseOptionDrafts = request.responseOptions() == null ? null
				: request.responseOptions().stream()
						.map(option -> new ResponseOptionDraft(option.label(), option.countsAsAttendance()))
						.toList();
		Venue venue = request.venue() == null ? null
				: new Venue(request.venue().placeName(), request.venue().address(), request.venue().latitude(),
						request.venue().longitude(), request.venue().placeId(), request.venue().directions());
		OnlineAccess onlineAccess = request.onlineAccess() == null ? null
				: new OnlineAccess(request.onlineAccess().platform(), request.onlineAccess().url(),
						request.onlineAccess().roomId(), request.onlineAccess().password(),
						request.onlineAccess().instructions(), request.onlineAccess().linkVisibility(),
						request.onlineAccess().hoursBefore());
		CreateEventCommand command = new CreateEventCommand(
				principal.getUserId(),
				request.title(),
				request.description(),
				request.startsAt(),
				request.endsAt(),
				request.timeZone(),
				request.modality(),
				responseOptionDrafts,
				request.allowComment(),
				request.allowResponseChange(),
				request.responseDeadline(),
				venue,
				onlineAccess, request.publicInvitationEnabled(), request.eventType(), request.datePoll() == null ? null : request.datePoll().toDomain());
		Event event = createEventUseCase.createEvent(command);
		return ResponseEntity.created(URI.create("/api/events/" + event.getId())).body(EventResponse.from(event));
	}

	@Operation(summary = "Consulta un evento", description = "Devuelve el detalle completo de un evento por su id.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Evento encontrado.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping("/{id}")
	public EventResponse getEvent(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return EventResponse.from(getEventByIdUseCase.getEventById(principal.getUserId(), id));
	}

	@Operation(summary = "Lista los eventos de un anfitrion",
			description = "Devuelve una proyeccion ligera de los eventos del anfitrion autenticado, sin paginacion. "
					+ "Incluye totalGuests, answered, pending y attending calculados sobre los invitados actuales. "
					+ "El anfitrion se obtiene de la sesion; no se acepta hostId como parametro de consulta.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Listado de eventos del anfitrion, puede ser vacio.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							array = @ArraySchema(schema = @Schema(implementation = EventSummaryResponse.class)))),
			@ApiResponse(responseCode = "401", description = "No hay una sesion autenticada.",
					content = @Content) })
	@GetMapping
	public List<EventSummaryResponse> listEvents(@AuthenticationPrincipal AuthenticatedUser principal) {
		return listEventsByHostUseCase.listEventsByHost(principal.getUserId()).stream()
				.map(EventSummaryResponse::from).toList();
	}

	@Operation(summary = "Edita los datos basicos de un evento",
			description = "Actualiza titulo, descripcion, fechas, zona horaria, modalidad, opciones de respuesta y "
					+ "configuracion. Solo permitido mientras el evento esta en DRAFT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Evento actualizado.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en DRAFT.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Viola una regla de negocio: titulo en blanco, periodo "
					+ "invalido, zona horaria invalida, cambio de modalidad sin bloque de ubicacion, o fecha limite "
					+ "posterior a startsAt.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}")
	public EventResponse updateEvent(@PathVariable String id, @Valid @RequestBody UpdateEventRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		Venue venue = request.venue() == null ? null
				: new Venue(request.venue().placeName(), request.venue().address(), request.venue().latitude(),
						request.venue().longitude(), request.venue().placeId(), request.venue().directions());
		OnlineAccess onlineAccess = request.onlineAccess() == null ? null
				: new OnlineAccess(request.onlineAccess().platform(), request.onlineAccess().url(),
						request.onlineAccess().roomId(), request.onlineAccess().password(),
						request.onlineAccess().instructions(), request.onlineAccess().linkVisibility(),
						request.onlineAccess().hoursBefore());
		boolean allowComment = request.allowComment() != null && request.allowComment();
		boolean allowResponseChange = request.allowResponseChange() == null || request.allowResponseChange();
		EventEdit edit = new EventEdit(request.title(), request.description(), request.startsAt(), request.endsAt(),
				request.timeZone(), request.modality(), venue, onlineAccess, allowComment, allowResponseChange,
				request.responseDeadline(), request.eventType());
		UpdateEventCommand command = new UpdateEventCommand(principal.getUserId(), id, edit);
		return EventResponse.from(updateEventUseCase.update(command));
	}

	@Operation(summary = "Cambia el plazo de respuesta",
			description = "Permitido en DRAFT o en PUBLISHED antes del instante de inicio. null elimina el plazo; "
					+ "un plazo pasado se acepta. Fecha local interpretada con la zona del evento. Requiere sesion y CSRF.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Plazo actualizado",
					content = @Content(schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "401", description = "Sin sesion", content = @Content),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o invalido", content = @Content),
			@ApiResponse(responseCode = "404", description = "Evento inexistente o ajeno",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "Estado o instante no permiten el cambio, o cambio concurrente",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Plazo posterior al inicio",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}/response-deadline")
	public EventResponse changeResponseDeadline(@PathVariable String id, @RequestBody ChangeResponseDeadlineRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return EventResponse.from(changeResponseDeadlineUseCase.changeResponseDeadline(
				new ChangeResponseDeadlineCommand(principal.getUserId(), id, request.responseDeadline())));
	}

	@Operation(summary = "Publica un evento",
			description = "Transicion DRAFT → PUBLISHED. Precondiciones: venue u onlineAccess segun modalidad, y al "
					+ "menos dos opciones de respuesta en FIXED_DATE. DATE_POLL publica las candidatas y su plazo, sin fecha definitiva.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Evento publicado.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en DRAFT y no puede publicarse.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "El evento no cumple las precondiciones de publicacion: "
					+ "falta venue u onlineAccess, o FIXED_DATE tiene menos de dos opciones de respuesta.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/{id}/publish")
	public EventResponse publishEvent(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return EventResponse.from(publishEventUseCase.publish(principal.getUserId(), id));
	}

	@Operation(summary = "Cancela un evento",
			description = "Transicion PUBLISHED → CANCELLED. Irreversible. Solo posible desde PUBLISHED; "
					+ "un borrador no se puede cancelar aunque ya tenga invitados.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Evento cancelado.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en PUBLISHED y no puede cancelarse.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/{id}/cancel")
	public EventResponse cancelEvent(@PathVariable String id,
			@RequestBody(required = false) CancelEventRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		String reason = request != null ? request.reason() : null;
		return EventResponse.from(cancelEventUseCase.cancel(principal.getUserId(), id, reason, Instant.now()));
	}

	@Operation(summary = "Reemplaza las opciones de respuesta de un evento",
			description = "Sustituye la lista completa en DRAFT o PUBLISHED. En PUBLISHED no se puede eliminar una opcion "
					+ "con respuestas ni cambiar su countsAsAttendance. Para conservar el id, envialo de vuelta; una opcion "
					+ "sin id se crea como nueva. allowComment, allowResponseChange y responseDeadline se gestionan "
					+ "a traves de PUT /api/events/{id}.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Opciones reemplazadas. Devuelve el evento completo.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "Evento cerrado, conflicto de version o intento de eliminar "
					+ "una opcion con respuestas o cambiar su countsAsAttendance en PUBLISHED.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "La peticion viola una regla de negocio: numero de "
					+ "opciones fuera de 2-5, ninguna opcion cuenta como asistencia, etiquetas vacias o duplicadas, "
					+ "o un id de opcion que no pertenece al evento.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}/response-options")
	public EventResponse replaceResponseOptions(@PathVariable String id,
			@Valid @RequestBody ReplaceResponseOptionsRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		List<ResponseOptionEdit> edits = request.responseOptions().stream()
				.map(option -> new ResponseOptionEdit(option.id(), option.label(), option.countsAsAttendance()))
				.toList();
		ReplaceResponseOptionsCommand command = new ReplaceResponseOptionsCommand(principal.getUserId(), id, edits);
		return EventResponse.from(replaceResponseOptionsUseCase.replaceResponseOptions(command));
	}

	@Operation(summary = "Reemplaza la ubicacion de un evento presencial",
			description = "Solo permitido mientras el evento esta en DRAFT y su modalidad es IN_PERSON.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Ubicacion reemplazada. Devuelve el evento completo.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en DRAFT.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Falta un campo obligatorio de la ubicacion, o el "
					+ "evento es ONLINE y no admite ubicacion.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}/venue")
	public EventResponse replaceVenue(@PathVariable String id, @Valid @RequestBody VenueRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		Venue venue = new Venue(request.placeName(), request.address(), request.latitude(), request.longitude(),
				request.placeId(), request.directions());
		ReplaceVenueCommand command = new ReplaceVenueCommand(principal.getUserId(), id, venue);
		return EventResponse.from(replaceVenueUseCase.replaceVenue(command));
	}

	@Operation(summary = "Reemplaza el acceso en linea de un evento online",
			description = "Solo permitido mientras el evento esta en DRAFT y su modalidad es ONLINE.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Acceso en linea reemplazado. Devuelve el evento "
					+ "completo, sin filtrar por LinkVisibility: es la vista del anfitrion.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en DRAFT.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Falta un campo obligatorio, la url no es http o "
					+ "https, hoursBefore no corresponde a linkVisibility, o el evento es IN_PERSON y no admite "
					+ "acceso en linea.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}/online-access")
	public EventResponse replaceOnlineAccess(@PathVariable String id,
			@Valid @RequestBody OnlineAccessRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		OnlineAccess onlineAccess = new OnlineAccess(request.platform(), request.url(), request.roomId(),
				request.password(), request.instructions(), request.linkVisibility(), request.hoursBefore());
		ReplaceOnlineAccessCommand command = new ReplaceOnlineAccessCommand(principal.getUserId(), id, onlineAccess);
		return EventResponse.from(replaceOnlineAccessUseCase.replaceOnlineAccess(command));
	}
}
