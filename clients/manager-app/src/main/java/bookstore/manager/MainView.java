package bookstore.manager;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.AppBar;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/** The manager's main window after login: Orders, Books, Sales reports. */
public class MainView {
    private final BorderPane root = new BorderPane();

    public MainView(ApiClient api, User user, Runnable onLogout) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        OrdersTab orders = new OrdersTab(api);
        Tab ordersTab = new Tab("Orders", orders.view());
        ordersTab.setOnSelectionChanged(e -> {
            if (ordersTab.isSelected()) {
                orders.refresh();
            }
        });
        tabs.getTabs().addAll(
                ordersTab,
                new Tab("Books", new BooksTab(api).view()),
                new Tab("Sales reports", new ReportsTab(api).view()));

        root.setTop(AppBar.create("Bookstore Manager", user.username(), onLogout));
        root.setCenter(tabs);
    }

    public Parent view() {
        return root;
    }
}
