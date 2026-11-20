package bookstore.user;

import bookstore.user.data.Database;
import bookstore.user.data.SessionRepository;
import bookstore.user.data.UserRepository;
import bookstore.user.http.AccountHandlers;
import bookstore.user.service.AccountService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Starts the user service: wires the layers together and opens the HTTP port. */
public class UserServiceApp {

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnv();

        Database db = new Database(config.dbUrl(), config.dbUser(), config.dbPassword());
        AccountService accounts = new AccountService(new UserRepository(db), new SessionRepository(db));
        accounts.ensureManager(config.managerUsername(), config.managerPassword(), config.managerEmail());
        accounts.ensureCustomer("alice", "alice123", "alice@bookstore.test", "Alice Customer");

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new AccountHandlers(accounts).router());
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("User service listening on http://localhost:" + config.port());
    }
}
