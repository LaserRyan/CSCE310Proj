package bookstore.manager;

import bookstore.client.model.Models.Book;
import bookstore.client.ui.Theme;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The add / edit book form. Returns the fields to send, or empty if cancelled. */
public final class BookForm {
    private BookForm() {
    }

    /** @param book the book to edit, or null to add a new one */
    public static Optional<Map<String, Object>> show(Window owner, Book book) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(book == null ? "Add book" : "Edit book");
        dialog.setHeaderText(book == null ? "Add a new book to the catalog" : "Edit \"" + book.title() + "\"");
        Theme.apply(dialog.getDialogPane().getScene());

        TextField title = field(book == null ? "" : book.title());
        TextField author = field(book == null ? "" : book.author());
        TextField genre = field(book == null || book.genre() == null ? "" : book.genre());
        TextField year = field(book == null || book.publishedYear() == null ? "" : book.publishedYear().toString());
        TextField isbn = field(book == null || book.isbn() == null ? "" : book.isbn());
        TextField price = field(book == null ? "" : String.format("%.2f", book.price()));
        TextField rent = field(book == null ? "" : String.format("%.2f", book.rentPrice()));
        TextField copies = field(book == null ? "1" : String.valueOf(book.copies()));
        TextArea description = new TextArea(book == null || book.description() == null ? "" : book.description());
        description.setPrefRowCount(3);
        description.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("error-text");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        int r = 0;
        grid.addRow(r++, new Label("Title *"), title);
        grid.addRow(r++, new Label("Author *"), author);
        grid.addRow(r++, new Label("Buy price ($) *"), price);
        grid.addRow(r++, new Label("Rent price ($) *"), rent);
        grid.addRow(r++, new Label("Copies in stock *"), copies);
        grid.addRow(r++, new Label("Genre"), genre);
        grid.addRow(r++, new Label("Year published"), year);
        grid.addRow(r++, new Label("ISBN"), isbn);
        grid.addRow(r++, new Label("Description"), description);
        grid.add(error, 1, r);
        dialog.getDialogPane().setContent(grid);

        ButtonType save = new ButtonType(book == null ? "Add book" : "Save", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        Map<String, Object> fields = new LinkedHashMap<>();
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, e -> {
            fields.clear();
            String problem = collect(fields, title, author, price, rent, copies, genre, year, isbn, description);
            if (problem != null) {
                error.setText(problem);
                e.consume(); // keep the dialog open
            }
        });
        dialog.setResultConverter(button -> button == save ? fields : null);
        return dialog.showAndWait();
    }

    private static String collect(Map<String, Object> out, TextField title, TextField author, TextField price,
                                  TextField rent, TextField copies, TextField genre, TextField year,
                                  TextField isbn, TextArea description) {
        if (title.getText().isBlank() || author.getText().isBlank()) {
            return "Title and author are required.";
        }
        out.put("title", title.getText().trim());
        out.put("author", author.getText().trim());
        try {
            out.put("price", Double.parseDouble(price.getText().trim()));
            out.put("rent_price", Double.parseDouble(rent.getText().trim()));
        } catch (NumberFormatException ex) {
            return "Prices must be numbers, like 12.99.";
        }
        try {
            out.put("copies", Integer.parseInt(copies.getText().trim()));
            if (!year.getText().isBlank()) {
                out.put("published_year", Integer.parseInt(year.getText().trim()));
            }
        } catch (NumberFormatException ex) {
            return "Copies and year must be whole numbers.";
        }
        out.put("genre", genre.getText().trim());
        out.put("isbn", isbn.getText().trim());
        out.put("description", description.getText().trim());
        return null;
    }

    private static TextField field(String value) {
        TextField f = new TextField(value);
        f.setPrefColumnCount(28);
        return f;
    }
}
