package com.hacknotts.extractor;

import com.hacknotts.extractor.config.ExtractorConfig;
import com.hacknotts.extractor.export.GodotExporter;
import com.hacknotts.extractor.export.JsonExporter;
import com.hacknotts.extractor.export.UnityExporter;
import com.hacknotts.extractor.ml.CoreVsFxClassifier;
import com.hacknotts.extractor.model.ProcessingDecision;
import com.hacknotts.extractor.model.SpriteSheetProcessingResult;
import com.hacknotts.extractor.processing.SpriteSheetProcessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ExtractorAcceptanceTest {
    private final Path outputDir;

    public ExtractorAcceptanceTest() throws Exception {
        this.outputDir = Files.createTempDirectory("extractor-tests");
    }

    private SpriteSheetProcessingResult process(String relativePath) throws Exception {
        ExtractorConfig config = ExtractorConfig.builder()
                .outputDir(outputDir)
                .visualize(false)
                .build();
        CoreVsFxClassifier classifier = CoreVsFxClassifier.loadOrCreate(outputDir.resolve("model.json"));
        SpriteSheetProcessor processor = new SpriteSheetProcessor(config, classifier);
        Path base = Path.of("..", "The legend of Esran - Escape Unemployment", "src", "resources", "bosses");
        Path path = base.resolve(relativePath);
        if (!path.toFile().exists()) {
            path = base.resolve("attacks").resolve(relativePath);
        }
        SpriteSheetProcessingResult result = processor.process(path);
        JsonExporter jsonExporter = new JsonExporter(config);
        UnityExporter unityExporter = new UnityExporter(config);
        GodotExporter godotExporter = new GodotExporter(config);
        jsonExporter.export(result);
        unityExporter.export(result);
        godotExporter.export(result);
        classifier.save();
        return result;
    }

    @Test
    void welchAttackShouldBeWhole() throws Exception {
        SpriteSheetProcessingResult result = process("attacks/theWelchAttack3.png");
        assertEquals(ProcessingDecision.WHOLE, result.getDecision());
        assertEquals(1, result.getFrames().size());
    }

    @Test
    void purpleEmpressSplitIntoTwo() throws Exception {
        SpriteSheetProcessingResult result = process("attacks/purpleEmpressAttack3.png");
        assertEquals(ProcessingDecision.TWO, result.getDecision());
        assertEquals(2, result.getFrames().size());
    }

    @Test
    void multipleAttackSheetsAreDetected() throws Exception {
        SpriteSheetProcessingResult result = process("attacks/goldMechAttack1.png");
        assertEquals(ProcessingDecision.MANY, result.getDecision());
        assertTrue(result.getFrames().size() > 2);
        assertFalse(result.getClips().isEmpty());
        assertTrue(result.getClips().get(0).getName().toLowerCase().contains("attack"));
    }
}
