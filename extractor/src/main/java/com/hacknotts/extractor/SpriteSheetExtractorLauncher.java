package com.hacknotts.extractor;

import com.hacknotts.extractor.config.ConfigParser;
import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.export.GodotExporter;
import com.hacknotts.extractor.export.JsonExporter;
import com.hacknotts.extractor.export.UnityExporter;
import com.hacknotts.extractor.ml.CoreVsFxClassifier;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;
import com.hacknotts.extractor.processing.SpriteSheetProcessor;
import com.hacknotts.extractor.ui.PreviewUI;
import javafx.application.Application;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class SpriteSheetExtractorLauncher {
    public static void main(String[] args) throws Exception {
        ExtractorConfig config = ConfigParser.parse(args);
        CoreVsFxClassifier classifier = CoreVsFxClassifier.loadOrCreate(config.getOutputDir().resolve("model.json"));
        SpriteSheetProcessor processor = new SpriteSheetProcessor(config, classifier);

        List<SpriteSheetProcessingResult> results = new ArrayList<>();
        if (config.getSingleFile() != null) {
            results.add(processor.process(config.getSingleFile()));
        } else {
            if (!Files.exists(config.getInputDir())) {
                Files.createDirectories(config.getInputDir());
            }
            try (var stream = Files.walk(config.getInputDir())) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".png"))
                        .forEach(path -> {
                            try {
                                results.add(processor.process(path));
                            } catch (Exception ex) {
                                throw new IllegalStateException("Failed to process " + path, ex);
                            }
                        });
            }
        }

        JsonExporter jsonExporter = new JsonExporter(config);
        UnityExporter unityExporter = new UnityExporter(config);
        GodotExporter godotExporter = new GodotExporter(config);

        for (SpriteSheetProcessingResult result : results) {
            jsonExporter.export(result);
            unityExporter.export(result);
            godotExporter.export(result);
        }

        classifier.save();

        if (config.isVisualize() && !GraphicsEnvironment.isHeadless()) {
            PreviewUI.setResults(results, config);
            Application.launch(PreviewUI.class);
        }
    }
}
