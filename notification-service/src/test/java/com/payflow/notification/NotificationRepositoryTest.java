package com.payflow.notification;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureEmbeddedDatabase(type = DatabaseType.POSTGRES, provider = DatabaseProvider.ZONKY)
class NotificationRepositoryTest {

    @Autowired NotificationRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void latestFiftyAreNewestFirstAndOnlyForThatUser() {
        jdbc.execute("TRUNCATE notifications RESTART IDENTITY");
        jdbc.update("""
                INSERT INTO notifications (user_id, message, created_at)
                SELECT CASE WHEN g % 2 = 0 THEN 100 ELSE 200 END, 'n' || g, now() - (g || ' minutes')::interval
                FROM generate_series(1, 200) AS g
                """);

        var latest = repository.findTop50ByUserIdOrderByCreatedAtDesc(100L);

        assertThat(latest).hasSize(50).allSatisfy(n -> assertThat(n.getUserId()).isEqualTo(100L));
        assertThat(latest.get(0).getMessage()).isEqualTo("n2");   // created 2 minutes ago = newest for user 100
        assertThat(latest).extracting(Notification::getCreatedAt).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }
}
