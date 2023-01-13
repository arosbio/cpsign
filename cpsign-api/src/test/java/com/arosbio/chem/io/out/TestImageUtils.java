package com.arosbio.chem.io.out;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.chem.io.out.image.FontFactory;
import com.arosbio.chem.io.out.image.ImageUtils;
import com.arosbio.chem.io.out.image.layout.Position.Vertical;
import com.arosbio.io.IOSettings;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestImageUtils {

    static List<AttributedString> lines;
    static String imageOutputFolder = null;

    @BeforeClass
    public static void setup() throws IOException{
        // Create a directory 
        File dir = new File(new File("").getAbsoluteFile(), "testoutput");
        imageOutputFolder = dir.getPath();
        Files.createDirectories(dir.toPath());

        // Add some lines
        lines = new ArrayList<>();
        lines.add(new AttributedString("Here is some text"));
        lines.add(new AttributedString("With a forced newline"));
        lines.get(0).addAttribute(TextAttribute.FOREGROUND, Color.RED, 3,7);
    }
    
    @Test
    public void testDrawIntoExistingImage() throws Exception {
        // AttributedString txt = new AttributedString("Here is some text");
        

        // for (AttributedString s : lines){
        //     System.err.println("init: " +s.getIterator().getAllAttributeKeys());
        // }
        // lines.get(0).addAttr ibute(TextAttribute.FOREGROUND, Color.BLACK);

        // doDraw(10, 10, "10x10.png");
        // doDraw(100, 100, "100x100.png");
        doDraw(100, 100, "200x200.png");
    }

    private static void doDraw(int w, int h, String fileName) throws IOException{
        BufferedImage res = new BufferedImage(w, h, IOSettings.BUFFERED_IMAGE_TYPE);

        ImageUtils.drawText(res, lines, new Rectangle(w,h), Vertical.LEFT_ADJUSTED,FontFactory.plain()); //, FontFactory.plain(), Color.BLACK);
        
        ImageIO.write(res, "png", new File(imageOutputFolder, fileName));
    }

    @Test
    public void calcDimAndDraw() throws Exception {
        // ImageUtils.calculateRequiredSpace(null, null, 0)
        Dimension2D dim = ImageUtils.calculateRequiredSpace(200, FontFactory.plain(), lines);
        // System.err.println("Calculated dimensions: " + dim);
        // Create a larger image - non-centered to make it more difficult
        BufferedImage img = new BufferedImage((int) Math.ceil(dim.getWidth())+100,(int)dim.getHeight() + 150, IOSettings.BUFFERED_IMAGE_TYPE);

        int drawStartX = 30, drawStartY = 50;

        Graphics2D g = img.createGraphics();
        // Set a white background color
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        // Draw a boarder around the text-area
        g.setColor(Color.ORANGE);
        g.drawRect(drawStartX, drawStartY, (int)dim.getWidth()-1, (int)dim.getHeight()-1);

        Rectangle2D area = new Rectangle2D.Float(drawStartX, drawStartY, (float)dim.getWidth(), (float)dim.getHeight());

        ImageUtils.drawText(g, lines, area, Vertical.LEFT_ADJUSTED, FontFactory.plain());

        ImageIO.write(img, "png", new File(imageOutputFolder, "calcDimAndDraw.png"));
    }
}
