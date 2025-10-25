package Battle;

import Battle.core.Battle;
import Battle.domain.*;

import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        Fighter hero  = new Fighter("King's Guardian", Affinity.STONE,  new Stats(120,18,18,14));
        Fighter druid = new Fighter("Wilder Druid",   Affinity.VERDANT, new Stats(105,20,14,16));
        new Battle(hero, druid).run(new Scanner(System.in));
    }
}
