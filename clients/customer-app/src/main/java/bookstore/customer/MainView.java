package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.AppBar;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/** The customer's main window after login: Browse, Cart, My orders, Profile. */
public class MainView {
    private final BorderPane root = new BorderPane();

    public MainView(ApiClient api, User user, Runnable onLogout) {
        Cart cart = new Cart();
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        OrdersTab orders = new OrdersTab(api);
        BrowseTab browse = new BrowseTab(api, cart);
        Tab browseTab = new Tab("Browse books", browse.view());
        Tab cartTab = new Tab("Cart", null);
        Tab ordersTab = new Tab("My orders", orders.view());
        Tab profileTab = new Tab("Profile", new ProfileTab(api).view());

        CartTab cartView = new CartTab(api, cart, order -> {
            orders.refresh();
            browse.refresh();
            tabs.getSelectionModel().select(ordersTab);
        });
        cartTab.setContent(cartView.view());
        cart.lines().addListener((ListChangeListener<Cart.Line>) c ->
                cartTab.setText(cart.itemCount() == 0 ? "Cart" : "Cart (" + cart.itemCount() + ")"));

        ordersTab.setOnSelectionChanged(e -> {
            if (ordersTab.isSelected()) {
                orders.refresh();
            }
        });

        tabs.getTabs().addAll(browseTab, cartTab, ordersTab, profileTab);
        String name = user.fullName() == null ? user.username() : user.fullName() + " (" + user.username() + ")";
        root.setTop(AppBar.create("Bookstore", name, onLogout));
        root.setCenter(tabs);
    }

    public Parent view() {
        return root;
    }
}
