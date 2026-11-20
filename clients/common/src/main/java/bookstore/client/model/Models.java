package bookstore.client.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data the server sends back, one record per JSON shape. Field names are the
 * camelCase form of the snake_case JSON keys (Jackson maps between them).
 */
public final class Models {
    private Models() {
    }

    public record User(int id, String username, String email, String fullName, String role,
                       OffsetDateTime createdAt) {
    }

    public record Book(String id, String title, String author, String isbn, String genre,
                       String description, Integer publishedYear, double price, double rentPrice,
                       int copies, Double avgRating, int reviewCount) {
    }

    public record Review(String id, String bookId, int userId, String username, int rating,
                         String comment, OffsetDateTime createdAt) {
    }

    public record OrderItem(int id, String bookId, String title, String author, String type,
                            int quantity, double unitPrice, double lineTotal, LocalDate dueDate,
                            OffsetDateTime returnedAt) {
        public boolean isRental() {
            return "RENT".equals(type);
        }
    }

    public record Order(int id, int userId, String customerUsername, String customerEmail,
                        String paymentStatus, double total, OffsetDateTime createdAt,
                        OffsetDateTime paidAt, List<OrderItem> items) {
        public int itemCount() {
            return items.stream().mapToInt(OrderItem::quantity).sum();
        }
    }

    public record SalesReport(int orderCount, double revenue, double paidRevenue, double pendingRevenue,
                              int unitsBought, int unitsRented, List<Order> orders) {
    }

    public record BestSeller(String bookId, String title, String author, int unitsBought,
                             int unitsRented, double revenue) {
        public int units() {
            return unitsBought + unitsRented;
        }
    }
}
