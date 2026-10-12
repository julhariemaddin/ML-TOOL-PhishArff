package com.julharie.phisharff.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage in ~/.phisharff/phisharff.db. It keeps:
 *   - history  : every export (file name, checksums, row counts), so the same file is recognised next time
 *   - settings : small things like the last folder you used
 * If SQLite cannot start, the app still works, it just forgets history (available() returns false).
 */
public final class Database {

    public record HistoryItem(String createdAt, String kind, String sourceName, String sourceSha256,
                              int rowsIn, int rowsOut, String mode, String outputPath, String outputSha256) {}

    private final String url;
    private boolean available;

    public Database(Path file) {
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL,"
                        + " kind TEXT NOT NULL, source_name TEXT, source_sha256 TEXT, rows_in INTEGER, rows_out INTEGER,"
                        + " mode TEXT, output_path TEXT, output_sha256 TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");
            }
            available = true;
        } catch (Exception e) {
            System.err.println("SQLite not available, history disabled: " + e);
        }
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".phisharff", "phisharff.db");
    }

    public boolean available() { return available; }

    private Connection connect() throws SQLException { return DriverManager.getConnection(url); }

    public void addHistory(HistoryItem h) {
        if (!available) return;
        String sql = "INSERT INTO history (created_at, kind, source_name, source_sha256, rows_in, rows_out, mode, output_path, output_sha256)"
                + " VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            ps.setString(2, h.kind());
            ps.setString(3, h.sourceName());
            ps.setString(4, h.sourceSha256());
            ps.setInt(5, h.rowsIn());
            ps.setInt(6, h.rowsOut());
            ps.setString(7, h.mode());
            ps.setString(8, h.outputPath());
            ps.setString(9, h.outputSha256());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save history: " + e);
        }
    }

    public List<HistoryItem> recent(String kind, int limit) {
        List<HistoryItem> out = new ArrayList<>();
        if (!available) return out;
        String sql = "SELECT created_at, kind, source_name, source_sha256, rows_in, rows_out, mode, output_path, output_sha256"
                + " FROM history WHERE kind = ? ORDER BY id DESC LIMIT ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        } catch (SQLException e) {
            System.err.println("Could not read history: " + e);
        }
        return out;
    }

    /** Was a file with this checksum exported before? Returns the latest export or null. */
    public HistoryItem findBySha(String kind, String sha256) {
        if (!available) return null;
        String sql = "SELECT created_at, kind, source_name, source_sha256, rows_in, rows_out, mode, output_path, output_sha256"
                + " FROM history WHERE kind = ? AND source_sha256 = ? ORDER BY id DESC LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setString(2, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public String getSetting(String key, String fallback) {
        if (!available) return fallback;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        } catch (SQLException e) {
            return fallback;
        }
    }

    public void setSetting(String key, String value) {
        if (!available) return;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save setting: " + e);
        }
    }

    private static HistoryItem read(ResultSet rs) throws SQLException {
        return new HistoryItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getString(9));
    }
}
