package com.gtog.shared.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final SecurityProblemWriter writer;

	public ProblemDetailAuthenticationEntryPoint(SecurityProblemWriter writer) {
		this.writer = writer;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
		writer.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
	}
}
