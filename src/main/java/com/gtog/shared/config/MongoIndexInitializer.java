package com.gtog.shared.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.gtog.event.infrastructure.out.persistence.EventDocument;

// Los indices se crean aqui en el arranque, no con @Indexed en los documentos: asi se sabe que existe
// en produccion mirando una sola clase, sin tener que rastrear anotaciones repartidas por el modelo.
@Component
public class MongoIndexInitializer {

	private final MongoTemplate mongoTemplate;

	public MongoIndexInitializer(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void createIndexes() {
		mongoTemplate.indexOps(EventDocument.class).createIndex(new Index().on("hostId", Sort.Direction.ASC));
	}
}
