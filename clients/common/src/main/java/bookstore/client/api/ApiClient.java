package bookstore.client.api;

import bookstore.client.model.Models.BestSeller;
import bookstore.client.model.Models.Book;
import bookstore.client.model.Models.Order;
import bookstore.client.model.Models.Review;
import bookstore.client.model.Models.SalesReport;
import bookstore.client.model.Models.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Every call the desktop apps make to the three services. All calls are
 * asynchronous (they return a CompletableFuture), so the window never freezes
 * while waiting on the network (N2). After login the token is kept here and
 * sent as "Authorization: Bearer ..." on every call.
 */
public class ApiClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String usersUrl;
    private final String booksUrl;
    private final String ordersUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private volatile String token;
    private volatile User currentUser;

    public ApiClient(String usersUrl, String booksUrl, String ordersUrl) {
        this.usersUrl = usersUrl;
        this.booksUrl = booksUrl;
        this.ordersUrl = ordersUrl;
    }

    /** Service addresses from environment variables, defaulting to this machine. */
    public static ApiClient fromEnvironment() {
        return new ApiClient(
                setting("USER_SERVICE_URL", "http://127.0.0.1:8081"),
                setting("BOOK_SERVICE_URL", "http://127.0.0.1:5001"),
                setting("ORDER_SERVICE_URL", "http://127.0.0.1:5002"));
    }

    public User currentUser() {
        return currentUser;
    }

    // ================================================================ accounts

    /** Logs in and keeps the token. {@code role} is "CUSTOMER" or "MANAGER"; others are refused. */
    public CompletableFuture<User> login(String username, String password, String role) {
        Map<String, Object> body = Map.of("username", username, "password", password, "role", role);
        return send("POST", usersUrl + "/api/auth/login", body).thenApply(node -> {
            token = node.get("token").asText();
            currentUser = convert(node.get("user"), User.class);
            return currentUser;
        });
    }

    public CompletableFuture<User> register(String username, String password, String email, String fullName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("email", email);
        body.put("full_name", fullName);
        return send("POST", usersUrl + "/api/auth/register", body).thenApply(n -> convert(n.get("user"), User.class));
    }

    /** Ends the session on the server, then forgets the token whatever the reply. */
    public CompletableFuture<Void> logout() {
        return send("POST", usersUrl + "/api/auth/logout", null)
                .handle((ok, err) -> {
                    token = null;
                    currentUser = null;
                    return null;
                });
    }

    public CompletableFuture<User> updateProfile(String email, String fullName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("full_name", fullName);
        return send("PUT", usersUrl + "/api/users/me", body).thenApply(n -> {
            currentUser = convert(n.get("user"), User.class);
            return currentUser;
        });
    }

    public CompletableFuture<Void> changePassword(String current, String next) {
        return send("PUT", usersUrl + "/api/users/me/password",
                Map.of("current_password", current, "new_password", next)).thenApply(n -> null);
    }

    // =================================================================== books

    public CompletableFuture<List<Book>> searchBooks(String keyword) {
        return send("GET", booksUrl + "/api/books?q=" + encode(keyword), null)
                .thenApply(n -> convert(n.get("books"), new TypeReference<List<Book>>() { }));
    }

    public CompletableFuture<Book> addBook(Map<String, Object> fields) {
        return send("POST", booksUrl + "/api/books", fields).thenApply(n -> convert(n.get("book"), Book.class));
    }

    public CompletableFuture<Book> updateBook(String bookId, Map<String, Object> fields) {
        return send("PUT", booksUrl + "/api/books/" + bookId, fields).thenApply(n -> convert(n.get("book"), Book.class));
    }

    public CompletableFuture<List<Review>> reviews(String bookId) {
        return send("GET", booksUrl + "/api/books/" + bookId + "/reviews", null)
                .thenApply(n -> convert(n.get("reviews"), new TypeReference<List<Review>>() { }));
    }

    /** Saves the caller's review and returns the book with its new average rating. */
    public CompletableFuture<Book> writeReview(String bookId, int rating, String comment) {
        return send("POST", booksUrl + "/api/books/" + bookId + "/reviews", Map.of("rating", rating, "comment", comment))
                .thenApply(n -> convert(n.get("book"), Book.class));
    }

    // ================================================================== orders

    /** One line of a new order. {@code type} is "BUY" or "RENT". */
    public record OrderLine(String bookId, String type, int quantity) {
    }

    public CompletableFuture<Order> placeOrder(List<OrderLine> lines) {
        List<Map<String, Object>> items = lines.stream()
                .map(l -> Map.<String, Object>of("book_id", l.bookId(), "type", l.type(), "quantity", l.quantity()))
                .toList();
        return send("POST", ordersUrl + "/api/orders", Map.of("items", items)).thenApply(n -> convert(n.get("order"), Order.class));
    }

    public CompletableFuture<List<Order>> myOrders() {
        return send("GET", ordersUrl + "/api/orders/mine", null).thenApply(this::orders);
    }

    /** The bill as an HTML page. */
    public CompletableFuture<String> bill(int orderId) {
        return sendRaw("GET", ordersUrl + "/api/orders/" + orderId + "/bill", null);
    }

    /** Manager: every order, optionally filtered by status and time range (end is exclusive). */
    public CompletableFuture<List<Order>> allOrders(String status, Instant from, Instant to) {
        String query = "?status=" + encode(status) + range(from, to);
        return send("GET", ordersUrl + "/api/orders" + query, null).thenApply(this::orders);
    }

    public CompletableFuture<Order> setPaymentStatus(int orderId, String status) {
        return send("PATCH", ordersUrl + "/api/orders/" + orderId + "/payment", Map.of("payment_status", status))
                .thenApply(n -> convert(n.get("order"), Order.class));
    }

    public CompletableFuture<Order> returnRental(int orderId, int itemId) {
        return send("POST", ordersUrl + "/api/orders/" + orderId + "/items/" + itemId + "/return", null)
                .thenApply(n -> convert(n.get("order"), Order.class));
    }

    public CompletableFuture<SalesReport> salesReport(Instant from, Instant to) {
        return send("GET", ordersUrl + "/api/reports/sales?" + range(from, to), null)
                .thenApply(n -> convert(n, SalesReport.class));
    }

    public CompletableFuture<List<BestSeller>> bestsellers(Instant from, Instant to, int limit) {
        return send("GET", ordersUrl + "/api/reports/bestsellers?limit=" + limit + range(from, to), null)
                .thenApply(n -> convert(n.get("books"), new TypeReference<List<BestSeller>>() { }));
    }

    // ================================================================ plumbing

    private List<Order> orders(JsonNode n) {
        return convert(n.get("orders"), new TypeReference<List<Order>>() { });
    }

    private CompletableFuture<JsonNode> send(String method, String url, Object body) {
        return sendRaw(method, url, body).thenApply(text -> {
            try {
                return json.readTree(text.isEmpty() ? "{}" : text);
            } catch (Exception e) {
                throw new ApiException(0, "Server sent a reply the app could not read");
            }
        });
    }

    private CompletableFuture<String> sendRaw(String method, String url, Object body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT);
        String t = token;
        if (t != null) {
            req.header("Authorization", "Bearer " + t);
        }
        if (body == null) {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            try {
                req.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        return http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, err) -> {
                    if (err != null) {
                        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                        String server = URI.create(url).getAuthority();
                        throw new ApiException(0, cause instanceof ConnectException
                                ? "Cannot reach the server at " + server + ". Is it running?"
                                : "Network error talking to " + server + ": " + cause.getMessage());
                    }
                    if (resp.statusCode() >= 400) {
                        throw new ApiException(resp.statusCode(), errorMessage(resp), t != null);
                    }
                    return resp.body();
                });
    }

    private String errorMessage(HttpResponse<String> resp) {
        try {
            JsonNode node = json.readTree(resp.body());
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Request failed (HTTP " + resp.statusCode() + ")";
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        return json.convertValue(node, type);
    }

    private <T> T convert(JsonNode node, TypeReference<T> type) {
        return json.convertValue(node, type);
    }

    private static String range(Instant from, Instant to) {
        return (from == null ? "" : "&from=" + encode(from.toString()))
                + (to == null ? "" : "&to=" + encode(to.toString()));
    }

    private static String encode(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String setting(String name, String fallback) {
        String v = System.getProperty(name, System.getenv(name));
        return v == null || v.isBlank() ? fallback : v;
    }
}
