package bookstore.user.service;

import bookstore.user.ApiException;

import java.util.regex.Pattern;

/** Input rules for account fields. */
public final class Validation {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    // Practical email check: something@something.tld, no spaces.
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    public static final int MIN_PASSWORD = 6;

    private Validation() {
    }

    public static String username(String value) {
        if (value == null || !USERNAME.matcher(value.trim()).matches()) {
            throw ApiException.badRequest("Username must be 3-30 letters, digits or underscores");
        }
        return value.trim();
    }

    public static String email(String value) {
        if (value == null || value.length() > 255 || !EMAIL.matcher(value.trim()).matches()) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        return value.trim().toLowerCase();
    }

    public static String password(String value) {
        if (value == null || value.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (value.length() > 72) { // bcrypt only uses the first 72 bytes
            throw ApiException.badRequest("Password must be at most 72 characters");
        }
        return value;
    }

    public static String fullName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 100) {
            throw ApiException.badRequest("Full name must be at most 100 characters");
        }
        return value.trim();
    }
}
