package bookstore.client.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Standard pop-up messages. */
public final class Dialogs {
    private Dialogs() {
    }

    public static void error(String message) {
        show(Alert.AlertType.ERROR, "Something went wrong", message);
    }

    public static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, header, message);
    }

    public static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }
}
