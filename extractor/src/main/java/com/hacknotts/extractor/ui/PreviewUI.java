package com.hacknotts.extractor.ui;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.model.FrameSlice;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PreviewUI extends Application {
    private static List<SpriteSheetProcessingResult> sharedResults = new ArrayList<>();

    private final ImageView imageView = new ImageView();
    private final Canvas overlay = new Canvas();
    private final Label status = new Label();

    public static void setResults(List<SpriteSheetProcessingResult> results, ExtractorConfig config) {
        sharedResults = List.copyOf(results);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("SpriteSheet AI Extractor Preview");
        BorderPane root = new BorderPane();
        ListView<SpriteSheetProcessingResult> listView = new ListView<>();
        listView.getItems().addAll(sharedResults);
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SpriteSheetProcessingResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getSource().getFileName().toString() + " (" + item.getDecision() + ")");
                }
            }
        });

        StackPane stack = new StackPane(imageView, overlay);
        stack.setPadding(new Insets(8));
        root.setCenter(stack);
        root.setLeft(listView);
        ToolBar toolBar = new ToolBar(status);
        root.setBottom(toolBar);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                render(selected);
            }
        });
        if (!sharedResults.isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.show();
    }

    private void render(SpriteSheetProcessingResult result) {
        try {
            BufferedImage image = ImageIO.read(result.getSource().toFile());
            if (image == null) {
                status.setText("Unable to load image: " + result.getSource());
                return;
            }
            Image fxImage = SwingFXUtils.toFXImage(image, null);
            imageView.setImage(fxImage);
            overlay.setWidth(fxImage.getWidth());
            overlay.setHeight(fxImage.getHeight());
            drawOverlay(result);
            status.setText(result.getSource().toString() + " - " + result.getDecision() + " - " + result.getStats());
        } catch (IOException ex) {
            status.setText("Failed to render " + result.getSource() + ": " + ex.getMessage());
        }
    }

    private void drawOverlay(SpriteSheetProcessingResult result) {
        GraphicsContext gc = overlay.getGraphicsContext2D();
        gc.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
        gc.setLineWidth(2.0);
        Color[] palette = new Color[]{Color.LIME, Color.ORANGE, Color.CYAN, Color.MAGENTA, Color.RED, Color.YELLOW};
        int i = 0;
        for (FrameSlice slice : result.getFrames()) {
            Color color = palette[i % palette.length];
            gc.setStroke(color);
            gc.strokeRect(slice.getX(), slice.getY(), slice.getWidth(), slice.getHeight());
            gc.setFill(color.deriveColor(0, 1, 1, 0.35));
            gc.fillOval(slice.getX() + slice.getPivotX() * slice.getWidth() - 4,
                    slice.getY() + slice.getPivotY() * slice.getHeight() - 4,
                    8,
                    8);
            i++;
        }
    }
}
