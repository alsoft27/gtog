package com.gtog.user.infrastructure.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.user.domain.port.in.*;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class EmailVerificationController {
	private final VerifyEmailUseCase verification;
	private final ResendVerificationUseCase resend;
	private final GetCurrentUserUseCase current;
	public EmailVerificationController(VerifyEmailUseCase verification, ResendVerificationUseCase resend, GetCurrentUserUseCase current) {
		this.verification = verification; this.resend = resend; this.current = current;
	}
	@Operation(summary = "Verifica el correo", description = "Público, con CSRF. Token de un solo uso, leído del fragmento del enlace. No inicia sesión.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Correo verificado; sin cuerpo, no-store exacto"),
			@ApiResponse(responseCode = "400", description = "Token inválido, caducado o usado; detalle genérico", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/verify-email") public ResponseEntity<Void> verify(@RequestBody VerifyEmailRequest request) {
		verification.verify(request.token()); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
	}
	@Operation(summary = "Reenvía la verificación", description = "Requiere sesión y CSRF; solo al correo propio. Respuesta uniforme aunque esté verificado, limitado o falle el envío. Sin reintentos.")
	@ApiResponses({ @ApiResponse(responseCode = "204", description = "Solicitud recibida; sin cuerpo, no-store exacto"),
			@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/resend-verification") public ResponseEntity<Void> resend(@AuthenticationPrincipal AuthenticatedUser principal) {
		resend.resend(principal.getUserId()); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}
	@Operation(summary = "Consulta el usuario actual", description = "Requiere sesión. Lee emailVerified de la base de datos, no de la sesión. No-store exacto.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Perfil y verificación vigentes"),
			@ApiResponse(responseCode = "401", description = "Sin sesión", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@GetMapping("/me") public ResponseEntity<AuthUserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AuthUserResponse.from(current.get(principal.getUserId())));
	}
}
