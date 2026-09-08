package com.gtog.user.infrastructure.out.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails {

	private final String userId;
	private final String name;
	private final String email;
	private final String passwordHash;
	private final String timeZone;

	public AuthenticatedUser(String userId, String name, String email, String passwordHash, String timeZone) {
		this.userId = userId;
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.timeZone = timeZone;
	}

	public String getUserId() { return userId; }
	public String getName() { return name; }
	public String getTimeZone() { return timeZone; }

	@Override public String getUsername() { return email; }
	@Override public String getPassword() { return passwordHash; }
	@Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
	@Override public boolean isAccountNonExpired() { return true; }
	@Override public boolean isAccountNonLocked() { return true; }
	@Override public boolean isCredentialsNonExpired() { return true; }
	@Override public boolean isEnabled() { return true; }
}
