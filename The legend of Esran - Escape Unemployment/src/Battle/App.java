package Battle;

import java.util.Scanner;

public class App {
    public static void main ( String[] args) {
        Fighter hero = new Fighter("King's Gaurdian", Affinity.STONE, new Stats(120, 18, 18, 14));
        Fighter druid = new Fighter("Wilder Druid", Affinity.VERDANT, new Stats(105, 20, 14, 16));

        new Battle(hero, druid).run(new Scanner(System.in));
    }
}