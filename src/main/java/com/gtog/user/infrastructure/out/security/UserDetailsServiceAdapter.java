package com.gtog.user.infrastructure.out.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.gtog.user.domain.port.out.UserRepositoryPort;

@Service
public class UserDetailsServiceAdapter implements UserDetailsService {

	private final UserRepositoryPort userRepository;

	public UserDetailsServiceAdapter(UserRepositoryPort userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email)
				.map(user -> new AuthenticatedUser(
						user.getId(), user.getName(), user.getEmail(), user.getPassword().hash(), user.getTimeZone()))
				.orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
	}
}
