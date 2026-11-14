package com.gtog.user.infrastructure.in.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gtog.user.domain.port.in.RegisterUserCommand;
import com.gtog.user.domain.port.in.RegisterUserUseCase;
import com.gtog.user.infrastructure.out.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registro e inicio de sesion del anfitrion")
public class AuthController {

	private final RegisterUserUseCase registerUserUseCase;
	private final AuthenticationManager authenticationManager;

	public AuthController(RegisterUserUseCase registerUserUseCase, AuthenticationManager authenticationManager) {
		this.registerUserUseCase = registerUserUseCase;
		this.authenticationManager = authenticationManager;
	}

	@Operation(summary = "Registra un anfitrion",
			description = "Crea la cuenta con email unico y zona horaria IANA valida. No inicia sesion. "
					+ "Requiere el token CSRF en la cabecera X-XSRF-TOKEN.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Usuario registrado. Location identifica al usuario "
					+ "creado en /api/users/{id}; actualmente no existe un GET de usuario.",
					headers = @Header(name = "Location", description = "URI del usuario creado",
							schema = @Schema(type = "string", format = "uri-reference")),
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = AuthUserResponse.class))),
			@ApiResponse(responseCode = "400", description = "El cuerpo no tiene el formato esperado.",
					content = @Content),
			@ApiResponse(responseCode = "403", description = "Falta el token CSRF o no es valido.",
					content = @Content),
			@ApiResponse(responseCode = "409", description = "El email ya esta registrado.",
					content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "422", description = "Viola una regla de negocio: nombre, email, "
					+ "contrasena o zona horaria invalidos.",
					content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))) })
	@PostMapping("/register")
	public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
		var command = new RegisterUserCommand(request.name(), request.email(), request.rawPassword(), request.timeZone());
		var user = registerUserUseCase.register(command);
		return ResponseEntity.created(URI.create("/api/users/" + user.getId())).body(AuthUserResponse.from(user));
	}

	@Operation(summary = "Inicia sesion",
			description = "Autentica al anfitrion con email y contrasena y guarda la sesion en el servidor. "
					+ "Requiere el token CSRF en la cabecera X-XSRF-TOKEN para evitar login-CSRF.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Sesion autenticada.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = AuthUserResponse.class))),
			@ApiResponse(responseCode = "400", description = "Faltan credenciales o el cuerpo tiene un formato invalido.",
					content = @Content),
			@ApiResponse(responseCode = "401", description = "Credenciales incorrectas.",
					content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "Falta el token CSRF o no es valido.",
					content = @Content) })
	@PostMapping("/login")
	public ResponseEntity<AuthUserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		Authentication auth = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password()));
		SecurityContextHolder.getContext().setAuthentication(auth);
		httpRequest.getSession(true).setAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
				SecurityContextHolder.getContext());
		AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
		return ResponseEntity.ok(new AuthUserResponse(
				principal.getUserId(), principal.getName(), principal.getUsername(), principal.getTimeZone()));
	}
}
