package bookstore.client.ui;

import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How money, dates and ratings look on screen. */
public final class Format {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Format() {
    }

    public static String money(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    /** A server time shown in this computer's time zone. */
    public static String dateTime(OffsetDateTime t) {
        return t == null ? "" : t.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME);
    }

    public static String rating(Double avg, int count) {
        if (avg == null || count == 0) {
            return "No reviews";
        }
        return String.format("%.1f ★ (%d)", avg, count);
    }

    public static String stars(int rating) {
        return "★".repeat(rating) + "☆".repeat(5 - rating);
    }
}
