package bookstore.client.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** The coloured strip across the top: app name, who is signed in, and Log out (FR2). */
public final class AppBar {
    private AppBar() {
    }

    public static HBox create(String appName, String signedInAs, Runnable onLogout) {
        Label title = new Label(appName);
        title.getStyleClass().add("app-title");
        Label who = new Label("Signed in as " + signedInAs);
        Button logout = new Button("Log out");
        logout.setOnAction(e -> onLogout.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(14, title, spacer, who, logout);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-bar");
        return bar;
    }
}
