package dev.javaroot.antixray.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Db {

    private static final int SCHEMA_VER = 1;

    private final File dbFile;
    private final Logger log;
    private final ExecutorService exec;
    private Connection conn;

    public Db(File dbFile, Logger log) {
        this.dbFile = dbFile;
        this.log = log;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiXray-SQLite");
            t.setDaemon(true);
            return t;
        });
    }

    public void init() throws SQLException {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Failed to create database directory: " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver not found", ex);
        }

        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA busy_timeout=5000;");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid CHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        total_phantom_mined INTEGER NOT NULL DEFAULT 0,
                        total_punishments INTEGER NOT NULL DEFAULT 0,
                        last_detection_at INTEGER NOT NULL DEFAULT 0,
                        suspicion_score REAL NOT NULL DEFAULT 0,
                        watch_until INTEGER NOT NULL DEFAULT 0,
                        phantom_hit_count INTEGER NOT NULL DEFAULT 0,
                        last_phantom_hit_at INTEGER NOT NULL DEFAULT 0
                    );
                    """);

            ensureProfileCols(st);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS detections_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid CHAR(36) NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        ore_type VARCHAR(64) NOT NULL,
                        suspicion_score REAL NOT NULL,
                        session_count INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS punishments_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid CHAR(36) NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        phantom_count INTEGER NOT NULL,
                        executed_commands TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    );
                    """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_detections_uuid_time ON detections_log (uuid, created_at DESC);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid_time ON punishments_log (uuid, created_at DESC);");
            st.execute("INSERT OR IGNORE INTO schema_version (version) VALUES (" + SCHEMA_VER + ");");
        }
    }

    private static void ensureProfileCols(Statement st) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(player_profiles);")) {
            while (rs.next()) {
                existing.add(rs.getString("name"));
            }
        }
        String[][] migrations = {
                {"suspicion_score", "ALTER TABLE player_profiles ADD COLUMN suspicion_score REAL NOT NULL DEFAULT 0;"},
                {"watch_until", "ALTER TABLE player_profiles ADD COLUMN watch_until INTEGER NOT NULL DEFAULT 0;"},
                {"phantom_hit_count", "ALTER TABLE player_profiles ADD COLUMN phantom_hit_count INTEGER NOT NULL DEFAULT 0;"},
                {"last_phantom_hit_at", "ALTER TABLE player_profiles ADD COLUMN last_phantom_hit_at INTEGER NOT NULL DEFAULT 0;"}
        };
        for (String[] m : migrations) {
            if (!existing.contains(m[0])) {
                st.execute(m[1]);
            }
        }
    }

    public <T> CompletableFuture<T> submit(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run(conn);
            } catch (SQLException ex) {
                throw new IllegalStateException("SQLite query failed", ex);
            }
        }, exec);
    }

    public CompletableFuture<Void> execute(SqlVoidTask task) {
        return submit(c -> {
            task.run(c);
            return null;
        });
    }

    public CompletableFuture<Void> inTx(SqlVoidTask task) {
        return execute(c -> {
            c.setAutoCommit(false);
            try {
                task.run(c);
                c.commit();
            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (SQLException rbEx) {
                    ex.addSuppressed(rbEx);
                }
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        });
    }

    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            exec.shutdownNow();
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ex) {
                if (log != null) {
                    log.log(Level.WARNING, "Failed to close SQLite connection", ex);
                }
            }
        }
    }

    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlVoidTask {
        void run(Connection c) throws SQLException;
    }
}
