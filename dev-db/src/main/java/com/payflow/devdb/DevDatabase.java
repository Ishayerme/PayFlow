package com.payflow.devdb;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Starts a real PostgreSQL on localhost:5432 for local development, with the
 * three databases the services expect. Data is kept in ./.local-db between runs.
 *
 * Run:  mvnw -pl dev-db exec:java      (stop with Ctrl+C)
 *
 * Only a convenience for machines without PostgreSQL or Docker; docker-compose.yml
 * is the normal way to run the whole stack.
 */
public final class DevDatabase {

    private static final String[] DATABASES = {"payflow_accounts", "payflow_transactions", "payflow_notifications"};

    private DevDatabase() {
    }

    public static void main(String[] args) throws Exception {
        File dataDir = new File(".local-db").getAbsoluteFile();
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setPort(5432)
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .start();

        try (Connection c = postgres.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = 'payflow'")) {
                if (!rs.next()) {
                    st.execute("CREATE ROLE payflow LOGIN PASSWORD 'payflow'");
                }
            }
            for (String db : DATABASES) {
                try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + db + "'")) {
                    if (!rs.next()) {
                        st.execute("CREATE DATABASE " + db + " OWNER payflow");
                    }
                }
            }
        }

        System.out.println("PostgreSQL running on localhost:5432 (user payflow / payflow), data in " + dataDir);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));
        Thread.currentThread().join();
    }
}
