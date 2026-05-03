package com.stellarideas.grooves.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.stellarideas.grooves.util.YearParser;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One-shot migration: converts {@code music_files.year} from {@code String} to
 * {@code Integer}. Pre-migration, the scanner stored whatever JAudioTagger
 * returned (e.g. "1987", "1987-05-12", ""), which made smart-playlist range
 * queries lexical and fragile.
 *
 * <p>Idempotent — the {@code $type: "string"} filter only matches unmigrated
 * documents. Invalid values are unset rather than coerced to a wrong year.
 */
@Component
public class MongoYearMigration {

    private static final Logger log = LoggerFactory.getLogger(MongoYearMigration.class);
    private static final int BATCH_SIZE = 500;

    private final MongoTemplate mongoTemplate;

    public MongoYearMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateStringYears() {
        MongoCollection<Document> collection = mongoTemplate.getCollection("music_files");
        Bson filter = Filters.type("year", "string");
        long total = collection.countDocuments(filter);
        if (total == 0) return;

        log.info("Migrating {} music_files documents with string year fields to Integer", total);

        List<WriteModel<Document>> ops = new ArrayList<>(BATCH_SIZE);
        long converted = 0;
        long unset = 0;

        try (var cursor = collection.find(filter).projection(new Document("_id", 1).append("year", 1)).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Object id = doc.get("_id");
                String raw = doc.getString("year");
                Integer parsed = YearParser.parse(raw);
                Bson update = parsed != null
                        ? Updates.set("year", parsed)
                        : Updates.unset("year");
                ops.add(new UpdateOneModel<>(Filters.eq("_id", id), update));
                if (parsed != null) converted++; else unset++;

                if (ops.size() >= BATCH_SIZE) {
                    collection.bulkWrite(ops);
                    ops.clear();
                }
            }
        }
        if (!ops.isEmpty()) {
            collection.bulkWrite(ops);
        }
        log.info("Year migration complete: {} converted, {} unset (unparseable)", converted, unset);
    }
}
