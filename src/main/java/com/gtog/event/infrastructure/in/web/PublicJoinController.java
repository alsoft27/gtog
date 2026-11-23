package com.gtog.event.infrastructure.in.web;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.event.domain.port.in.*;

@RestController
@RequestMapping("/api/invitations/join/{joinToken}")
@Tag(name = "Invitations")
public class PublicJoinController {
	private final PreviewPublicInvitationUseCase preview;
	private final JoinPublicInvitationUseCase join;
	private final PublicJoinCookie cookie;
	public PublicJoinController(PreviewPublicInvitationUseCase preview, JoinPublicInvitationUseCase join, PublicJoinCookie cookie) {
		this.preview = preview; this.join = join; this.cookie = cookie;
	}
	public record Request(String name, String email, String phone) {
		@Override public String toString() { return "PublicJoinRequest[redacted]"; }
	}
	public record Created(@Schema(description = "Credencial individual nueva, nunca la de otro invitado") String invitationPath) {
		@Override public String toString() { return "PublicJoinCreated[redacted]"; }
	}
	@GetMapping
	@Operation(summary = "Vista previa para darse de alta por enlace", description = "Sin sesión ni CSRF. No escribe. "
			+ "Un evento cancelado o finalizado conserva la vista, con canJoin=false. La cookie es fricción, no una barrera.")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "Vista pública sin datos personales"),
			@ApiResponse(responseCode = "404", description = "Enlace desconocido, desactivado, revocado o borrador")})
	public ResponseEntity<PublicJoinPreviewResponse> get(@PathVariable String joinToken, HttpServletRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
				PublicJoinPreviewResponse.from(preview.preview(joinToken, cookie.read(request, joinToken))));
	}
	@PostMapping
	@Operation(summary = "Alta inmediata de un invitado por enlace", description = "Sin sesión ni CSRF. Contacto opcional; "
			+ "los contactos ya presentes se descartan sin revelar a sus titulares. Solo emite cookie en el 201.")
	@ApiResponses({@ApiResponse(responseCode = "201", description = "Credencial nueva y cookie HttpOnly"),
			@ApiResponse(responseCode = "400", description = "Cuerpo ausente o ilegible"),
			@ApiResponse(responseCode = "404", description = "Enlace no disponible"),
			@ApiResponse(responseCode = "409", description = "ALREADY_JOINED, JOIN_CLOSED, PUBLIC_LINK_FULL o CAPACITY_EXCEEDED"),
			@ApiResponse(responseCode = "422", description = "errors con field y code, sin datos aportados")})
	public ResponseEntity<Created> post(@PathVariable String joinToken, @RequestBody Request body, HttpServletRequest request) {
		var guest = join.join(new JoinPublicInvitationCommand(joinToken, cookie.read(request, joinToken), body.name(), body.email(), body.phone()));
		return ResponseEntity.created(URI.create("/api/invitations/join/" + joinToken)).cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, cookie.create(joinToken, guest.getToken()).toString())
				.body(new Created("/r/" + guest.getToken()));
	}
}
