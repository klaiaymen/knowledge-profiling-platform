package dev.knowledgeprofiling.app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.DriverManager;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "KPF_MIGRATION_TEST_URL", matches = ".+")
class FlywayMigrationTest {
    @Test void migratesAnEmptyPgvectorDatabase() throws Exception {
        String url = System.getenv("KPF_MIGRATION_TEST_URL");
        String user = System.getenv("KPF_MIGRATION_TEST_USERNAME");
        String password = System.getenv("KPF_MIGRATION_TEST_PASSWORD");
        var encoder = new BCryptPasswordEncoder();
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholders(Map.of("demoUserHash", encoder.encode(java.util.UUID.randomUUID().toString()),
                        "demoAdminHash", encoder.encode(java.util.UUID.randomUUID().toString())))
                .load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name = 'source_document'")) {
            result.next();
            assertEquals(1, result.getInt(1));
        }
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM app_user WHERE role IN ('USER', 'ADMIN')")) {
            result.next();
            assertEquals(2, result.getInt(1));
        }
    }
}
