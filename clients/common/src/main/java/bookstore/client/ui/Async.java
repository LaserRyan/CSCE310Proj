package bookstore.client.ui;

import bookstore.client.api.ApiException;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Runs a network call in the background and hands the result back on the
 * JavaFX thread. While it runs, the given control is disabled and shows a
 * busy cursor, so the user can't click it twice. The window stays usable.
 */
public final class Async {
    private static Runnable onSessionExpired = () -> { };

    private Async() {
    }

    /** What to do when the server says the login is no longer valid (e.g. go back to the login screen). */
    public static void setOnSessionExpired(Runnable action) {
        onSessionExpired = action;
    }

    public static <T> void run(CompletableFuture<T> call, Node busy, Consumer<T> onSuccess) {
        run(call, busy, onSuccess, Dialogs::error);
    }

    public static <T> void run(CompletableFuture<T> call, Node busy, Consumer<T> onSuccess, Consumer<String> onError) {
        if (busy != null) {
            busy.setDisable(true);
            busy.setCursor(Cursor.WAIT);
        }
        call.whenComplete((result, err) -> Platform.runLater(() -> {
            if (busy != null) {
                busy.setDisable(false);
                busy.setCursor(Cursor.DEFAULT);
            }
            if (err == null) {
                onSuccess.accept(result);
                return;
            }
            Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
            if (cause instanceof ApiException api && api.isSessionExpired()) {
                Dialogs.error(api.getMessage());
                onSessionExpired.run();
                return;
            }
            onError.accept(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }));
    }
}
