package vn.ltm.chat.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Optional JDBC persistence. Configure LTM_CHAT_DB_URL to enable MySQL storage. */
final class ChatRepository {
    private final String url;
    private final String username;
    private final String password;
    private volatile boolean persistenceEnabled;

    ChatRepository(String url, String username, String password) {
        this.url = url == null ? "" : url.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.persistenceEnabled = !this.url.isBlank();
    }

    boolean enabled() { return persistenceEnabled; }

    void disablePersistence() { persistenceEnabled = false; }

    void initialize() throws SQLException {
        if (!enabled()) return;
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chat_users (" +
                    "username VARCHAR(64) PRIMARY KEY, first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chat_messages (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, sender VARCHAR(64) NOT NULL, recipient VARCHAR(64) NULL, " +
                    "chat_type VARCHAR(20) NOT NULL, content TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chat_attachments (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, sender VARCHAR(64) NOT NULL, recipient VARCHAR(64) NULL, " +
                    "chat_scope VARCHAR(20) NOT NULL, file_name VARCHAR(255) NOT NULL, content_type VARCHAR(128) NOT NULL, " +
                    "storage_path VARCHAR(1024) NOT NULL, size_bytes BIGINT NOT NULL, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    void userSeen(String name) {
        if (!enabled()) return;
        String sql = "INSERT INTO chat_users(username) VALUES (?) ON DUPLICATE KEY UPDATE last_seen=CURRENT_TIMESTAMP";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name); ps.executeUpdate();
        } catch (SQLException e) { System.err.println("[DB] Cannot save user activity: " + e.getMessage()); }
    }

    void saveMessage(String sender, String recipient, String type, String body, Instant time) {
        if (!enabled()) return;
        String sql = "INSERT INTO chat_messages(sender,recipient,chat_type,content,created_at) VALUES (?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender); ps.setString(2, recipient); ps.setString(3, type);
            ps.setString(4, body); ps.setTimestamp(5, Timestamp.from(time)); ps.executeUpdate();
        } catch (SQLException e) { System.err.println("[DB] Cannot save message: " + e.getMessage()); }
    }


    void saveAttachment(String sender, String recipient, String scope, String fileName,
                        String contentType, String storagePath, long sizeBytes) {
        if (!enabled()) return;
        String sql = "INSERT INTO chat_attachments(sender,recipient,chat_scope,file_name,content_type,storage_path,size_bytes) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender); ps.setString(2, recipient); ps.setString(3, scope);
            ps.setString(4, fileName); ps.setString(5, contentType); ps.setString(6, storagePath);
            ps.setLong(7, sizeBytes); ps.executeUpdate();
        } catch (SQLException e) { System.err.println("[DB] Cannot save attachment metadata: " + e.getMessage()); }
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(url, username, password); }
}
