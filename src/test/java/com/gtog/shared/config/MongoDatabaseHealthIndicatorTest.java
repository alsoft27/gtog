package com.gtog.shared.config;

import org.bson.Document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoDatabaseHealthIndicatorTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private MongoDatabase mongoDatabase;

	@Test
	void reportsUpAndTheApplicationDatabaseNameWhenPingSucceeds() {
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(mongoDatabase.getName()).thenReturn("gtog_dev");
		when(mongoTemplate.executeCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

		Health health = new MongoDatabaseHealthIndicator(mongoTemplate).health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("database", "gtog_dev");
	}

	@Test
	void reportsDownWhenThePingFails() {
		when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
		when(mongoDatabase.getName()).thenReturn("gtog_dev");
		when(mongoTemplate.executeCommand(any(Document.class))).thenThrow(new RuntimeException("not authorized"));

		Health health = new MongoDatabaseHealthIndicator(mongoTemplate).health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
	}
}
