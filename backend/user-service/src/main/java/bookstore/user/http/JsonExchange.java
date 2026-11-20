package bookstore.user.http;

import bookstore.user.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Small helpers for reading JSON requests and writing JSON responses. */
final class JsonExchange {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonExchange() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return Map.of();
            }
            Object parsed = MAPPER.readValue(bytes, Object.class);
            if (!(parsed instanceof Map)) {
                throw ApiException.badRequest("Request body must be a JSON object");
            }
            return (Map<String, Object>) parsed;
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Request body is not valid JSON");
        }
    }

    static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    /** The token from an "Authorization: Bearer <token>" header, or null. */
    static String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, Map.of("error", message));
    }
}
