package bookstore.user.data;

import bookstore.user.model.Role;
import bookstore.user.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Optional;

/** SQL for the users table. */
public class UserRepository {
    private static final String COLUMNS = "id, username, email, password_hash, full_name, role, created_at";

    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    public Optional<User> findById(int id) throws SQLException {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE id = ?", id);
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE username = ?", username);
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE email = ?", email);
    }

    /** Inserts a user and returns its new id. Throws {@link DuplicateException} if the username or email is taken. */
    public int insert(String username, String email, String passwordHash, String fullName, Role role) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, full_name, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, fullName);
            ps.setString(5, role.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateException();
        }
    }

    public void updateProfile(int id, String email, String fullName) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET email = ?, full_name = ? WHERE id = ?")) {
            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateException();
        }
    }

    public void updatePasswordHash(int id, String passwordHash) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Optional<User> findOne(String sql, Object param) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toInstant());
    }

    /** The username or email is already used by another account. */
    public static class DuplicateException extends RuntimeException {
    }
}
