package bookstore.customer;

import bookstore.client.api.ApiClient.OrderLine;
import bookstore.client.model.Models.Book;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/** The books a customer has picked but not ordered yet (FR6). Lives only in the app. */
public class Cart {

    public static final String BUY = "BUY";
    public static final String RENT = "RENT";

    /** One book, bought or rented, with a quantity the customer can change. */
    public static class Line {
        private final Book book;
        private final String type;
        private final IntegerProperty quantity = new SimpleIntegerProperty(1);

        Line(Book book, String type) {
            this.book = book;
            this.type = type;
        }

        public Book book() { return book; }
        public String type() { return type; }
        public IntegerProperty quantityProperty() { return quantity; }
        public int quantity() { return quantity.get(); }

        public double unitPrice() {
            return RENT.equals(type) ? book.rentPrice() : book.price();
        }

        public double lineTotal() {
            return unitPrice() * quantity();
        }
    }

    // The extractor makes the list report a change when a quantity changes, so totals update.
    private final ObservableList<Line> lines =
            FXCollections.observableArrayList(line -> new ObservableValue[]{line.quantityProperty()});
    private final DoubleBinding total = Bindings.createDoubleBinding(
            () -> lines.stream().mapToDouble(Line::lineTotal).sum(), lines);

    public ObservableList<Line> lines() {
        return lines;
    }

    public DoubleBinding total() {
        return total;
    }

    /** Adds one copy, or one more if the same book is already in the cart the same way. */
    public void add(Book book, String type) {
        for (Line line : lines) {
            if (line.book().id().equals(book.id()) && line.type().equals(type)) {
                line.quantityProperty().set(line.quantity() + 1);
                return;
            }
        }
        lines.add(new Line(book, type));
    }

    public int itemCount() {
        return lines.stream().mapToInt(Line::quantity).sum();
    }

    public List<OrderLine> toOrderLines() {
        return lines.stream().map(l -> new OrderLine(l.book().id(), l.type(), l.quantity())).toList();
    }
}
