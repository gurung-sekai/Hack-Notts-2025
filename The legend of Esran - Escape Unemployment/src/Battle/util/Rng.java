package Battle.util;
import java.util.Random;
public class Rng {
    private static final Random r = new Random();
    public static boolean roll(int percent) {return r.nextInt(100)<percent;}
    public static double d01(){return r.nextDouble();}
}