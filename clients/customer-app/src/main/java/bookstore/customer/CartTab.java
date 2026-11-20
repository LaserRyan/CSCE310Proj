package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.Order;
import bookstore.client.ui.Async;
import bookstore.client.ui.BillWindow;
import bookstore.client.ui.Dialogs;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/** Review the picked books, change quantities, and place the order (FR7), then show the bill (FR8). */
public class CartTab {
    private static final int MAX_QUANTITY = 20;

    private record Placed(Order order, String billHtml) {
    }

    private final BorderPane root = new BorderPane();

    public CartTab(ApiClient api, Cart cart, Consumer<Order> onOrdered) {
        TableView<Cart.Line> table = new TableView<>(cart.lines());
        TableColumn<Cart.Line, Integer> qty = new TableColumn<>("Qty");
        qty.setPrefWidth(130);
        qty.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        qty.setCellFactory(c -> new QuantityCell(cart));
        table.getColumns().addAll(java.util.List.of(
                Tables.column("Title", l -> l.book().title(), 280),
                Tables.column("Author", l -> l.book().author(), 170),
                Tables.column("Buy / Rent", l -> Cart.RENT.equals(l.type()) ? "Rent (30 days)" : "Buy", 110),
                qty,
                Tables.money("Price", Cart.Line::unitPrice, 90),
                Tables.money("Amount", Cart.Line::lineTotal, 100)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(table, "Your cart is empty. Add books from the Browse tab.");

        Label total = new Label();
        total.getStyleClass().add("total");
        total.textProperty().bind(Bindings.createStringBinding(
                () -> "Total: " + Format.money(cart.total().get()), cart.total()));

        Button clear = new Button("Empty cart");
        clear.setOnAction(e -> cart.lines().clear());
        Button place = new Button("Place order");
        place.getStyleClass().add("primary");
        place.disableProperty().bind(Bindings.isEmpty(cart.lines()));
        place.setOnAction(e -> {
            String message = cart.itemCount() + " item(s), total " + Format.money(cart.total().get())
                    + ".\nPayment is collected later; the order starts as Pending.";
            if (!Dialogs.confirm("Place this order?", message)) {
                return;
            }
            Async.run(api.placeOrder(cart.toOrderLines()).thenCompose(order ->
                            api.bill(order.id()).thenApply(html -> new Placed(order, html))),
                    root,
                    placed -> {
                        cart.lines().clear();
                        BillWindow.show(root.getScene().getWindow(), placed.order().id(), placed.billHtml());
                        onOrdered.accept(placed.order());
                    });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, clear, spacer, total, place);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10));

        root.setCenter(table);
        root.setBottom(footer);
        BorderPane.setMargin(table, new Insets(10, 10, 0, 10));
    }

    public Parent view() {
        return root;
    }

    /** Shows the quantity with − / + buttons and a remove button. */
    private static class QuantityCell extends TableCell<Cart.Line, Integer> {
        private final Cart cart;

        QuantityCell(Cart cart) {
            this.cart = cart;
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            Cart.Line line = empty ? null : getTableRow().getItem();
            if (line == null) {
                setGraphic(null);
                return;
            }
            Button minus = new Button("−");
            Button plus = new Button("+");
            Button remove = new Button("✕");
            Label count = new Label(String.valueOf(line.quantity()));
            count.setMinWidth(22);
            count.setAlignment(Pos.CENTER);
            minus.setDisable(line.quantity() <= 1);
            plus.setDisable(line.quantity() >= MAX_QUANTITY);
            minus.setOnAction(e -> line.quantityProperty().set(line.quantity() - 1));
            plus.setOnAction(e -> line.quantityProperty().set(line.quantity() + 1));
            remove.setOnAction(e -> cart.lines().remove(line));
            HBox box = new HBox(4, minus, count, plus, remove);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
        }
    }
}
