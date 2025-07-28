import javafx.animation.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ColorApp extends Application {
    private Set<String> favoritesSet = new HashSet<>();
    private HBox favoritesBox = new HBox(5);
    private LinkedList<String> historyList = new LinkedList<>();
    private HBox historyBox = new HBox(5);
    private static final int MAX_HISTORY = 10;
    private static final String HISTORY_FILE = "history.txt";
    private static final String FAVORITES_FILE = "favorites.txt";
    private TextField colorInput;
    private Label contrastLabel = new Label("نسبت کنتراست: ---");
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        loadHistory();
        loadFavorites();
        this.primaryStage = primaryStage;

        colorInput = new TextField();
        colorInput.setPromptText("مثلاً #FF5733");
        colorInput.setPrefWidth(140);
        colorInput.setStyle("-fx-font-size: 14px; -fx-border-radius: 5px; -fx-background-radius: 5px;");

        Rectangle previewRect = new Rectangle(50, 35);
        previewRect.setStroke(Color.DARKGRAY);
        previewRect.setArcWidth(12);
        previewRect.setArcHeight(12);
        previewRect.setFill(Color.LIGHTGRAY);

        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setStyle("-fx-pref-width: 45px;");

        updateHistoryBox();

        contrastLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        colorInput.textProperty().addListener((obs, oldText, newText) -> {
            try {
                Color c = Color.web(newText.trim());
                previewRect.setFill(c);
                double contrast = getContrastRatio(c, Color.WHITE);
                String level = getWCAGLevel(contrast);
                contrastLabel.setText(String.format("نسبت کنتراست با متن سفید: %.2f (%s)", contrast, level));
            } catch (Exception ex) {
                previewRect.setFill(Color.LIGHTGRAY);
                contrastLabel.setText("نسبت کنتراست: ---");
            }
        });

        colorPicker.setOnAction(e -> {
            Color selectedColor = colorPicker.getValue();
            previewRect.setFill(selectedColor);
            String hex = toHexCode(selectedColor);
            colorInput.setText(hex);
            updateHistory(hex);
        });

        HBox inputRow = new HBox(10, colorInput, previewRect, colorPicker);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(10, 0, 10, 0));

        Button showButton = new Button("نمایش رنگ‌های پیشنهادی");
        showButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        showButton.setPrefWidth(200);
        showButton.setPadding(new Insets(8, 10, 8, 10));

        Button addToFavorites = new Button("★");
        addToFavorites.setStyle("-fx-background-color: #ffd400; -fx-font-weight: bold; -fx-font-size: 18px; -fx-background-radius: 8;");
        addToFavorites.setPrefWidth(45);
        addToFavorites.setPadding(new Insets(8, 10, 8, 10));

        HBox buttonsRow = new HBox(10, showButton, addToFavorites);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        HBox colorBox = new HBox(30);
        colorBox.setPadding(new Insets(15, 0, 0, 0));

        showButton.setOnAction(e -> {
            String input = colorInput.getText().trim();
            colorBox.getChildren().clear();

            try {
                Color baseColor = Color.web(input);
                updateHistory(toHexCode(baseColor));

                colorBox.getChildren().add(makeColorGroup("مکمل", List.of(getComplementaryColor(baseColor))));
                colorBox.getChildren().add(makeColorGroup("آنالوگ", getAnalogousColors(baseColor)));
                colorBox.getChildren().add(makeColorGroup("سه‌گانه", getTriadicColors(baseColor)));

            } catch (Exception ex) {
                Label errorLabel = new Label("کد رنگ نامعتبر");
                errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
                colorBox.getChildren().add(errorLabel);
            }
        });

        addToFavorites.setOnAction(e -> {
            String input = colorInput.getText().trim();
            try {
                Color color = Color.web(input);
                String hex = toHexCode(color);
                if (favoritesSet.add(hex)) {
                    Rectangle rect = makeColorRect(color);

                    rect.setOnMouseClicked(ev -> {
                        if (ev.getClickCount() == 2) {
                            FadeTransition fadeOut = new FadeTransition(Duration.millis(400), rect);
                            fadeOut.setFromValue(1.0);
                            fadeOut.setToValue(0.0);
                            fadeOut.setOnFinished(fadeEvent -> {
                                favoritesBox.getChildren().remove(rect);
                                favoritesSet.remove(hex);
                                saveFavorites();
                                showToast(this.primaryStage, "رنگ " + hex + " از مورد علاقه حذف شد!");
                            });
                            fadeOut.play();
                        } else {
                            colorInput.setText(hex);
                        }
                    });

                    favoritesBox.getChildren().add(rect);
                    saveFavorites();
                }
            } catch (Exception ignored) {}
        });


        Label favoritesLabel = new Label("رنگ‌های مورد علاقه:");
        VBox favoritesSection = new VBox(5, favoritesLabel, favoritesBox);
        favoritesSection.setPadding(new Insets(10, 0, 0, 0));

        Label historyLabel = new Label("تاریخچه رنگ‌ها:");
        Button clearHistoryButton = new Button("پاک کردن تاریخچه");
        clearHistoryButton.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-size: 13px;");
        clearHistoryButton.setOnAction(e -> clearHistory());
        HBox historyHeader = new HBox(10, historyLabel, clearHistoryButton);
        historyHeader.setAlignment(Pos.CENTER_LEFT);

        VBox historySection = new VBox(5, historyHeader, historyBox);
        historySection.setPadding(new Insets(10, 0, 0, 0));

        VBox mainLayout = new VBox(15, inputRow, buttonsRow, colorBox, contrastLabel, favoritesSection, historySection);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setStyle("-fx-font-family: 'Tahoma'; -fx-background-color: #F9F9F9;");

        StackPane root = new StackPane(mainLayout);

        Scene scene = new Scene(root, 780, 500);
        primaryStage.setTitle("سیستم پیشنهاد رنگ");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            saveHistory();
            saveFavorites();
        });
        primaryStage.show();
    }

    private void clearHistory() {
        historyList.clear();
        historyBox.getChildren().clear();
        try {
            Files.deleteIfExists(Paths.get(HISTORY_FILE));
        } catch (IOException ignored) {}
    }

    private void showToast(Stage stage, String message) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 20px;");
        toastLabel.setOpacity(0);

        StackPane root = (StackPane) stage.getScene().getRoot();
        root.getChildren().add(toastLabel);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toastLabel, new Insets(0, 0, 50, 0));

        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), toastLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(0.8);
        fadeIn.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastLabel);
                fadeOut.setFromValue(0.8);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(ev -> root.getChildren().remove(toastLabel));
                fadeOut.play();
            });
            pause.play();
        });
        fadeIn.play();
    }

    private void updateHistory(String hexColor) {
        historyList.remove(hexColor);
        historyList.addFirst(hexColor);
        while (historyList.size() > MAX_HISTORY) {
            historyList.removeLast();
        }
        updateHistoryBox();
    }

    private void updateHistoryBox() {
        historyBox.getChildren().clear();
        for (String hex : historyList) {
            Color color = Color.web(hex);
            Rectangle rect = makeColorRect(color);
            rect.setOnMouseClicked(e -> colorInput.setText(hex));
            historyBox.getChildren().add(rect);
        }
    }

    private void saveHistory() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HISTORY_FILE))) {
            for (String hex : historyList) {
                writer.write(hex);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(HISTORY_FILE));
            Collections.reverse(lines);
            for (String line : lines) {
                if (line.matches("#[0-9A-Fa-f]{6}")) {
                    historyList.addFirst(line.toUpperCase());
                    if (historyList.size() > MAX_HISTORY) break;
                }
            }
        } catch (IOException ignored) {}
    }

    private VBox makeColorGroup(String title, List<Color> colors) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");

        HBox colorsBox = new HBox(15);
        for (Color c : colors) {
            colorsBox.getChildren().add(makeColorVBox(c));
        }

        VBox groupBox = new VBox(titleLabel, colorsBox);
        groupBox.setAlignment(Pos.TOP_CENTER);
        groupBox.setPrefWidth(200);
        groupBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-radius: 12; -fx-background-radius: 12;-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 6,0,0,2);");
        return groupBox;
    }

    private VBox makeColorVBox(Color color) {
        Rectangle rect = new Rectangle(100, 100);
        rect.setFill(color);
        rect.setStroke(Color.GRAY);
        rect.setArcWidth(15);
        rect.setArcHeight(15);
        rect.setEffect(new DropShadow(5, Color.gray(0.5, 0.3)));

        Label hexLabel = new Label(toHexCode(color));
        hexLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 8 0 0 0;");

        VBox vbox = new VBox(rect, hexLabel);
        vbox.setAlignment(Pos.CENTER);
        vbox.setSpacing(8);
        return vbox;
    }

    private Rectangle makeColorRect(Color color) {
        Rectangle rect = new Rectangle(30, 30);
        rect.setFill(color);
        rect.setStroke(Color.GRAY);
        rect.setArcWidth(8);
        rect.setArcHeight(8);
        return rect;
    }

    private String toHexCode(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private float[] rgbToHsb(Color color) {
        return java.awt.Color.RGBtoHSB((int)(color.getRed() * 255), (int)(color.getGreen() * 255), (int)(color.getBlue() * 255), null);
    }

    private Color hsbToColor(float h, float s, float b) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, b);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        return Color.rgb(r, g, bl);
    }

    private Color getComplementaryColor(Color base) {
        float[] hsb = rgbToHsb(base);
        float newHue = (hsb[0] + 0.5f) % 1f;
        return hsbToColor(newHue, hsb[1], hsb[2]);
    }

    private List<Color> getAnalogousColors(Color base) {
        float[] hsb = rgbToHsb(base);
        float shift = 30f / 360f;
        Color c1 = hsbToColor((hsb[0] + shift) % 1f, hsb[1], hsb[2]);
        Color c2 = hsbToColor((hsb[0] - shift + 1f) % 1f, hsb[1], hsb[2]);
        return Arrays.asList(c1, c2);
    }

    private List<Color> getTriadicColors(Color base) {
        float[] hsb = rgbToHsb(base);
        Color c1 = hsbToColor((hsb[0] + 1f / 3f) % 1f, hsb[1], hsb[2]);
        Color c2 = hsbToColor((hsb[0] + 2f / 3f) % 1f, hsb[1], hsb[2]);
        return Arrays.asList(c1, c2);
    }

    private double getLuminance(Color color) {
        double r = adjust(color.getRed());
        double g = adjust(color.getGreen());
        double b = adjust(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private double adjust(double c) {
        return (c <= 0.03928) ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private double getContrastRatio(Color c1, Color c2) {
        double l1 = getLuminance(c1);
        double l2 = getLuminance(c2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private String getWCAGLevel(double ratio) {
        if (ratio >= 7) return "AAA";
        else if (ratio >= 4.5) return "AA";
        else if (ratio >= 3) return "AA (متن بزرگ)";
        else return "نامناسب";
    }

    private void loadFavorites() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(FAVORITES_FILE));
            for (String line : lines) {
                if (line.matches("#[0-9A-Fa-f]{6}")) {
                    String hex = line.toUpperCase();
                    if (favoritesSet.add(hex)) {
                        Color color = Color.web(hex);
                        Rectangle rect = makeColorRect(color);

                        rect.setOnMouseClicked(ev -> {
                            if (ev.getClickCount() == 2) {
                                FadeTransition fadeOut = new FadeTransition(Duration.millis(400), rect);
                                fadeOut.setFromValue(1.0);
                                fadeOut.setToValue(0.0);
                                fadeOut.setOnFinished(fadeEvent -> {
                                    favoritesBox.getChildren().remove(rect);
                                    favoritesSet.remove(hex);
                                    saveFavorites();
                                    showToast(this.primaryStage, "رنگ " + hex + " از مورد علاقه حذف شد!");
                                });
                                fadeOut.play();
                            } else {
                                colorInput.setText(hex);
                            }
                        });

                        favoritesBox.getChildren().add(rect);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void saveFavorites() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FAVORITES_FILE))) {
            for (String hex : favoritesSet) {
                writer.write(hex);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}