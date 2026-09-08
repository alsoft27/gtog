package com.gtog.user.infrastructure.in.web;

// Todos los campos son reglas de negocio validadas en el dominio: BlankUserNameException, InvalidEmailException,
// PasswordTooShortException y BlankTimeZoneException → 422. No usar @NotBlank aqui.
public record RegisterUserRequest(String name, String email, String rawPassword, String timeZone) {
}
