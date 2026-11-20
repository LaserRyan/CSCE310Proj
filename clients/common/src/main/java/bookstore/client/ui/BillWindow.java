package bookstore.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Shows an HTML bill, with buttons to print it or save a copy (FR8, FR9). */
public final class BillWindow {
    private BillWindow() {
    }

    public static void show(Window owner, int orderId, String html) {
        WebView web = new WebView();
        web.getEngine().loadContent(html);

        Button print = new Button("Print…");
        print.getStyleClass().add("primary");
        print.setOnAction(e -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) {
                Dialogs.error("No printer is set up on this computer.");
                return;
            }
            if (job.showPrintDialog(web.getScene().getWindow())) {
                web.getEngine().print(job);
                job.endJob();
            }
        });

        Button save = new Button("Save as HTML…");
        save.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName("order-" + orderId + ".html");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML page", "*.html"));
            File file = chooser.showSaveDialog(web.getScene().getWindow());
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    Dialogs.error("Could not save the bill: " + ex.getMessage());
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label label = new Label("Bill for order #" + orderId);
        label.getStyleClass().add("section-title");
        HBox bar = new HBox(8, label, spacer, save, print);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Bill - Order #" + orderId);
        Scene scene = new Scene(new BorderPane(web, bar, null, null, null), 820, 760);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.show();
    }
}
