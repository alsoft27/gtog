package com.gtog.user.infrastructure.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
public class AuthController {

	private final RegisterUserUseCase registerUserUseCase;
	private final AuthenticationManager authenticationManager;

	public AuthController(RegisterUserUseCase registerUserUseCase, AuthenticationManager authenticationManager) {
		this.registerUserUseCase = registerUserUseCase;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
		var command = new RegisterUserCommand(request.name(), request.email(), request.rawPassword(), request.timeZone());
		var user = registerUserUseCase.register(command);
		return ResponseEntity.status(HttpStatus.CREATED).body(AuthUserResponse.from(user));
	}

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
