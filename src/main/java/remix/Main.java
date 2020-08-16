package remix;

import processing.core.PApplet;

import javax.imageio.ImageIO;

public class Main {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        ImageIO.setUseCache(false);
        System.gc();
        if (args == null || args.length == 0) {
            PApplet.main("remix.Virus");
        } else {
            PApplet.main(PApplet.concat(
                    new String[] { "remix.Virus" }, args));
        }
    }
}
