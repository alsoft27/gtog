package com.gtog.user.infrastructure.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gtog.user.domain.port.in.ForgotPasswordUseCase;
import com.gtog.user.domain.port.in.ResetPasswordUseCase;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class PasswordRecoveryController {
	private final ForgotPasswordUseCase forgot;
	private final ResetPasswordUseCase reset;
	public PasswordRecoveryController(ForgotPasswordUseCase forgot, ResetPasswordUseCase reset) { this.forgot = forgot; this.reset = reset; }
	@Operation(summary = "Solicita restablecer la contraseña", description = "Público, con CSRF. 202 uniforme para cuentas existentes, inexistentes y limitadas. Flujo de correo asíncrono, sin reintentos.")
	@ApiResponses({ @ApiResponse(responseCode = "202", description = "Solicitud recibida; sin cuerpo y no-store exacto"),
			@ApiResponse(responseCode = "422", description = "Correo con formato inválido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/forgot-password") public ResponseEntity<Void> forgot(@RequestBody ForgotPasswordRequest request) {
		forgot.request(request.email()); return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
	}
	@Operation(summary = "Restablece la contraseña", description = "Público, con CSRF. Valida la contraseña antes de consumir el token. Marca emailVerified, borra los demás tokens e invalida sesiones, sin login automático. Un fallo tras consumir exige pedir otro token.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Contraseña restablecida; sin cuerpo y no-store exacto"),
			@ApiResponse(responseCode = "400", description = "Token inválido, caducado o usado; detalle genérico", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Contraseña demasiado corta; token sin consumir", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "500", description = "Fallo posterior al consumo; pedir otro token", content = @Content(schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/reset-password") public ResponseEntity<Void> reset(@RequestBody ResetPasswordRequest request) {
		reset.reset(request.token(), request.newPassword()); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
	}
}
