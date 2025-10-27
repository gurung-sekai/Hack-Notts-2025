# SpriteSheet AI Extractor

This Gradle project provides a command-line and JavaFX GUI tool that analyses sprite sheets, segments frames using pure Java
image processing, clusters connected components via a DBSCAN-inspired pass, and emits ready-to-use exports for Unity and Godot
alongside structured JSON metadata.

## Requirements

* Java 17+
* JavaFX SDK placed in one of the following locations relative to the repository root: `javafx-sdk-21.0.2/lib`,
  `javafx-sdk-25.0.1/lib`, or `javafx-sdk/lib`.
* No external network access is required — the project ships with a pure-Java implementation of the required computer vision
  primitives.

## Running

```
./gradlew :extractor:run --args="inDir=../The\ legend\ of\ Esran\ -\ Escape\ Unemployment/src/resources/bosses outDir=./out visualize=false"
```

Useful arguments:

* `file=<path>` – process a single PNG.
* `visualize=true` – launch the JavaFX preview window after processing.
* `alphaThreshold`, `padding`, `minArea`, `eps`, `minSamples`, `valleyWindow`, `wholeCoverage`, `twoGapIouMax`, `pixelsPerUnit`
  – tune segmentation and export parameters.
* `decision.<pattern>=WHOLE|TWO|MANY` – force a decision for matching files (wildcards `*` supported).
* `override.<pattern>=ClipName` – override animation clip naming heuristics.
* `--name=<Character>` – override the inferred character name.

## Outputs

For every processed sheet the tool writes:

* Cropped PNG frames under `out/<character>/<animation>/frame_XXX.png`.
* `metadata.json` describing each frame, pivots, durations, and the decision module outcome.
* Unity `.meta`, `.spriteatlas`, and `.anim.json` files aligned with the exported frames.
* Godot `.tres` SpriteFrames resources referencing the generated PNGs.

## Tests

The acceptance suite verifies the specification for well-known tricky sheets:

``` 
./gradlew :extractor:acceptanceTest
```

A dedicated `verifySpriteSheets` task remains available inside the original game project for regression coverage.
