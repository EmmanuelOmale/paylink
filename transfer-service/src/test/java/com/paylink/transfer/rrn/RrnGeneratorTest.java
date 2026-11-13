package com.paylink.transfer.rrn;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class RrnGeneratorTest {

    private final RrnGenerator generator = new RrnGenerator(JdbcClient.create(databaseWithSchema()));

    @Test
    void sampleInstant() {
        String rrn = generator.next(Instant.parse("2026-10-14T14:05:12Z"));

        assertThat(rrn).hasSize(12).containsOnlyDigits().startsWith("628714");
    }

    @Test
    void startOfYear() {
        String rrn = generator.next(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(rrn).hasSize(12).startsWith("600100");
    }

    @Test
    void uniqueAcrossThreads() throws Exception {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        Instant at = Instant.parse("2026-10-14T14:05:12Z");
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                workers.add(pool.submit(() -> {
                    for (int call = 0; call < 1000; call++) {
                        seen.add(generator.next(at));
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        assertThat(seen).hasSize(8000);
    }

    private static DataSource databaseWithSchema() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        return dataSource;
    }
}
