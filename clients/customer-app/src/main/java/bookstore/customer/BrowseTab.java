package bookstore.customer;

import bookstore.client.api.ApiClient;
import bookstore.client.model.Models.Book;
import bookstore.client.model.Models.Review;
import bookstore.client.ui.Async;
import bookstore.client.ui.Format;
import bookstore.client.ui.Tables;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Search (FR4), results list (FR5), picking books for the cart (FR6), and the
 * selected book's details and reviews (extra credit).
 */
public class BrowseTab {
    private final ApiClient api;
    private final Cart cart;
    private final BorderPane root = new BorderPane();
    private final TextField search = new TextField();
    private final TableView<Book> table = new TableView<>();
    private final Label resultCount = new Label();
    private final VBox details = new VBox(10);

    public BrowseTab(ApiClient api, Cart cart) {
        this.api = api;
        this.cart = cart;

        // ---- search bar
        search.setPromptText("Search by title or author");
        search.setPrefWidth(360);
        Button go = new Button("Search");
        go.getStyleClass().add("primary");
        go.setDefaultButton(true);
        go.setOnAction(e -> refresh());
        Button clear = new Button("Show all");
        clear.setOnAction(e -> {
            search.clear();
            refresh();
        });
        resultCount.getStyleClass().add("muted");
        HBox bar = new HBox(8, search, go, clear, resultCount);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("toolbar");

        // ---- results
        table.getColumns().addAll(List.of(
                Tables.column("Title", Book::title, 250),
                Tables.column("Author", Book::author, 160),
                Tables.money("Buy price", Book::price, 85),
                Tables.money("Rent price", Book::rentPrice, 85),
                Tables.column("In stock", Book::copies, 70),
                Tables.column("Rating", b -> Format.rating(b.avgRating(), b.reviewCount()), 95)));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Tables.placeholder(table, "No books match your search.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, book) -> showDetails(book));

        Button addBuy = new Button("Add selected to cart: Buy");
        addBuy.getStyleClass().add("primary");
        addBuy.setOnAction(e -> addSelected(Cart.BUY));
        Button addRent = new Button("Add selected to cart: Rent");
        addRent.setOnAction(e -> addSelected(Cart.RENT));
        Label hint = new Label("Tip: Ctrl+click or Shift+click to pick several books.");
        hint.getStyleClass().add("muted");
        HBox actions = new HBox(8, addBuy, addRent, hint);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(8, 10, 10, 10));

        BorderPane left = new BorderPane(table, null, null, actions, null);
        BorderPane.setMargin(table, new Insets(10, 10, 0, 10));

        details.setPadding(new Insets(14));
        ScrollPane detailScroll = new ScrollPane(details);
        detailScroll.setFitToWidth(true);
        showDetails(null);

        SplitPane split = new SplitPane(left, detailScroll);
        split.setDividerPositions(0.66);

        root.setTop(bar);
        root.setCenter(split);
        refresh();
    }

    public Parent view() {
        return root;
    }

    /** Runs the search again (also used after an order, so stock counts are current). */
    public void refresh() {
        Async.run(api.searchBooks(search.getText().trim()), root, books -> {
            table.getItems().setAll(books);
            resultCount.setText(books.size() + (books.size() == 1 ? " book" : " books"));
        });
    }

    private void addSelected(String type) {
        List<Book> picked = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (picked.isEmpty()) {
            resultCount.setText("Select one or more books first.");
            return;
        }
        picked.forEach(book -> cart.add(book, type));
        resultCount.setText("Added " + picked.size() + (picked.size() == 1 ? " book" : " books")
                + " to cart (" + (Cart.RENT.equals(type) ? "rent" : "buy") + ")");
    }

    // ------------------------------------------------------- details pane

