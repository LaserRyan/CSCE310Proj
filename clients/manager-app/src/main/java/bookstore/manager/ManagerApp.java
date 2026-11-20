package bookstore.manager;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.Async;
import bookstore.client.ui.LoginView;
import bookstore.client.ui.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/** Manager app entry point (FR11). Only manager accounts can log in here. */
public class ManagerApp extends Application {
    private final ApiClient api = ApiClient.fromEnvironment();
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new Pane(), 1240, 780);
        Theme.apply(scene);
        stage.setTitle("Bookstore Manager");
        stage.setScene(scene);
        Async.setOnSessionExpired(this::showLogin);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        scene.setRoot(new LoginView(api, "Bookstore Manager", "Staff only. Log in with a manager account",
                "MANAGER", this::showMain, null).view());
    }

    private void showMain(User user) {
        scene.setRoot(new MainView(api, user, () -> Async.run(api.logout(), null, ok -> showLogin())).view());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
