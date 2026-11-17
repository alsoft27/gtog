package com.gtog.shared.security;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityProblemWriter {
	private final JsonMapper mapper;

	public SecurityProblemWriter(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		if (response.isCommitted()) { return; }
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setInstance(URI.create("/api/errors"));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		// Escribir directamente evita el despacho a /error y no incorpora datos de la peticion.
		mapper.writeValue(response.getOutputStream(), problem);
	}
}
