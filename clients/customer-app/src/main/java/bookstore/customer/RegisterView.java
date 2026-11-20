package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.Async;
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

/** Sign-up screen (FR3). On success it logs the new customer straight in. */
public class RegisterView {
    private final StackPane root = new StackPane();

    public RegisterView(ApiClient api, Consumer<User> onRegistered, Runnable onBack) {
        Label heading = new Label("Create an account");
        heading.getStyleClass().add("login-title");

        TextField username = new TextField();
        username.setPromptText("3-30 letters, digits or _");
        TextField email = new TextField();
        email.setPromptText("you@example.com");
        TextField fullName = new TextField();
        fullName.setPromptText("Optional");
        PasswordField password = new PasswordField();
        password.setPromptText("At least 6 characters");
        PasswordField confirm = new PasswordField();

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        Button create = new Button("Create account");
        create.getStyleClass().add("primary");
        create.setDefaultButton(true);
        create.setMaxWidth(Double.MAX_VALUE);
        Hyperlink back = new Hyperlink("Back to log in");
        back.setOnAction(e -> onBack.run());

        VBox card = new VBox(8, heading,
                new Label("Username"), username,
                new Label("Email"), email,
                new Label("Full name"), fullName,
                new Label("Password"), password,
                new Label("Confirm password"), confirm,
                error, create, back);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(28));
        card.setMaxSize(380, Region.USE_PREF_SIZE);

        create.setOnAction(e -> {
            error.setText("");
            if (username.getText().isBlank() || email.getText().isBlank() || password.getText().isEmpty()) {
                error.setText("Username, email and password are required.");
                return;
            }
            if (!password.getText().equals(confirm.getText())) {
                error.setText("The two passwords don't match.");
                return;
            }
            String user = username.getText().trim();
            String pass = password.getText();
            Async.run(api.register(user, pass, email.getText().trim(), fullName.getText().trim())
                            .thenCompose(created -> api.login(user, pass, "CUSTOMER")),
                    card, onRegistered, error::setText);
        });

        root.getChildren().add(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
    }

    public Parent view() {
        return root;
    }
}
