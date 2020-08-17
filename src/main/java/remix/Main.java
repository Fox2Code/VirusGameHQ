package remix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class Main {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        ImageIO.setUseCache(false);
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().dispose();
        System.gc();
        Virus.main(args);
    }
}
