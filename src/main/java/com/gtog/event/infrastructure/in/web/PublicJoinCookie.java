package com.gtog.event.infrastructure.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class PublicJoinCookie {
	private final boolean secure;
	private final String sameSite;
	private final Duration maxAge;
	public PublicJoinCookie(@Value("${app.public-base-url}") String publicBaseUrl,
			@Value("${app.join.cookie-secure:#{null}}") Boolean secure,
			@Value("${app.join.cookie-same-site:Lax}") String sameSite,
			@Value("${app.join.cookie-max-age:P30D}") Duration maxAge) {
		this.secure = secure == null ? publicBaseUrl.startsWith("https") : secure;
		if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite) || maxAge.isNegative() || maxAge.isZero()) {
			throw new IllegalArgumentException("Invalid public invitation cookie configuration");
		}
		this.sameSite = sameSite; this.maxAge = maxAge;
	}
	public String name(String joinToken) {
		try {
			return "gtog_join_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(joinToken.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
		} catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable"); }
	}
	public String read(HttpServletRequest request, String joinToken) {
		if (request.getCookies() == null) { return null; }
		String name = name(joinToken);
		for (var cookie : request.getCookies()) { if (cookie.getName().equals(name)) { return cookie.getValue(); } }
		return null;
	}
	public ResponseCookie create(String joinToken, String guestToken) {
		return ResponseCookie.from(name(joinToken), guestToken).httpOnly(true).secure(secure).sameSite(sameSite)
				.path("/api/invitations/join").maxAge(maxAge).build();
	}
}
