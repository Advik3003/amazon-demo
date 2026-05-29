package com.amazondemo.product.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * MongoDB Configuration
 * ======================
 * Ensures required indexes exist on startup.
 * The @TextIndexed annotations in ProductReadModel require a MongoDB text index
 * which must be explicitly created if Spring auto-index-creation doesn't pick it up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoConfig implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureTextIndex();
    }

    private void ensureTextIndex() {
        try {
            MongoCollection<Document> collection = mongoTemplate.getCollection("products");
            // Create text index for product search
            Document indexKeys = new Document("name", "text")
                    .append("description", "text")
                    .append("brand", "text");
            IndexOptions options = new IndexOptions()
                    .name("product_text_search")
                    .weights(new Document("name", 2).append("description", 1).append("brand", 1));
            collection.createIndex(indexKeys, options);
            log.info("MongoDB text index ensured on 'products' collection");
        } catch (Exception e) {
            log.warn("Could not create MongoDB text index (may already exist): {}", e.getMessage());
        }
    }
}
