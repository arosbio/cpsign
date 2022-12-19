/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.depict;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import com.google.common.base.Stopwatch;

public class Benchmark extends BaseTestClass {

    private static List<IAtomContainer> testMols;
    private static List<Map<Integer,Double>> colors;

    @BeforeClass
    public static void readAndConfigMols()throws Exception {
        Timer t = new Timer();
        testMols = new ArrayList<>();
        colors = new ArrayList<>();
        SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        StructureDiagramGenerator gen2D = new StructureDiagramGenerator();
        try (
            InputStreamReader reader = new InputStreamReader(Benchmark.class.getResourceAsStream("/smiles_cmpds.csv"));
            BufferedReader buff = new BufferedReader(reader);
            ){
                String line = null;
                while ( (line = buff.readLine()) != null){
                    IAtomContainer ac = parser.parseSmiles(line.split("\\s")[0]);
                    // Config properly
                    CDKConfigureAtomContainer.configMolecule(ac);
                    // Calc 2D
                    gen2D.generateCoordinates(ac);

                    testMols.add(ac);

                    // Generate a random color map
                    Map<Integer,Double> m = new HashMap<>();
                    for (IAtom a : ac.atoms()){
                        m.put(a.getIndex(), Math.random()*2-1);
                    }
                    colors.add(m);
                }
        }
        System.err.println("Loaded and configured " + testMols.size() + " molecules");
        System.err.println("time: " + t);
    }

    
    static int imgSize = 800, padd=10;
    @Test
    public void benchStepSize() throws Exception {
        
        int fullH = (imgSize+padd)*testMols.size() + padd;
        int fullW = padd * 4 + imgSize*3;
        BufferedImage resultImg = new BufferedImage(fullW,fullH,BufferedImage.TYPE_4BYTE_ABGR);
        BufferedImage resultImg2 = new BufferedImage(fullW,fullH,BufferedImage.TYPE_4BYTE_ABGR);

        // Init the different ones
        MoleculeDepictor vanilla = new MoleculeDepictor.Builder().h(imgSize).w(imgSize).build();
        MoleculeDepictor second = new MoleculeDepictor.Builder().h(imgSize).w(imgSize).rasterSize(2).build();
        MoleculeDepictor third = new MoleculeDepictor.Builder().h(imgSize).w(imgSize).rasterSize(3).build();

        Stopwatch w1 = Stopwatch.createStarted();
        // first vanilla
        doCol(resultImg, padd, padd, vanilla,"Vanilla 1x1", false);

        // second 2x2
        doCol(resultImg, padd*2 + imgSize, padd, second,"2x2", false);

        // third 3x3
        doCol(resultImg, padd*3+imgSize*2, padd, third, "3x3", false);
        w1.stop();
        System.err.printf("Old impl ran in %s %n", w1);

        ImageIO.write(resultImg, "PNG", new File(TEST_OUTPUT_DIR,"benchmark_output.png"));


        Stopwatch w2 = Stopwatch.createStarted();
        // first vanilla
        doCol(resultImg2, padd, padd, vanilla,"Vanilla 1x1", true);

        // second 2x2
        doCol(resultImg2, padd*2 + imgSize, padd, second,"2x2", true);

        // third 3x3
        doCol(resultImg2, padd*3+imgSize*2, padd, third, "3x3", true);
        w2.stop();
        System.err.printf("New impl ran in %s %n", w2);

        ImageIO.write(resultImg2, "PNG", new File(TEST_OUTPUT_DIR,"benchmark_output_2.png"));
        
        
    }

    private void doCol(BufferedImage resultImg, int xOff, int yPadd, MoleculeDepictor dep, String id, boolean drawDirectly){
        Timer t = new Timer();
        Graphics2D g2 = resultImg.createGraphics();

        if (drawDirectly){
            // New implementation, write using the same graphics2d object, no copy of the images
            
            for (int i=0; i<testMols.size(); i++){
                Rectangle2D drawArea = new Rectangle2D.Double(xOff,yPadd + i*(dep.getImageHeight()+yPadd),
                    imgSize,imgSize);

                dep.depict(testMols.get(i), colors.get(i), resultImg, g2, drawArea);
                // g2.drawImage(im, null, xOff, yPadd + i*(dep.getImageHeight()+yPadd));
            }
        } else {
            for (int i=0; i<testMols.size(); i++){
                BufferedImage im = dep.depict(testMols.get(i), colors.get(i));
                g2.drawImage(im, null, xOff, yPadd + i*(dep.getImageHeight()+yPadd));
            }
        }

        t.stop();
        System.err.printf("%s: %s%n",id,t);
    }


    
}
