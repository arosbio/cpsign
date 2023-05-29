package com.arosbio.depict;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

public class EvaluateBloomSettings extends BaseTestClass {

    static IAtomContainer mol, mol2;
    static Map<Integer,Double> atomContributions = new HashMap<>(),atomContributions2 = new HashMap<>();
    static File outputDir;

    @BeforeClass
    public static void setupResources() throws InvalidSmilesException {

        SmilesParser sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
		mol = sp.parseSmiles("[C-]CC(C(=O)NC1=CC=C(C=C1)C(F)(F)F)N(C)CC(=O)N2CCN(CC2)CC3=CC=CC=C3");
        // generate random contributions
        Random rng = new Random(56782914l);
        for (int i = 0; i < mol.getAtomCount(); i++){
            atomContributions.put(i, rng.nextDouble()*2 - 1); // random uniform in the range [-1,1]
        }
        mol2 = sp.parseSmiles("COc(c1)cccc1C#N");
        // generate random contributions
        for (int i = 0; i < mol2.getAtomCount(); i++){
            atomContributions2.put(i, rng.nextDouble()*2 - 1); // random uniform in the range [-1,1]
        }

        outputDir = new File(TEST_OUTPUT_DIR,"settings");
        // create test output directory if not exists
        try{
            Files.createDirectories(outputDir.toPath());
        } catch (IOException e){
            Assert.fail("failed setting up test directory for images");
        }

    }

    @Test
    public void renderDifferentImageSize() throws Exception {
        for (int size : new int[]{100, 200, 400, 1000, 2000}){
            for (int raster : new int[]{1, 2, 3, 5}){
                doDepict(mol, atomContributions, size, size, raster, 1);
            }
        }

        for (int size : new int[]{100, 200, 400, 1000, 2000}){
            for (int raster : new int[]{1, 2, 3, 5}){
                doDepict(mol2, atomContributions2, size, size, raster, 2);
            }
        }
    }
    
    private static void doDepict(IAtomContainer testMol, Map<Integer,Double> mapping, int w, int h, int rasterSize, int index) throws IOException {
        MoleculeDepictor depictor = new MoleculeDepictor.Builder().w(w).h(h).rasterSize(rasterSize).build();

        BufferedImage img = depictor.depict(testMol, mapping);
        ImageIO.write(img, "png", new File(outputDir, String.format(Locale.ENGLISH, "%dx%d_raster=%d_index=%d.png", w,h,rasterSize,index)));
    }
    
}
