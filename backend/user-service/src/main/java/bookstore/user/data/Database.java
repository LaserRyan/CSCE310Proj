package bookstore.user.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out JDBC connections from a pool. Callers close them with try-with-resources,
 * which returns them to the pool. Opening a fresh MySQL connection per request cost
 * ~50-80 ms; a pooled one is near instant.
 */
public class Database {
    private final HikariDataSource pool;

    public Database(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(16); // one per HTTP worker thread
        config.setPoolName("users-db");
        pool = new HikariDataSource(config);
    }

    public Connection connect() throws SQLException {
        return pool.getConnection();
    }
}
