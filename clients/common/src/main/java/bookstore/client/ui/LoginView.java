package bookstore.client.ui;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * The login screen (FR1). Both apps use it; {@code role} decides which accounts
 * it accepts, so customers can't sign in to the manager app and vice versa (FR11).
 */
public class LoginView {
    private final StackPane root = new StackPane();

    /** @param onSignUp shown as a "Create an account" link when not null */
    public LoginView(ApiClient api, String title, String subtitle, String role,
                     Consumer<User> onLogin, Runnable onSignUp) {
        Label heading = new Label(title);
        heading.getStyleClass().add("login-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("muted");

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        Button login = new Button("Log in");
        login.getStyleClass().add("primary");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(10, heading, sub, new Label("Username"), username,
                new Label("Password"), password, error, login);
        if (onSignUp != null) {
            Hyperlink signUp = new Hyperlink("New here? Create an account");
            signUp.setOnAction(e -> onSignUp.run());
            card.getChildren().add(signUp);
        }
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(28));
        card.setMaxSize(360, Region.USE_PREF_SIZE);

        login.setOnAction(e -> {
            error.setText("");
            if (username.getText().isBlank() || password.getText().isEmpty()) {
                error.setText("Enter your username and password.");
                return;
            }
            Async.run(api.login(username.getText().trim(), password.getText(), role), card,
                    onLogin, error::setText);
        });

        root.getChildren().add(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        Platform.runLater(username::requestFocus);
    }

    public Parent view() {
        return root;
    }
}
