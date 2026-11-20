package bookstore.user.service;

import bookstore.user.ApiException;
import bookstore.user.data.SessionRepository;
import bookstore.user.data.UserRepository;
import bookstore.user.model.Role;
import bookstore.user.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;

/**
 * Account rules: sign-up, login, logout, token checks and profile changes.
 * Knows nothing about HTTP; handlers translate requests into these calls.
 */
public class AccountService {
    private static final int BCRYPT_COST = 10;

    private final UserRepository users;
    private final SessionRepository sessions;

    public AccountService(UserRepository users, SessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    /** Result of a successful login: the raw token (shown to the client once) and the user. */
    public record Login(String token, User user) {
    }

    /** Sign-up always makes a customer. Managers are created by {@link #ensureManager}. */
    public User register(String username, String password, String email, String fullName) throws SQLException {
        String u = Validation.username(username);
        String p = Validation.password(password);
        String e = Validation.email(email);
        String n = Validation.fullName(fullName);
        try {
            int id = users.insert(u, e, hash(p), n, Role.CUSTOMER);
            return users.findById(id).orElseThrow();
        } catch (UserRepository.DuplicateException ex) {
            throw ApiException.conflict("That username or email is already registered");
        }
    }

    /**
     * Checks the password and opens a session. When {@code requiredRole} is given, a user
     * with a different role is refused, so the manager app only accepts managers and the
     * customer app only accepts customers.
     */
    public Login login(String username, String password, Role requiredRole) throws SQLException {
        if (username == null || password == null) {
            throw ApiException.badRequest("Username and password are required");
        }
        User user = users.findByUsername(username.trim())
                .filter(found -> BCrypt.checkpw(password, found.passwordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Wrong username or password"));
        if (requiredRole != null && user.role() != requiredRole) {
            throw ApiException.forbidden(requiredRole == Role.MANAGER
                    ? "This account is not a manager account"
                    : "Managers must use the manager app");
        }
        String token = Tokens.newToken();
        sessions.insert(Tokens.hash(token), user.id());
        return new Login(token, user);
    }

    public void logout(String token) throws SQLException {
        sessions.revoke(Tokens.hash(token));
    }

    /** The user behind a token, or 401 if the token is unknown or logged out. */
    public User authenticate(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("Missing login token");
        }
        return sessions.findActiveUser(Tokens.hash(token))
                .orElseThrow(() -> ApiException.unauthorized("Session expired or logged out. Please log in again"));
    }

    public User updateProfile(User user, String email, String fullName) throws SQLException {
        String e = email == null ? user.email() : Validation.email(email);
        String n = fullName == null ? user.fullName() : Validation.fullName(fullName);
        try {
            users.updateProfile(user.id(), e, n);
        } catch (UserRepository.DuplicateException ex) {
            throw ApiException.conflict("That email is already registered");
        }
        return users.findById(user.id()).orElseThrow();
    }

    public void changePassword(User user, String currentPassword, String newPassword) throws SQLException {
        if (currentPassword == null || !BCrypt.checkpw(currentPassword, user.passwordHash())) {
            throw ApiException.forbidden("Current password is wrong");
        }
        users.updatePasswordHash(user.id(), hash(Validation.password(newPassword)));
    }

    /** Creates the manager account on first start, so a fresh install always has one. */
    public void ensureManager(String username, String password, String email) throws SQLException {
        if (users.findByUsername(username).isEmpty()) {
            users.insert(username, email, hash(password), "Store Manager", Role.MANAGER);
            System.out.println("Created manager account '" + username + "'");
        }
    }

    /** Creates a demo customer on first start, for testing. */
    public void ensureCustomer(String username, String password, String email, String fullName) throws SQLException {
        if (users.findByUsername(username).isEmpty() && users.findByEmail(email).isEmpty()) {
            users.insert(username, email, hash(password), fullName, Role.CUSTOMER);
            System.out.println("Created demo customer '" + username + "'");
        }
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
    }
}
