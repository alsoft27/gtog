package com.gtog.shared.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {
	private final SecurityProblemWriter writer;

	public ProblemDetailAccessDeniedHandler(SecurityProblemWriter writer) {
		this.writer = writer;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
		writer.write(response, HttpStatus.FORBIDDEN, "Access denied");
	}
}
