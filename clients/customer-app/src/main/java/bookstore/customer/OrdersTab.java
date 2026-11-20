package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.Order;
import bookstore.client.model.Models.OrderItem;
import bookstore.client.ui.Async;
import bookstore.client.ui.BillWindow;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** The customer's order history (extra credit), with each order's items and bill. */
public class OrdersTab {
    private final ApiClient api;
    private final BorderPane root = new BorderPane();
    private final TableView<Order> orders = new TableView<>();
    private final TableView<OrderItem> items = new TableView<>();
    private final Label summary = new Label();

    public OrdersTab(ApiClient api) {
        this.api = api;

        orders.getColumns().addAll(List.of(
                Tables.column("Order #", Order::id, 80),
                Tables.column("Placed", o -> Format.dateTime(o.createdAt()), 150),
                Tables.column("Items", Order::itemCount, 70),
                Tables.money("Total", Order::total, 100),
                Tables.status("Payment", Order::paymentStatus, 100),
                Tables.column("Paid on", o -> Format.dateTime(o.paidAt()), 150)));
        orders.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(orders, "You haven't placed any orders yet.");

        items.getColumns().addAll(List.of(
                Tables.column("Title", OrderItem::title, 260),
                Tables.column("Author", OrderItem::author, 160),
                Tables.column("Buy / Rent", i -> i.isRental() ? "Rent" : "Buy", 90),
                Tables.column("Qty", OrderItem::quantity, 50),
                Tables.money("Price", OrderItem::unitPrice, 85),
                Tables.money("Amount", OrderItem::lineTotal, 90),
                Tables.column("Rental", OrdersTab::rentalStatus, 170)));
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(items, "Select an order to see its books.");
        orders.getSelectionModel().selectedItemProperty().addListener((obs, old, o) ->
                items.getItems().setAll(o == null ? List.of() : o.items()));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        Button bill = new Button("View bill");
        bill.getStyleClass().add("primary");
        bill.disableProperty().bind(orders.getSelectionModel().selectedItemProperty().isNull());
        bill.setOnAction(e -> {
            Order o = orders.getSelectionModel().getSelectedItem();
            Async.run(api.bill(o.id()), bill, html -> BillWindow.show(root.getScene().getWindow(), o.id(), html));
        });
        summary.getStyleClass().add("muted");
        HBox bar = new HBox(8, refresh, bill, summary);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        Label itemsTitle = new Label("Books in this order");
        itemsTitle.getStyleClass().add("section-title");
        VBox bottom = new VBox(6, itemsTitle, items);
        bottom.setPadding(new Insets(10));
        VBox.setVgrow(items, javafx.scene.layout.Priority.ALWAYS);
        VBox top = new VBox(orders);
        top.setPadding(new Insets(10, 10, 0, 10));
        VBox.setVgrow(orders, javafx.scene.layout.Priority.ALWAYS);

        SplitPane split = new SplitPane(top, bottom);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        root.setTop(bar);
        root.setCenter(split);
    }

    public Parent view() {
        return root;
    }

    public void refresh() {
        Integer selected = orders.getSelectionModel().getSelectedItem() == null
                ? null : orders.getSelectionModel().getSelectedItem().id();
        Async.run(api.myOrders(), root, list -> {
            orders.getItems().setAll(list);
            double spent = list.stream().mapToDouble(Order::total).sum();
            summary.setText(list.size() + " order(s), " + Format.money(spent) + " in total");
            list.stream().filter(o -> selected != null && o.id() == selected).findFirst()
                    .or(() -> list.stream().findFirst())
                    .ifPresent(o -> orders.getSelectionModel().select(o));
        });
    }

    static String rentalStatus(OrderItem i) {
        if (!i.isRental()) {
            return "";
        }
        if (i.returnedAt() != null) {
            return "Returned " + Format.dateTime(i.returnedAt());
        }
        return "Due back " + i.dueDate();
    }
}