    private void showDetails(Book book) {
        details.getChildren().clear();
        if (book == null) {
            Label none = new Label("Select a book to see its details and reviews.");
            none.getStyleClass().add("muted");
            details.getChildren().add(none);
            return;
        }
        Label title = new Label(book.title());
        title.getStyleClass().add("book-title");
        title.setWrapText(true);
        Label author = new Label("by " + book.author());
        String meta = String.join(" · ", List.of(
                book.genre() == null ? "" : book.genre(),
                book.publishedYear() == null ? "" : String.valueOf(book.publishedYear())).stream()
                .filter(s -> !s.isEmpty()).toList());
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("muted");
        Label prices = new Label("Buy " + Format.money(book.price()) + "   ·   Rent "
                + Format.money(book.rentPrice()) + " for 30 days");
        Label stock = new Label(book.copies() > 0 ? book.copies() + " in stock" : "Out of stock");
        stock.getStyleClass().add(book.copies() > 0 ? "ok-text" : "error-text");
        Label rating = new Label(Format.rating(book.avgRating(), book.reviewCount()));

        details.getChildren().addAll(title, author, metaLabel, prices, stock, rating);
        if (book.description() != null) {
            Label desc = new Label(book.description());
            desc.setWrapText(true);
            details.getChildren().add(desc);
        }

        Label reviewsTitle = new Label("Reviews");
        reviewsTitle.getStyleClass().add("section-title");
        VBox reviewList = new VBox(6);
        details.getChildren().addAll(new Separator(), reviewForm(book), new Separator(), reviewsTitle, reviewList);
        loadReviews(book, reviewList);
    }

    private void loadReviews(Book book, VBox into) {
        Label loading = new Label("Loading…");
        loading.getStyleClass().add("muted");
        into.getChildren().setAll(loading);
        Async.run(api.reviews(book.id()), null, reviews -> {
            into.getChildren().clear();
            if (reviews.isEmpty()) {
                Label none = new Label("No reviews yet. Be the first!");
                none.getStyleClass().add("muted");
                into.getChildren().add(none);
            }
            for (Review r : reviews) {
                Label head = new Label(Format.stars(r.rating()) + "  " + r.username());
                head.setStyle("-fx-font-weight: bold;");
                Label when = new Label(Format.dateTime(r.createdAt()));
                when.getStyleClass().add("muted");
                VBox card = new VBox(3, head, when);
                if (r.comment() != null && !r.comment().isBlank()) {
                    Label text = new Label(r.comment());
                    text.setWrapText(true);
                    card.getChildren().add(text);
                }
                card.getStyleClass().add("review-card");
                into.getChildren().add(card);
            }
        });
    }

    private VBox reviewForm(Book book) {
        Label heading = new Label("Rate this book");
        heading.getStyleClass().add("section-title");
        ChoiceBox<Integer> stars = new ChoiceBox<>();
        stars.getItems().addAll(5, 4, 3, 2, 1);
        stars.setValue(5);
        stars.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer n) {
                return n == null ? "" : Format.stars(n);
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        TextArea comment = new TextArea();
        comment.setPromptText("What did you think? (optional)");
        comment.setPrefRowCount(3);
        comment.setWrapText(true);
        Button submit = new Button("Submit review");
        Label status = new Label();
        status.setWrapText(true);
        submit.setOnAction(e -> Async.run(api.writeReview(book.id(), stars.getValue(), comment.getText().trim()), submit,
                updated -> {
                    replaceInTable(updated);
                    showDetails(updated);
                },
                msg -> {
                    status.getStyleClass().setAll("label", "error-text");
                    status.setText(msg);
                }));
        HBox row = new HBox(8, new Label("Stars"), stars, submit);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, heading, row, comment, status);
        VBox.setVgrow(comment, Priority.NEVER);
        return box;
    }

    private void replaceInTable(Book updated) {
        for (int i = 0; i < table.getItems().size(); i++) {
            if (table.getItems().get(i).id().equals(updated.id())) {
                table.getItems().set(i, updated);
                table.getSelectionModel().clearAndSelect(i);
                return;
            }
        }
    }
}
