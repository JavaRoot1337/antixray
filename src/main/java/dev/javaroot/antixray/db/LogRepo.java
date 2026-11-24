package dev.javaroot.antixray.db;

import dev.javaroot.antixray.mdl.DetLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogRepo {

    public record HistoryPage(List<DetLog> entries, int page, int maxPage, int totalCount) {
    }

    public record PlayerProfile(
            int totalPunishments,
            double suspicionScore,
            long watchUntilMs,
            int phantomHitCount,
            long lastPhantomHitAtMs
    ) {
        public static final PlayerProfile EMPTY = new PlayerProfile(0, 0.0, 0L, 0, 0L);
    }

    private final Db db;
    private final Logger logger;

    public LogRepo(Db db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    private <T> T logAndFallback(String action, Throwable ex, T fallback) {
        if (logger != null) {
            logger.log(Level.WARNING, "Database error during " + action, ex);
        }
        return fallback;
    }

    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid) {
        return db.submit(conn -> {
            String sql = """
                    SELECT total_punishments, suspicion_score, watch_until, phantom_hit_count, last_phantom_hit_at
                    FROM player_profiles
                    WHERE uuid = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return PlayerProfile.EMPTY;
                    }
                    return new PlayerProfile(
                            rs.getInt(1),
                            rs.getDouble(2),
                            rs.getLong(3),
                            rs.getInt(4),
                            rs.getLong(5)
                    );
                }
            }
        }).exceptionally(ex -> logAndFallback("loadProfile(" + uuid + ")", ex, PlayerProfile.EMPTY));
    }

    public CompletableFuture<Void> saveSessionState(
            UUID uuid,
            String username,
            double suspicionScore,
            long watchUntilMs,
            int phantomHitCount,
            long lastPhantomHitAtMs
    ) {
        return db.execute(conn -> {
            String upsert = """
                    INSERT INTO player_profiles (
                        uuid, username, total_phantom_mined, total_punishments, last_detection_at,
                        suspicion_score, watch_until, phantom_hit_count, last_phantom_hit_at
                    )
                    VALUES (?, ?, 0, 0, 0, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username = excluded.username,
                        suspicion_score = excluded.suspicion_score,
                        watch_until = excluded.watch_until,
                        phantom_hit_count = excluded.phantom_hit_count,
                        last_phantom_hit_at = excluded.last_phantom_hit_at
                    """;
            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setDouble(3, suspicionScore);
                ps.setLong(4, watchUntilMs);
                ps.setInt(5, phantomHitCount);
                ps.setLong(6, lastPhantomHitAtMs);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logAndFallback("saveSessionState(" + uuid + ")", ex, null));
    }

    public CompletableFuture<Integer> loadTotalPunishments(UUID uuid) {
        return db.submit(conn -> {
            String sql = "SELECT total_punishments FROM player_profiles WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }).exceptionally(ex -> logAndFallback("loadTotalPunishments(" + uuid + ")", ex, 0));
    }

    public CompletableFuture<Void> recordDetection(
            UUID uuid,
            String username,
            String world,
            int x,
            int y,
            int z,
            String oreType,
            double suspicionScore,
            int sessionCount,
            long createdAtMs
    ) {
        return db.execute(conn -> {
            conn.setAutoCommit(false);
            try {
                String upsert = """
                        INSERT INTO player_profiles (uuid, username, total_phantom_mined, total_punishments, last_detection_at)
                        VALUES (?, ?, 1, 0, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            username = excluded.username,
                            total_phantom_mined = player_profiles.total_phantom_mined + 1,
                            last_detection_at = excluded.last_detection_at
                        """;
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setLong(3, createdAtMs);
                    ps.executeUpdate();
                }

                String insertLog = """
                        INSERT INTO detections_log (uuid, username, world, x, y, z, ore_type, suspicion_score, session_count, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertLog)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setString(3, world);
                    ps.setInt(4, x);
                    ps.setInt(5, y);
                    ps.setInt(6, z);
                    ps.setString(7, oreType);
                    ps.setDouble(8, suspicionScore);
                    ps.setInt(9, sessionCount);
                    ps.setLong(10, createdAtMs);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }).exceptionally(ex -> logAndFallback("recordDetection(" + uuid + ")", ex, null));
    }

    public CompletableFuture<Void> recordPunishment(
            UUID uuid,
            String username,
            String world,
            int x,
            int y,
            int z,
            int phantomCount,
            String executedCommands,
            long createdAtMs
    ) {
        return db.execute(conn -> {
            conn.setAutoCommit(false);
            try {
                String upsert = """
                        INSERT INTO player_profiles (uuid, username, total_phantom_mined, total_punishments, last_detection_at)
                        VALUES (?, ?, 0, 1, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            username = excluded.username,
                            total_punishments = player_profiles.total_punishments + 1
                        """;
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setLong(3, createdAtMs);
                    ps.executeUpdate();
                }

                String insertPun = """
                        INSERT INTO punishments_log (uuid, username, world, x, y, z, phantom_count, executed_commands, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertPun)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setString(3, world);
                    ps.setInt(4, x);
                    ps.setInt(5, y);
                    ps.setInt(6, z);
                    ps.setInt(7, phantomCount);
                    ps.setString(8, executedCommands);
                    ps.setLong(9, createdAtMs);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }).exceptionally(ex -> logAndFallback("recordPunishment(" + uuid + ")", ex, null));
    }

    public CompletableFuture<HistoryPage> fetchHistory(UUID uuid, int requestedPage, int pageSize) {
        return db.submit(conn -> {
            int total = 0;
            String countSql = "SELECT COUNT(*) FROM detections_log WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }
            if (total == 0) {
                return new HistoryPage(List.of(), 1, 1, 0);
            }

            int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
            int page = Math.max(1, Math.min(requestedPage, maxPage));
            int offset = (page - 1) * pageSize;

            List<DetLog> list = new ArrayList<>(pageSize);
            String querySql = """
                    SELECT id, username, world, x, y, z, ore_type, suspicion_score, session_count, created_at
                    FROM detections_log
                    WHERE uuid = ?
                    ORDER BY created_at DESC
                    LIMIT ? OFFSET ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(querySql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, pageSize);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new DetLog(
                                rs.getLong("id"),
                                uuid,
                                rs.getString("username"),
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("ore_type"),
                                rs.getDouble("suspicion_score"),
                                rs.getInt("session_count"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            }
            return new HistoryPage(list, page, maxPage, total);
        }).exceptionally(ex -> logAndFallback("fetchHistory(" + uuid + ")", ex, new HistoryPage(List.of(), 1, 1, 0)));
    }

    public CompletableFuture<UUID> findUuidByUsername(String username) {
        return db.submit(conn -> {
            String sql = "SELECT uuid FROM player_profiles WHERE LOWER(username) = LOWER(?) LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString(1));
                    }
                    return null;
                }
            }
        }).exceptionally(ex -> logAndFallback("findUuidByUsername(" + username + ")", ex, null));
    }

    public CompletableFuture<Void> pruneOldDetections(int retentionDays) {
        if (retentionDays <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        long cutoffMs = System.currentTimeMillis() - retentionDays * 86_400_000L;
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM detections_log WHERE created_at < ?")) {
                ps.setLong(1, cutoffMs);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logAndFallback("pruneOldDetections", ex, null));
    }
}
