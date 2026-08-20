package com.gtog.event.infrastructure.in.web;

import java.net.URI;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.event.domain.model.Event;
import com.gtog.event.domain.model.ResponseOptionDraft;
import com.gtog.event.domain.model.ResponseOptionEdit;
import com.gtog.event.domain.port.in.CreateEventCommand;
import com.gtog.event.domain.port.in.CreateEventUseCase;
import com.gtog.event.domain.port.in.GetEventByIdUseCase;
import com.gtog.event.domain.port.in.ListEventsByHostUseCase;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsCommand;
import com.gtog.event.domain.port.in.ReplaceResponseOptionsUseCase;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Creacion y consulta de eventos por parte del anfitrion")
public class EventController {

	private final CreateEventUseCase createEventUseCase;
	private final GetEventByIdUseCase getEventByIdUseCase;
	private final ListEventsByHostUseCase listEventsByHostUseCase;
	private final ReplaceResponseOptionsUseCase replaceResponseOptionsUseCase;

	public EventController(CreateEventUseCase createEventUseCase, GetEventByIdUseCase getEventByIdUseCase,
			ListEventsByHostUseCase listEventsByHostUseCase,
			ReplaceResponseOptionsUseCase replaceResponseOptionsUseCase) {
		this.createEventUseCase = createEventUseCase;
		this.getEventByIdUseCase = getEventByIdUseCase;
		this.listEventsByHostUseCase = listEventsByHostUseCase;
		this.replaceResponseOptionsUseCase = replaceResponseOptionsUseCase;
	}

	@Operation(summary = "Crea un evento", description = "El anfitrion crea un evento nuevo. El evento se crea en "
			+ "estado DRAFT; las opciones de respuesta y los invitados se anaden en peticiones posteriores.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Evento creado. La cabecera Location apunta al recurso.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
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
	public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
		List<ResponseOptionDraft> responseOptionDrafts = request.responseOptions() == null ? null
				: request.responseOptions().stream()
						.map(option -> new ResponseOptionDraft(option.label(), option.countsAsAttendance()))
						.toList();
		CreateEventCommand command = new CreateEventCommand(
				request.hostId(),
				request.title(),
				request.description(),
				request.startsAt(),
				request.endsAt(),
				request.timeZone(),
				request.modality(),
				responseOptionDrafts,
				request.allowComment(),
				request.allowResponseChange(),
				request.responseDeadline());
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
	public EventResponse getEvent(@PathVariable String id) {
		return EventResponse.from(getEventByIdUseCase.getEventById(id));
	}

	@Operation(summary = "Lista los eventos de un anfitrion",
			description = "Devuelve una proyeccion ligera de cada evento del anfitrion indicado, sin paginacion. "
					+ "El parametro hostId es temporal: hoy es obligatorio porque no existe todavia el usuario "
					+ "autenticado, y desaparecera cuando el anfitrion se identifique por su sesion.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Listado de eventos del anfitrion, puede ser vacio.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							array = @ArraySchema(schema = @Schema(implementation = EventSummaryResponse.class)))),
			@ApiResponse(responseCode = "400", description = "Falta el parametro obligatorio hostId.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping
	public List<EventSummaryResponse> listEvents(
			@Parameter(description = "Identificador del anfitrion. Obligatorio y temporal: hoy no hay usuario "
					+ "autenticado del que derivarlo.", required = true)
			@RequestParam String hostId) {
		return listEventsByHostUseCase.listEventsByHost(hostId).stream().map(EventSummaryResponse::from).toList();
	}

	@Operation(summary = "Reemplaza las opciones de respuesta de un evento",
			description = "Sustituye la lista completa de opciones de respuesta, junto con allowComment, "
					+ "allowResponseChange y responseDeadline. Solo permitido mientras el evento esta en DRAFT. "
					+ "Para conservar el id de una opcion existente, envialo de vuelta; una opcion sin id se crea "
					+ "como nueva.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Opciones reemplazadas. Devuelve el evento completo.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = EventResponse.class))),
			@ApiResponse(responseCode = "404", description = "No existe ningun evento con ese id.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "El evento no esta en DRAFT y ya no admite cambios en "
					+ "sus opciones de respuesta.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "La peticion viola una regla de negocio: numero de "
					+ "opciones fuera de 2-5, ninguna opcion cuenta como asistencia, etiquetas vacias o duplicadas, "
					+ "un id de opcion que no pertenece al evento, o responseDeadline posterior a startsAt.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PutMapping("/{id}/response-options")
	public EventResponse replaceResponseOptions(@PathVariable String id,
			@Valid @RequestBody ReplaceResponseOptionsRequest request) {
		List<ResponseOptionEdit> edits = request.responseOptions().stream()
				.map(option -> new ResponseOptionEdit(option.id(), option.label(), option.countsAsAttendance()))
				.toList();
		ReplaceResponseOptionsCommand command = new ReplaceResponseOptionsCommand(id, edits, request.allowComment(),
				request.allowResponseChange(), request.responseDeadline());
		return EventResponse.from(replaceResponseOptionsUseCase.replaceResponseOptions(command));
	}
}
