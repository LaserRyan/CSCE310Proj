package bookstore.manager;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.Order;
import bookstore.client.model.Models.OrderItem;
import bookstore.client.ui.Async;
import bookstore.client.ui.BillWindow;
import bookstore.client.ui.Dialogs;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * All orders (FR12), payment status changes (FR13), bills to print (FR9),
 * and marking rented books returned.
 */
public class OrdersTab {
    private static final String ALL = "All orders";

    private final ApiClient api;
    private final BorderPane root = new BorderPane();
    private final TableView<Order> orders = new TableView<>();
    private final TableView<OrderItem> items = new TableView<>();
    private final ChoiceBox<String> statusFilter = new ChoiceBox<>();
    private final TextField customerFilter = new TextField();
    private final Label summary = new Label();
    private List<Order> loaded = List.of();

    public OrdersTab(ApiClient api) {
        this.api = api;

        statusFilter.getItems().addAll(ALL, "PENDING", "PAID");
        statusFilter.setValue(ALL);
        statusFilter.setOnAction(e -> refresh());
        customerFilter.setPromptText("Filter by customer");
        customerFilter.textProperty().addListener((obs, old, text) -> applyCustomerFilter());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        summary.getStyleClass().add("muted");
        HBox bar = new HBox(8, new Label("Show"), statusFilter, customerFilter, refresh, summary);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        orders.getColumns().addAll(List.of(
                Tables.column("Order #", Order::id, 75),
                Tables.column("Placed", o -> Format.dateTime(o.createdAt()), 140),
                Tables.column("Customer", Order::customerUsername, 120),
                Tables.column("Email", Order::customerEmail, 200),
                Tables.column("Items", Order::itemCount, 60),
                Tables.money("Total", Order::total, 95),
                Tables.status("Payment", Order::paymentStatus, 95),
                Tables.column("Paid on", o -> Format.dateTime(o.paidAt()), 140)));
        orders.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(orders, "No orders match.");
        orders.getSelectionModel().selectedItemProperty().addListener((obs, old, o) ->
                items.getItems().setAll(o == null ? List.of() : o.items()));

        Button markPaid = new Button("Mark as Paid");
        markPaid.getStyleClass().add("primary");
        markPaid.setOnAction(e -> setStatus("PAID", markPaid));
        Button markPending = new Button("Mark as Pending");
        markPending.setOnAction(e -> setStatus("PENDING", markPending));
        Button bill = new Button("View / print bill");
        bill.setOnAction(e -> {
            Order o = orders.getSelectionModel().getSelectedItem();
            Async.run(api.bill(o.id()), bill, html -> BillWindow.show(root.getScene().getWindow(), o.id(), html));
        });
        for (Button b : List.of(markPaid, markPending, bill)) {
            b.disableProperty().bind(orders.getSelectionModel().selectedItemProperty().isNull());
        }
        HBox orderActions = new HBox(8, markPaid, markPending, bill);
        orderActions.setPadding(new Insets(8, 0, 0, 0));

        items.getColumns().addAll(List.of(
                Tables.column("Title", OrderItem::title, 250),
                Tables.column("Author", OrderItem::author, 150),
                Tables.column("Buy / Rent", i -> i.isRental() ? "Rent" : "Buy", 85),
                Tables.column("Qty", OrderItem::quantity, 50),
                Tables.money("Price", OrderItem::unitPrice, 80),
                Tables.money("Amount", OrderItem::lineTotal, 85),
                Tables.column("Rental", OrdersTab::rentalStatus, 190)));
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(items, "Select an order to see its books.");

        Button returned = new Button("Mark rental returned");
        returned.disableProperty().bind(items.getSelectionModel().selectedItemProperty().isNull());
        returned.setOnAction(e -> markReturned(returned));
        Label itemsTitle = new Label("Books in this order");
        itemsTitle.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox itemsHeader = new HBox(8, itemsTitle, spacer, returned);
        itemsHeader.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(orders, orderActions);
        top.setPadding(new Insets(10, 10, 6, 10));
        VBox.setVgrow(orders, Priority.ALWAYS);
        VBox bottom = new VBox(6, itemsHeader, items);
        bottom.setPadding(new Insets(10));
        VBox.setVgrow(items, Priority.ALWAYS);
        SplitPane split = new SplitPane(top, bottom);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);

        root.setTop(bar);
        root.setCenter(split);
        refresh();
    }

    public Parent view() {
        return root;
    }

    public void refresh() {
        String status = ALL.equals(statusFilter.getValue()) ? null : statusFilter.getValue();
        Async.run(api.allOrders(status, null, null), root, list -> {
            loaded = list;
            applyCustomerFilter();
        });
    }

    private void applyCustomerFilter() {
        Integer selected = selectedId();
        String needle = customerFilter.getText().trim().toLowerCase();
        List<Order> shown = loaded.stream()
                .filter(o -> needle.isEmpty()
                        || o.customerUsername().toLowerCase().contains(needle)
                        || o.customerEmail().toLowerCase().contains(needle))
                .toList();
        orders.getItems().setAll(shown);
        long pending = shown.stream().filter(o -> "PENDING".equals(o.paymentStatus())).count();
        summary.setText(shown.size() + " order(s), " + pending + " pending");
        shown.stream().filter(o -> selected != null && o.id() == selected).findFirst()
                .ifPresent(o -> orders.getSelectionModel().select(o));
    }

    private void setStatus(String status, Button busy) {
        Order o = orders.getSelectionModel().getSelectedItem();
        if (o.paymentStatus().equals(status)) {
            Dialogs.info("Nothing to change", "Order #" + o.id() + " is already " + status + ".");
            return;
        }
        Async.run(api.setPaymentStatus(o.id(), status), busy, this::replace);
    }

    private void markReturned(Button busy) {
        Order o = orders.getSelectionModel().getSelectedItem();
        OrderItem item = items.getSelectionModel().getSelectedItem();
        if (!item.isRental()) {
            Dialogs.info("Not a rental", "\"" + item.title() + "\" was bought, not rented.");
            return;
        }
        if (item.returnedAt() != null) {
            Dialogs.info("Already returned", "\"" + item.title() + "\" was already returned.");
            return;
        }
        if (Dialogs.confirm("Mark as returned?", "\"" + item.title() + "\" × " + item.quantity()
                + " goes back into stock.")) {
            Async.run(api.returnRental(o.id(), item.id()), busy, this::replace);
        }
    }

    /** Swaps an updated order into the list and keeps it selected. */
    private void replace(Order updated) {
        loaded = loaded.stream().map(o -> o.id() == updated.id() ? updated : o).toList();
        applyCustomerFilter();
        orders.getSelectionModel().select(updated);
        items.getItems().setAll(updated.items());
    }

    private Integer selectedId() {
        Order o = orders.getSelectionModel().getSelectedItem();
        return o == null ? null : o.id();
    }

    static String rentalStatus(OrderItem i) {
        if (!i.isRental()) {
            return "";
        }
        if (i.returnedAt() != null) {
            return "Returned " + Format.dateTime(i.returnedAt());
        }
        boolean late = i.dueDate() != null && i.dueDate().isBefore(java.time.LocalDate.now());
        return (late ? "OVERDUE, was due " : "Out, due back ") + i.dueDate();
    }
}
