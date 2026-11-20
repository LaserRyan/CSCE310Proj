package bookstore.user;

/** Settings read from environment variables, with defaults that match docker-compose.yml. */
public record Config(
        int port,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String managerUsername,
        String managerPassword,
        String managerEmail) {

    public static Config fromEnv() {
        return new Config(
                Integer.parseInt(env("USER_SERVICE_PORT", "8081")),
                env("USER_DB_URL", "jdbc:mysql://localhost:3307/bookstore_users?connectionTimeZone=UTC"),
                env("MYSQL_USER", "bookstore"),
                env("MYSQL_PASSWORD", "bookstore"),
                env("MANAGER_USERNAME", "manager"),
                env("MANAGER_PASSWORD", "manager123"),
                env("MANAGER_EMAIL", "manager@bookstore.test"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
