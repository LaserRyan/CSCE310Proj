package bookstore.tools;

import bookstore.client.api.ApiClient;
import bookstore.client.ui.LoginView;
import bookstore.client.ui.Theme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Walks through both apps and saves a PNG of each screen to docs/screenshots.
 * Steps run one after another with a pause between them so network calls can finish.
 */
public class Screenshots extends Application {
    // javafx:run starts in the module folder; screenshots go to the project's docs folder.
    private static final File OUT = new File("../../docs/screenshots");
    private static final long PAUSE_MS = 1800;

    private final Deque<Runnable> steps = new ArrayDeque<>();
    private Scene scene;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        OUT.mkdirs();
        scene = new Scene(new Pane(), 1240, 780);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.show();

        ApiClient customer = ApiClient.fromEnvironment();
        ApiClient manager = ApiClient.fromEnvironment();

        // ---------------- customer app
        step(() -> show(new LoginView(customer, "Bookstore", "Log in to browse and order books", "CUSTOMER",
                u -> { }, () -> { }).view()));
        step(() -> save("01-customer-login"));
        step(() -> show(new bookstore.customer.RegisterView(customer, u -> { }, () -> { }).view()));
        step(() -> save("02-customer-register"));
        // Some activity first, so the history, reviews and reports have something to show.
        step(() -> customer.login("alice", "alice123", "CUSTOMER")
                .thenCompose(u -> customer.writeReview(firstBookId(customer, "1984"), 5,
                        "Still unsettling decades later. A must read."))
                .thenCompose(b -> customer.placeOrder(List.of(
                        new ApiClient.OrderLine(firstBookId(customer, "Hobbit"), "BUY", 2),
                        new ApiClient.OrderLine(firstBookId(customer, "Emma"), "RENT", 1))))
                .thenCompose(o -> manager.login("manager", "manager123", "MANAGER")
                        .thenCompose(m -> manager.setPaymentStatus(o.id(), "PAID")))
                .thenAccept(o -> Platform.runLater(() ->
                        show(new bookstore.customer.MainView(customer, customer.currentUser(), () -> { }).view())))
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                }));
        step(() -> { }); // extra time for the calls above
        step(() -> {
            TableView<?> table = find(TableView.class);
            table.getSelectionModel().clearAndSelect(0); // 1984: shows its review on the right
        });
        step(() -> { }); // let the reviews load
        step(() -> save("03a-customer-book-details"));
        step(() -> {
            TableView<?> table = find(TableView.class);
            table.getSelectionModel().selectIndices(3, 15, 17);
        });
        step(() -> save("03-customer-browse"));
        step(() -> {
            button("Add selected to cart: Buy").fire();
            TableView<?> table = find(TableView.class);
            table.getSelectionModel().clearAndSelect(17);
            button("Add selected to cart: Rent").fire();
            tabs().getSelectionModel().select(1);
        });
        step(() -> save("04-customer-cart"));
        step(() -> customer.placeOrder(List.of(
                        new ApiClient.OrderLine(firstBookId(customer, "1984"), "BUY", 1),
                        new ApiClient.OrderLine(firstBookId(customer, "Dune"), "RENT", 1)))
                .thenCompose(o -> customer.bill(o.id()))
                .thenAccept(html -> Platform.runLater(() -> {
                    WebView web = new WebView();
                    web.getEngine().loadContent(html);
                    show(web);
                })));
        step(() -> save("05-bill"));
        step(() -> show(new bookstore.customer.MainView(customer, customer.currentUser(), () -> { }).view()));
        step(() -> tabs().getSelectionModel().select(2));
        step(() -> save("06-customer-orders"));
        step(() -> tabs().getSelectionModel().select(3));
        step(() -> save("07-customer-profile"));

        // ---------------- manager app
        step(() -> show(new LoginView(manager, "Bookstore Manager", "Staff only. Log in with a manager account",
                "MANAGER", u -> { }, null).view()));
        step(() -> save("08-manager-login"));
        step(() -> show(new bookstore.manager.MainView(manager, manager.currentUser(), () -> { }).view()));
        step(() -> find(TableView.class).getSelectionModel().select(0));
        step(() -> save("09-manager-orders"));
        step(() -> tabs().getSelectionModel().select(1));
        step(() -> save("10-manager-books"));
        step(() -> tabs().getSelectionModel().select(2));
        step(() -> save("11-manager-reports"));
        step(() -> {
            System.out.println("Saved screenshots to " + OUT.getAbsolutePath());
            javafx.stage.Window.getWindows().stream()
                    .filter(w -> w != stage && w.getScene() != null)
                    .forEach(w -> System.out.println("Left open: " + w.getScene().getRoot().lookupAll(".label")
                            .stream().map(n -> ((javafx.scene.control.Label) n).getText()).toList()));
            Platform.exit();
        });

        next();
    }

    private void step(Runnable r) {
        steps.add(r);
    }

    private void next() {
        Runnable r = steps.poll();
        if (r == null) {
            return;
        }
        try {
            r.run();
        } catch (Exception e) {
            e.printStackTrace();
            Platform.exit();
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException ignored) {
                // fall through
            }
            Platform.runLater(this::next);
        }).start();
    }

    private void show(Parent root) {
        scene.setRoot(root);
    }

    private void save(String name) {
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", new File(OUT, name + ".png"));
            System.out.println("saved " + name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TabPane tabs() {
        return find(TabPane.class);
    }

    /** The first visible node of a type in the current screen. */
    @SuppressWarnings("unchecked")
    private <T extends Node> T find(Class<T> type) {
        return (T) scene.getRoot().lookupAll("*").stream()
                .filter(type::isInstance)
                .filter(Screenshots::onScreen)
                .findFirst().orElseThrow(() -> new IllegalStateException("No " + type.getSimpleName()));
    }

    /** Visible, and so are all its parents (hidden tabs keep their content, just invisible). */
    private static boolean onScreen(Node n) {
        for (Node p = n; p != null; p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private Button button(String text) {
        return scene.getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && text.equals(b.getText()) && onScreen(b))
                .map(n -> (Button) n)
                .findFirst().orElseThrow(() -> new IllegalStateException("No button " + text));
    }

    private static String firstBookId(ApiClient api, String keyword) {
        return api.searchBooks(keyword).join().get(0).id();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
