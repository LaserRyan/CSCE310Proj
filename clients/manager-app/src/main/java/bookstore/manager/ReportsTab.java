package bookstore.manager;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.BestSeller;
import bookstore.client.model.Models.Order;
import bookstore.client.model.Models.SalesReport;
import bookstore.client.ui.Async;
import bookstore.client.ui.Dialogs;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Sales report for a date range, and best-selling books (extra credit). */
public class ReportsTab {
    private final ApiClient api;
    private final BorderPane root = new BorderPane();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final HBox stats = new HBox(10);
    private final TableView<BestSeller> best = new TableView<>();
    private final TableView<Order> orders = new TableView<>();
    private final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());

    public ReportsTab(ApiClient api) {
        this.api = api;

        Button run = new Button("Run report");
        run.getStyleClass().add("primary");
        run.setDefaultButton(true);
        run.setOnAction(e -> run());
        Button thisMonth = new Button("This month");
        thisMonth.setOnAction(e -> setRange(LocalDate.now().withDayOfMonth(1), LocalDate.now()));
        Button last30 = new Button("Last 30 days");
        last30.setOnAction(e -> setRange(LocalDate.now().minusDays(29), LocalDate.now()));
        Button thisYear = new Button("This year");
        thisYear.setOnAction(e -> setRange(LocalDate.now().withDayOfYear(1), LocalDate.now()));
        HBox bar = new HBox(8, new Label("From"), from, new Label("to"), to, run, thisMonth, last30, thisYear);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        best.getColumns().addAll(List.of(
                Tables.column("#", b -> best.getItems().indexOf(b) + 1, 40),
                Tables.column("Title", BestSeller::title, 230),
                Tables.column("Author", BestSeller::author, 150),
                Tables.column("Bought", BestSeller::unitsBought, 70),
                Tables.column("Rented", BestSeller::unitsRented, 70),
                Tables.money("Revenue", BestSeller::revenue, 95)));
        best.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        best.setPrefHeight(300);
        Tables.placeholder(best, "No sales in this period.");

        orders.getColumns().addAll(List.of(
                Tables.column("Order #", Order::id, 75),
                Tables.column("Placed", o -> Format.dateTime(o.createdAt()), 140),
                Tables.column("Customer", Order::customerUsername, 130),
                Tables.column("Items", Order::itemCount, 60),
                Tables.money("Total", Order::total, 95),
                Tables.status("Payment", Order::paymentStatus, 95)));
        orders.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        orders.setPrefHeight(300);
        Tables.placeholder(orders, "No orders in this period.");

        chart.setTitle("Top 10 books by copies sold or rented");
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setPrefHeight(320);
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        yAxis.setMinorTickVisible(false);
        yAxis.setLabel("Copies");
        // Copies are whole numbers: hide the in-between tick labels the axis picks on its own.
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number n) {
                return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.intValue()) : "";
            }

            @Override
            public Number fromString(String s) {
                return Integer.parseInt(s);
            }
        });
        chart.setCategoryGap(18);

        VBox left = new VBox(6, title("Best sellers"), best);
        VBox right = new VBox(6, title("Orders in this period"), orders);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox tablesRow = new HBox(14, left, right);

        stats.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(14, stats, chart, tablesRow);
        content.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        root.setTop(bar);
        root.setCenter(scroll);
        run();
    }

    public Parent view() {
        return root;
    }

    private void setRange(LocalDate start, LocalDate end) {
        from.setValue(start);
        to.setValue(end);
        run();
    }

    private void run() {
        if (from.getValue() == null || to.getValue() == null || to.getValue().isBefore(from.getValue())) {
            Dialogs.error("Pick a start date on or before the end date.");
            return;
        }
        // Whole local days: from the start of "from" up to (not including) the day after "to".
        ZoneId zone = ZoneId.systemDefault();
        Instant start = from.getValue().atStartOfDay(zone).toInstant();
        Instant end = to.getValue().plusDays(1).atStartOfDay(zone).toInstant();
        Async.run(api.salesReport(start, end), root, this::showReport);
        Async.run(api.bestsellers(start, end, 10), null, this::showBestSellers);
    }

    private void showReport(SalesReport r) {
        stats.getChildren().setAll(
                stat("Orders", String.valueOf(r.orderCount())),
                stat("Revenue", Format.money(r.revenue())),
                stat("Paid", Format.money(r.paidRevenue())),
                stat("Pending", Format.money(r.pendingRevenue())),
                stat("Copies sold", String.valueOf(r.unitsBought())),
                stat("Copies rented", String.valueOf(r.unitsRented())));
        orders.getItems().setAll(r.orders());
    }

    private void showBestSellers(List<BestSeller> list) {
        best.getItems().setAll(list);
        XYChart.Series<String, Number> bought = new XYChart.Series<>();
        bought.setName("Bought");
        XYChart.Series<String, Number> rented = new XYChart.Series<>();
        rented.setName("Rented");
        for (BestSeller b : list) {
            String label = b.title().length() > 22 ? b.title().substring(0, 21) + "…" : b.title();
            bought.getData().add(new XYChart.Data<>(label, b.unitsBought()));
            rented.getData().add(new XYChart.Data<>(label, b.unitsRented()));
        }
        chart.getData().setAll(List.of(bought, rented));
    }

    private static VBox stat(String name, String value) {
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        Label n = new Label(name);
        n.getStyleClass().add("muted");
        VBox box = new VBox(2, v, n);
        box.getStyleClass().add("stat");
        return box;
    }

    private static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }
}
