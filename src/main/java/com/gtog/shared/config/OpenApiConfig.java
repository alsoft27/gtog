package com.gtog.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI gtogOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("gtog API")
						.version("v1")
						.description("API del backend de gtog, la aplicacion de organizacion de eventos. "
								+ "El anfitrion crea el evento y gestiona invitados; cada invitado responde "
								+ "desde su enlace unico sin necesidad de cuenta."));
	}

}
