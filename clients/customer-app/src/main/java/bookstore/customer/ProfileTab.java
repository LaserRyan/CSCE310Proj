package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.User;
import bookstore.client.ui.Async;
import bookstore.client.ui.Format;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The customer's profile (extra credit): see and change their details and password. */
public class ProfileTab {
    private final VBox root = new VBox(18);

    public ProfileTab(ApiClient api) {
        User user = api.currentUser();

        Label detailsTitle = new Label("Your details");
        detailsTitle.getStyleClass().add("section-title");
        TextField email = wide(new TextField(user.email()));
        TextField fullName = wide(new TextField(user.fullName() == null ? "" : user.fullName()));
        Label detailsStatus = new Label();
        Button save = new Button("Save changes");
        save.getStyleClass().add("primary");

        GridPane details = form();
        details.addRow(0, new Label("Username"), new Label(user.username()));
        details.addRow(1, new Label("Member since"), new Label(Format.dateTime(user.createdAt())));
        details.addRow(2, new Label("Email"), email);
        details.addRow(3, new Label("Full name"), fullName);
        details.add(save, 1, 4);
        details.add(detailsStatus, 1, 5);

        save.setOnAction(e -> Async.run(api.updateProfile(email.getText().trim(), fullName.getText().trim()), save,
                updated -> ok(detailsStatus, "Saved."),
                msg -> fail(detailsStatus, msg)));

        Label passwordTitle = new Label("Change password");
        passwordTitle.getStyleClass().add("section-title");
        PasswordField current = (PasswordField) wide(new PasswordField());
        PasswordField next = (PasswordField) wide(new PasswordField());
        PasswordField confirm = (PasswordField) wide(new PasswordField());
        Label passwordStatus = new Label();
        Button change = new Button("Change password");

        GridPane password = form();
        password.addRow(0, new Label("Current password"), current);
        password.addRow(1, new Label("New password"), next);
        password.addRow(2, new Label("Confirm new password"), confirm);
        password.add(change, 1, 3);
        password.add(passwordStatus, 1, 4);

        change.setOnAction(e -> {
            if (!next.getText().equals(confirm.getText())) {
                fail(passwordStatus, "The new passwords don't match.");
                return;
            }
            Async.run(api.changePassword(current.getText(), next.getText()), change, done -> {
                current.clear();
                next.clear();
                confirm.clear();
                ok(passwordStatus, "Password changed.");
            }, msg -> fail(passwordStatus, msg));
        });

        Label hint = new Label("Your order history is on the My orders tab.");
        hint.getStyleClass().add("muted");
        root.getChildren().addAll(detailsTitle, details, passwordTitle, password, hint);
        root.setPadding(new Insets(20));
        root.setMaxWidth(560);
    }

    public Parent view() {
        return root;
    }

    private static TextField wide(TextField field) {
        field.setPrefColumnCount(26);
        return field;
    }

    private static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        return grid;
    }

    private static void ok(Label label, String text) {
        label.getStyleClass().setAll("label", "ok-text");
        label.setText(text);
    }

    private static void fail(Label label, String text) {
        label.getStyleClass().setAll("label", "error-text");
        label.setText(text);
    }
}
