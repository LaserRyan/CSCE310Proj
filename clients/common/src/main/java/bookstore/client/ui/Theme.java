package bookstore.client.ui;

import javafx.scene.Scene;

/** Applies the shared stylesheet. */
public final class Theme {
    private Theme() {
    }

    public static void apply(Scene scene) {
        scene.getStylesheets().add(Theme.class.getResource("/bookstore/client/ui/app.css").toExternalForm());
    }
}
