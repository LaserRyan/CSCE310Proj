package bookstore.user.http;

import bookstore.user.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps "METHOD /exact/path" to a route, and turns thrown exceptions into JSON errors.
 * HttpServer only matches path prefixes, so this does the exact matching.
 */
public class Router implements HttpHandler {

    @FunctionalInterface
    public interface Route {
        void handle(HttpExchange ex) throws Exception;
    }

    private record Entry(String method, String path, Route route) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public Router add(String method, String path, Route route) {
        entries.add(new Entry(method, path, route));
        return this;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        int status = 200;
        try {
            boolean pathKnown = false;
            for (Entry e : entries) {
                if (e.path.equals(path)) {
                    pathKnown = true;
                    if (e.method.equals(method)) {
                        e.route.handle(ex);
                        status = ex.getResponseCode();
                        return;
                    }
                }
            }
            status = pathKnown ? 405 : 404;
            JsonExchange.sendError(ex, status, pathKnown ? "Method not allowed" : "Not found");
        } catch (ApiException e) {
            status = e.status();
            JsonExchange.sendError(ex, status, e.getMessage());
        } catch (Exception e) {
            status = 500;
            e.printStackTrace();
            JsonExchange.sendError(ex, 500, "Internal server error");
        } finally {
            ex.close();
            System.out.printf("%s %s -> %d (%d ms)%n", method, path, status, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
