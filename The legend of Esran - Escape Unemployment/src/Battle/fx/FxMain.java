package Battle.fx;

import Battle.core.*;
import Battle.domain.*;
import Battle.util.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class FxMain extends Application {

    private Fighter hero;
    private Fighter druid;
    private BattleFxController ctrl;

    private Label lblMomentum;
    private Label lblYou, lblFoe;
    private ProgressBar barYou, barFoe;
    private Button[] moveBtns = new Button[4];
    private Button btnReset;
    private TextArea logArea;

    @Override
    public void start(Stage stage) {
        // Create fighters (same values you used in console App)
        hero  = new Fighter("King's Guardian", Affinity.STONE,  new Stats(120, 18, 18, 14));
        druid = new Fighter("Wilder Druid",   Affinity.VERDANT, new Stats(105, 20, 14, 16));
        ctrl  = new BattleFxController(hero, druid);

        // UI
        lblMomentum = new Label("[MOMENTUM: +0]");
        lblYou = new Label();
        lblFoe = new Label();

        barYou = new ProgressBar(1.0);
        barFoe = new ProgressBar(1.0);
        barYou.setPrefWidth(280);
        barFoe.setPrefWidth(280);

        HBox youBox = row("You", lblYou, barYou);
        HBox foeBox = row("Foe", lblFoe, barFoe);

        HBox moves = new HBox(10);
        moves.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            moveBtns[i] = new Button(BaseMoves.MOVES[i].name);
            moveBtns[i].setPrefWidth(150);
            moveBtns[i].setOnAction(e -> {
                if (ctrl.isOver()) return;
                String msg = ctrl.playerTurn(idx);  // resolves both actions + end-of-round
                appendLog(msg);
                refresh();
            });
        }
        moves.getChildren().addAll(moveBtns);

        btnReset = new Button("Reset Battle");
        btnReset.setOnAction(e -> resetBattle());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);

        VBox root = new VBox(10,
                lblMomentum,
                youBox,
                foeBox,
                new Separator(),
                new Label("Choose Technique:"),
                moves,
                new Separator(),
                new Label("Battle Log:"),
                logArea,
                btnReset
        );
        root.setPadding(new Insets(12));

        stage.setTitle("Druid Duel (JavaFX)");
        stage.setScene(new Scene(root, 700, 460));
        stage.show();

        appendLog("Battle begins...");
        refresh();
    }

    private HBox row(String title, Label info, ProgressBar bar) {
        Label l = new Label(title + ":");
        HBox box = new HBox(10, l, info, bar);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void refresh() {
        lblYou.setText(hero.name + "  " + hero.hp + "/" + hero.base.hp + "  [" + hero.status + "]");
        lblFoe.setText(druid.name + "  " + druid.hp + "/" + druid.base.hp + "  [" + druid.status + "]");
        barYou.setProgress(Math.max(0, (double) hero.hp / hero.base.hp));
        barFoe.setProgress(Math.max(0, (double) druid.hp / druid.base.hp));

        String signed = (ctrl.getMomentum() > 0 ? "+" : "") + ctrl.getMomentum();
        lblMomentum.setText("[MOMENTUM: " + signed + "]");

        // Update buttons for cooldowns / game over
        for (int i = 0; i < 4; i++) {
            Technique t = BaseMoves.MOVES[i];
            int cd = hero.cd.getOrDefault(t, 0);
            boolean disabled = cd > 0 || ctrl.isOver();
            moveBtns[i].setDisable(disabled);
            moveBtns[i].setText(t.name + (cd > 0 ? " (CD " + cd + ")" : ""));
        }

        if (ctrl.isOver()) {
            String winner = ctrl.getWinnerName();
            lblMomentum.setText(lblMomentum.getText() + "  |  Winner: " + winner);
        }
    }

    private void resetBattle() {
        hero  = new Fighter("King's Guardian", Affinity.STONE,  new Stats(120, 18, 18, 14));
        druid = new Fighter("Wilder Druid",   Affinity.VERDANT, new Stats(105, 20, 14, 16));
        ctrl  = new BattleFxController(hero, druid);
        logArea.clear();
        appendLog("Battle begins...");
        refresh();
    }

    private void appendLog(String text) {
        if (text == null || text.isEmpty()) return;
        if (logArea.getText().isEmpty()) logArea.setText(text);
        else logArea.appendText("\n" + text);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
