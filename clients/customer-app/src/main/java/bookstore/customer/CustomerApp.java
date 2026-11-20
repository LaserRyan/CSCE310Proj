package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.Async;
import bookstore.client.ui.LoginView;
import bookstore.client.ui.Theme;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Customer app entry point. Switches between the login, sign-up and main screens. */
public class CustomerApp extends Application {
    private final ApiClient api = ApiClient.fromEnvironment();
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new javafx.scene.layout.Pane(), 1180, 760);
        Theme.apply(scene);
        stage.setTitle("Bookstore");
        stage.setScene(scene);
        Async.setOnSessionExpired(this::showLogin);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        show(new LoginView(api, "Bookstore", "Log in to browse and order books", "CUSTOMER",
                this::showMain, this::showRegister).view());
    }

    private void showRegister() {
        show(new RegisterView(api, this::showMain, this::showLogin).view());
    }

    private void showMain(User user) {
        show(new MainView(api, user, () -> Async.run(api.logout(), null, ok -> showLogin())).view());
    }

    private void show(Parent root) {
        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
