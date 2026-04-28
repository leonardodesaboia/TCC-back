package com.allset.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt-secret=test-secret-test-secret-test-secret-1234",
        "cpf-encryption-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "database-url=jdbc:postgresql://placeholder/test",
        "db-user=test",
        "db-pass=test",
        "redis-host=localhost",
        "redis-port=6379",
        "port=8080",
        "user-purge-cron=0 0 2 * * *",
        "minio.endpoint=http://test:9000",
        "minio.public-endpoint=http://test:9000",
        "minio.access-key=test",
        "minio.secret-key=testsecret",
        "minio.auto-create-buckets=false"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FlywaySchemaSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldExposeTablesAndEnumTypesFromCurrentMigrations() {
        assertThat(tableExists("subscription_plans")).isTrue();
        assertThat(tableExists("service_areas")).isTrue();
        assertThat(tableExists("service_categories")).isTrue();
        assertThat(tableExists("professionals")).isTrue();
        assertThat(tableExists("professional_specialties")).isTrue();
        assertThat(tableExists("professional_documents")).isTrue();
        assertThat(tableExists("professional_services")).isTrue();
        assertThat(tableExists("blocked_periods")).isTrue();
        assertThat(tableExists("conversations")).isTrue();
        assertThat(tableExists("messages")).isTrue();
        assertThat(tableExists("push_tokens")).isTrue();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(tableExists("reviews")).isTrue();

        assertThat(enumExists("verification_status")).isTrue();
        assertThat(enumExists("doc_type")).isTrue();
        assertThat(enumExists("pricing_type")).isTrue();
        assertThat(enumExists("block_type")).isTrue();
        assertThat(enumExists("msg_type")).isTrue();
        assertThat(enumExists("notification_type")).isTrue();
        assertThat(enumExists("platform")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean enumExists(String typeName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from pg_type where typname = ?",
                Integer.class,
                typeName
        );
        return count != null && count > 0;
    }
}
