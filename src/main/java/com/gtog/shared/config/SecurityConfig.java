package com.gtog.shared.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import com.gtog.shared.security.ProblemDetailAccessDeniedHandler;
import com.gtog.shared.security.ProblemDetailAuthenticationEntryPoint;
import com.gtog.shared.security.ProblemDetailSessionExpiredStrategy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Value("${app.cors.allowed-origin:http://localhost:4200}")
	private String allowedOrigin;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAccessDeniedHandler accessDeniedHandler,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint, SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SessionRegistry sessionRegistry, ProblemDetailSessionExpiredStrategy expiredStrategy) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(csrf -> csrf
				// La credencial publica es el token de la ruta, no una cookie: no hay credencial ambiental aprovechable.
				.ignoringRequestMatchers("/api/invitations/**")
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				// Se conserva el valor crudo y la cookie existente: autenticar no ejecuta CsrfAuthenticationStrategy.
				.sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
			.sessionManagement(session -> session.sessionAuthenticationStrategy(sessionAuthenticationStrategy))
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			.addFilterAt(new ConcurrentSessionFilter(sessionRegistry, expiredStrategy), ConcurrentSessionFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/verify-email").permitAll()
				.requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/invitations/**").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(ex -> ex
				.accessDeniedHandler(accessDeniedHandler)
				.authenticationEntryPoint(authenticationEntryPoint))
			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value())));
		return http.build();
	}

	// Solo se registra en la cadena de seguridad, no como filtro adicional del contenedor.
	private static final class CsrfCookieFilter extends OncePerRequestFilter {
		private final RequestMatcher invitations = PathPatternRequestMatcher.pathPattern("/api/invitations/**");

		@Override
		protected boolean shouldNotFilter(HttpServletRequest request) {
			return invitations.matches(request);
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			// Security 7.1 publica el DeferredCsrfToken como atributo; consumirlo emite la cookie en el primer GET.
			if (request.getAttribute(DeferredCsrfToken.class.getName()) instanceof DeferredCsrfToken deferred) {
				deferred.get().getToken();
			}
			chain.doFilter(request, response);
		}
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(allowedOrigin));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
