package bookstore.user.data;

import bookstore.user.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** SQL for the sessions table. Works on token hashes, never raw tokens. */
public class SessionRepository {
    private final Database db;

    public SessionRepository(Database db) {
        this.db = db;
    }

    public void insert(String tokenHash, int userId) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO sessions (token_hash, user_id) VALUES (?, ?)")) {
            ps.setString(1, tokenHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** The user who owns an active (not logged-out) session with this token hash. */
    public Optional<User> findActiveUser(String tokenHash) throws SQLException {
        String sql = "SELECT u.id, u.username, u.email, u.password_hash, u.full_name, u.role, u.created_at "
                + "FROM sessions s JOIN users u ON u.id = s.user_id "
                + "WHERE s.token_hash = ? AND s.revoked_at IS NULL";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UserRepository.map(rs)) : Optional.empty();
            }
        }
    }

    public void revoke(String tokenHash) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL")) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }
}
