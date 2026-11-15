package com.example.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;



public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeMigration(String scriptPath) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            creerTableVersions(conn);

            if (dejaAppliquee(conn, scriptPath)) {
                LOGGER.info("Migration déjà appliquée : " + scriptPath);
                return;
            }

            List<String> instructions = lireScript(scriptPath);
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String sql : instructions) {
                    LOGGER.info("Exécution : " + sql);
                    stmt.execute(sql);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO schema_version(script, applied_at) VALUES (?, ?)")) {
                    ps.setString(1, scriptPath);
                    ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    ps.executeUpdate();
                }
                conn.commit();
                LOGGER.info("Migration appliquée avec succès : " + scriptPath);
            } catch (SQLException e) {
                // Sous MySQL, ALTER TABLE valide implicitement (pas de rollback possible)
                conn.rollback();
                LOGGER.severe("Échec de la migration " + scriptPath + " : " + e.getMessage());
                throw e;
            }
        }
    }

    private void creerTableVersions(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "script VARCHAR(255) PRIMARY KEY, "
                    + "applied_at TIMESTAMP NOT NULL)");
        }
    }

    private boolean dejaAppliquee(Connection conn, String scriptPath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM schema_version WHERE script = ?")) {
            ps.setString(1, scriptPath);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private List<String> lireScript(String scriptPath) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(scriptPath);
        if (is == null) {
            throw new IllegalArgumentException("Script introuvable dans le classpath : " + scriptPath);
        }
        List<String> instructions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder courant = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("--")) continue;
                courant.append(l).append(' ');
                if (l.endsWith(";")) {
                    String sql = courant.toString().trim();
                    instructions.add(sql.substring(0, sql.length() - 1));
                    courant.setLength(0);
                }
            }
            if (!courant.toString().isBlank()) instructions.add(courant.toString().trim());
        }
        return instructions;
    }
}