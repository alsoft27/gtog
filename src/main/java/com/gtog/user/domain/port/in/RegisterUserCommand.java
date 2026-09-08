package com.gtog.user.domain.port.in;

public record RegisterUserCommand(String name, String email, String rawPassword, String timeZone) {
}
