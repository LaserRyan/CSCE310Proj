package bookstore.user.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A row of the users table. The password hash never leaves this service. */
public record User(
        int id,
        String username,
        String email,
        String passwordHash,
        String fullName,
        Role role,
        Instant createdAt) {

    /** The public view of a user, as sent in JSON responses. */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("username", username);
        json.put("email", email);
        json.put("full_name", fullName);
        json.put("role", role.name());
        json.put("created_at", createdAt == null ? null : createdAt.toString());
        return json;
    }
}
