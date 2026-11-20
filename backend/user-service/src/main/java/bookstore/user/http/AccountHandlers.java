package bookstore.user.http;

import bookstore.user.ApiException;
import bookstore.user.model.Role;
import bookstore.user.model.User;
import bookstore.user.service.AccountService;
import com.sun.net.httpserver.HttpExchange;

import java.util.LinkedHashMap;
import java.util.Map;

/** The HTTP endpoints. Each one reads JSON, calls {@link AccountService}, and writes JSON. */
public class AccountHandlers {
    private final AccountService accounts;

    public AccountHandlers(AccountService accounts) {
        this.accounts = accounts;
    }

    public Router router() {
        return new Router()
                .add("POST", "/api/auth/register", this::register)
                .add("POST", "/api/auth/login", this::login)
                .add("POST", "/api/auth/logout", this::logout)
                .add("GET", "/api/users/me", this::me)
                .add("PUT", "/api/users/me", this::updateMe)
                .add("PUT", "/api/users/me/password", this::changePassword)
                .add("GET", "/health", ex -> JsonExchange.send(ex, 200, Map.of("status", "ok")));
    }

    /** POST /api/auth/register  {username, password, email, full_name?} -> 201 {user} */
    private void register(HttpExchange ex) throws Exception {
        Map<String, Object> body = JsonExchange.readBody(ex);
        User user = accounts.register(
                JsonExchange.string(body, "username"),
                JsonExchange.string(body, "password"),
                JsonExchange.string(body, "email"),
                JsonExchange.string(body, "full_name"));
        JsonExchange.send(ex, 201, Map.of("user", user.toJson()));
    }

    /** POST /api/auth/login  {username, password, role?} -> 200 {token, user} */
    private void login(HttpExchange ex) throws Exception {
        Map<String, Object> body = JsonExchange.readBody(ex);
        Role role = parseRole(JsonExchange.string(body, "role"));
        AccountService.Login login = accounts.login(
                JsonExchange.string(body, "username"),
                JsonExchange.string(body, "password"),
                role);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", login.token());
        out.put("user", login.user().toJson());
        JsonExchange.send(ex, 200, out);
    }

    /** POST /api/auth/logout  (Bearer token) -> 200 */
    private void logout(HttpExchange ex) throws Exception {
        String token = JsonExchange.bearerToken(ex);
        accounts.authenticate(token);
        accounts.logout(token);
        JsonExchange.send(ex, 200, Map.of("message", "Logged out"));
    }

    /** GET /api/users/me  (Bearer token) -> 200 {user}. Other services call this to check a token. */
    private void me(HttpExchange ex) throws Exception {
        User user = accounts.authenticate(JsonExchange.bearerToken(ex));
        JsonExchange.send(ex, 200, Map.of("user", user.toJson()));
    }

    /** PUT /api/users/me  {email?, full_name?} -> 200 {user} */
    private void updateMe(HttpExchange ex) throws Exception {
        User user = accounts.authenticate(JsonExchange.bearerToken(ex));
        Map<String, Object> body = JsonExchange.readBody(ex);
        User updated = accounts.updateProfile(user,
                JsonExchange.string(body, "email"),
                JsonExchange.string(body, "full_name"));
        JsonExchange.send(ex, 200, Map.of("user", updated.toJson()));
    }

    /** PUT /api/users/me/password  {current_password, new_password} -> 200 */
    private void changePassword(HttpExchange ex) throws Exception {
        User user = accounts.authenticate(JsonExchange.bearerToken(ex));
        Map<String, Object> body = JsonExchange.readBody(ex);
        accounts.changePassword(user,
                JsonExchange.string(body, "current_password"),
                JsonExchange.string(body, "new_password"));
        JsonExchange.send(ex, 200, Map.of("message", "Password changed"));
    }

    private static Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("role must be CUSTOMER or MANAGER");
        }
    }
}
