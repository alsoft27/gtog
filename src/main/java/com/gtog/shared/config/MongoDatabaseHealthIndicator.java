package com.gtog.shared.config;

import org.bson.Document;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

// Sustituye al MongoHealthIndicator por defecto de Actuator (desactivado en
// application*.properties con management.health.mongodb.enabled=false). El de Spring Boot recorre
// TODAS las bases visibles vía listDatabaseNames() -- local, admin, config, la de la app... -- y
// ejecuta "hello" en cada una; en Atlas el usuario de la aplicacion no tiene permiso sobre "local",
// asi que ese chequeo cae con DOWN aunque la base real de la app (gtog_dev/gtog_test) responda bien.
// Este solo hace ping contra esa base, la que de verdad usa la aplicacion.
@Component("mongoHealthIndicator")
public class MongoDatabaseHealthIndicator extends AbstractHealthIndicator {

	private final MongoTemplate mongoTemplate;

	public MongoDatabaseHealthIndicator(MongoTemplate mongoTemplate) {
		super("MongoDB health check failed");
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		String databaseName = mongoTemplate.getDb().getName();
		Document result = mongoTemplate.executeCommand(new Document("ping", 1));
		builder.up().withDetail("database", databaseName).withDetail("ping", result.get("ok"));
	}
}
