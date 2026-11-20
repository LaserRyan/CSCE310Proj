package bookstore.client.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.function.Function;

/** Shortcuts for building read-only table columns. */
public final class Tables {
    private Tables() {
    }

    /** A plain column. Numbers are right-aligned so they line up. */
    public static <T> TableColumn<T, Object> column(String title, Function<T, Object> value, double width) {
        TableColumn<T, Object> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setAlignment(item instanceof Number ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            }
        });
        col.setPrefWidth(width);
        return col;
    }

    /** A right-aligned money column that still sorts by the number. */
    public static <T> TableColumn<T, Double> money(String title, Function<T, Double> value, double width) {
        TableColumn<T, Double> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Format.money(item));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        col.setPrefWidth(width);
        return col;
    }

    /** A payment-status column coloured green (PAID) or amber (PENDING). */
    public static <T> TableColumn<T, String> status(String title, Function<T, String> value, double width) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-PAID", "status-PENDING");
                setText(empty ? null : item);
                if (!empty && item != null) {
                    getStyleClass().add("status-" + item);
                }
            }
        });
        col.setPrefWidth(width);
        return col;
    }

    public static void placeholder(TableView<?> table, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        table.setPlaceholder(label);
    }
}
