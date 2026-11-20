package bookstore.manager;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.Book;
import bookstore.client.ui.Async;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.List;

/** The catalog for managers: search, add a book, edit one (FR14). */
public class BooksTab {
    private final ApiClient api;
    private final BorderPane root = new BorderPane();
    private final TableView<Book> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label summary = new Label();

    public BooksTab(ApiClient api) {
        this.api = api;

        search.setPromptText("Search by title or author");
        search.setPrefWidth(300);
        Button go = new Button("Search");
        go.setDefaultButton(true);
        go.setOnAction(e -> refresh());
        Button add = new Button("Add book…");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> BookForm.show(root.getScene().getWindow(), null)
                .ifPresent(fields -> Async.run(api.addBook(fields), root, book -> {
                    table.getItems().add(0, book);
                    table.getSelectionModel().select(book);
                    summary.setText("Added \"" + book.title() + "\".");
                })));
        Button edit = new Button("Edit selected…");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> editSelected());
        summary.getStyleClass().add("muted");
        HBox bar = new HBox(8, search, go, add, edit, summary);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        table.getColumns().addAll(List.of(
                Tables.column("Title", Book::title, 250),
                Tables.column("Author", Book::author, 170),
                Tables.column("Genre", Book::genre, 110),
                Tables.column("Year", Book::publishedYear, 60),
                Tables.money("Buy price", Book::price, 85),
                Tables.money("Rent price", Book::rentPrice, 85),
                Tables.column("Copies", Book::copies, 70),
                Tables.column("Rating", b -> Format.rating(b.avgRating(), b.reviewCount()), 100)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(table, "No books match.");
        table.setRowFactory(t -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    editSelected();
                }
            });
            return row;
        });

        Label hint = new Label("Double-click a book to edit it, e.g. to change the number of copies.");
        hint.getStyleClass().add("muted");
        hint.setPadding(new Insets(6, 10, 10, 10));

        root.setTop(bar);
        root.setCenter(table);
        root.setBottom(hint);
        BorderPane.setMargin(table, new Insets(10, 10, 0, 10));
        refresh();
    }

    public Parent view() {
        return root;
    }

    private void refresh() {
        Async.run(api.searchBooks(search.getText().trim()), root, books -> {
            table.getItems().setAll(books);
            summary.setText(books.size() + " book(s)");
        });
    }

    private void editSelected() {
        Book book = table.getSelectionModel().getSelectedItem();
        BookForm.show(root.getScene().getWindow(), book)
                .ifPresent(fields -> Async.run(api.updateBook(book.id(), fields), root, updated -> {
                    int i = table.getItems().indexOf(book);
                    table.getItems().set(i, updated);
                    table.getSelectionModel().select(i);
                    summary.setText("Saved \"" + updated.title() + "\".");
                }));
    }
}
