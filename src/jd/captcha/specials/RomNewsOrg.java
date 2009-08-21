package jd.captcha.specials;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Captcha Recognition for Smiley-Captcha
 * 
 * @author JTB
 */
public class RomNewsOrg {

    private BufferedImage pic;
    private int[][] selected = new int[20001][2];
    private boolean smiley = false;
    private Color color;
    private final Color colorsmiley = new Color(50, 50, 50);
    private int max_x = 0;
    private int max_y = 0;
    private int min_x = 300;
    private int min_y = 100;
    private int[] result;

    public RomNewsOrg(File file, Color color) {
        try {
            this.pic = ImageIO.read(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int r, g, b;
        if (color.getRed() > 205)
            r = 205;
        else if (color.getRed() < 50)
            r = 50;
        else
            r = color.getRed();
        if (color.getGreen() > 205)
            g = 205;
        else if (color.getGreen() < 50)
            g = 50;
        else
            g = color.getGreen();
        if (color.getBlue() > 205)
            b = 205;
        else if (color.getBlue() < 50)
            b = 50;
        else
            b = color.getBlue();
        this.color = new Color(r, g, b);
        this.selected[0][0] = 1;
        int x = 3;
        int y = 3;
        result = new int[2];
        while (y + 3 < this.pic.getHeight()) {
            if (result[0] == 0 && colcom(new Color(this.pic.getRGB(x, y)), this.color) && colcom(new Color(this.pic.getRGB(x, y - 3)), this.color) && colcom(new Color(this.pic.getRGB(x, y + 3)), this.color) && colcom(new Color(this.pic.getRGB(x - 3, y)), this.color) && colcom(new Color(this.pic.getRGB(x + 3, y)), this.color)) {
                select(x, y);
                if (smiley == true) {
                    result[0] = (this.max_x - this.min_x) / 2 + this.min_x;
                    result[1] = (this.max_y - this.min_y) / 2 + this.min_y;
                } else {
                    max_x = 0;
                    max_y = 0;
                    min_x = 300;
                    min_y = 100;
                }
            }

            x++;
            if (x + 4 >= this.pic.getWidth()) {
                x = 0;
                y++;
            }
        }
    }

    public int[] getResult() {
        return result;
    }

    private void select(int x, int y) {
        boolean vorhanden = false;
        for (int i = 1; i < this.selected.length; i++) {
            if (this.selected[i][0] == x && this.selected[i][1] == y) vorhanden = true;
        }
        if (vorhanden == false) {
            this.selected[this.selected[0][0]][0] = x;
            this.selected[this.selected[0][0]][1] = y;
            this.selected[0][0]++;

            if (x > this.max_x)
                this.max_x = x;
            else {
                if (x < this.min_x) this.min_x = x;
            }
            if (y > this.max_y)
                this.max_y = y;
            else {
                if (y < this.min_y) this.min_y = y;
            }

            if (colcom(new Color(this.pic.getRGB(x + 1, y)), this.color))
                select(x + 1, y);
            else if (colcom(new Color(this.pic.getRGB(x + 1, y)), this.colorsmiley) && this.smiley == false) this.smiley = true;
            if (colcom(new Color(this.pic.getRGB(x, y + 1)), this.color))
                select(x, y + 1);
            else if (colcom(new Color(this.pic.getRGB(x, y + 1)), this.colorsmiley) && this.smiley == false) this.smiley = true;
            if (colcom(new Color(this.pic.getRGB(x - 1, y)), this.color))
                select(x - 1, y);
            else if (colcom(new Color(this.pic.getRGB(x - 1, y)), this.colorsmiley) && this.smiley == false) this.smiley = true;
            if (colcom(new Color(this.pic.getRGB(x, y - 1)), this.color))
                select(x, y - 1);
            else if (colcom(new Color(this.pic.getRGB(x, y - 1)), this.colorsmiley) && this.smiley == false) this.smiley = true;
        }
    }

    /**
     * colcom = color-compare
     */
    private static boolean colcom(Color color1, Color color2) {
        int unterschied = 50;
        if ((color1.getRed() + unterschied > color2.getRed() && color1.getRed() - unterschied < color2.getRed()) && (color1.getBlue() + unterschied > color2.getBlue() && color1.getBlue() - unterschied < color2.getBlue()) && (color1.getGreen() + unterschied > color2.getGreen() && color1.getGreen() - unterschied < color2.getGreen()))
            return true;
        else
            return false;
    }

}
