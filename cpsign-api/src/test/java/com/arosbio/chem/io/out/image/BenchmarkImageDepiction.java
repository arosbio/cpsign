package com.arosbio.chem.io.out.image;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.cheminf.ChemCPClassifier;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.commons.Stopwatch;
import com.arosbio.io.UriUtils;
import com.arosbio.tests.TestResources.CSVCmpdData;
import com.arosbio.tests.TestResources.Reg;
import com.arosbio.tests.suites.PerformanceTest;
import com.arosbio.testutils.TestChemDataLoader.PreTrainedModels;

@Category(PerformanceTest.class)
public class BenchmarkImageDepiction {

    public static List<Triple<IAtomContainer,SignificantSignature,Map<String,Double>>> data = new ArrayList<>();
    static Set<String> labels;

    public static File legacyOut, newOut;

    @BeforeClass
    public static void initMolecules() throws InvalidKeyException, IOException, URISyntaxException, NullPointerException, IllegalStateException, CDKException{
        ChemCPClassifier model = (ChemCPClassifier) ModelSerializer.loadChemPredictor(PreTrainedModels.ACP_CLF_LIBLINEAR.toURI(),null);
        labels = model.getLabelsSet();
        CSVCmpdData solu100 = Reg.getSolubility_100();

        Iterator<IAtomContainer> molIterator = new CSVFile(solu100.uri()).setDelimiter(solu100.delim()).getIterator();
        
        while (molIterator.hasNext()){
            IAtomContainer mol = molIterator.next();
            mol.setTitle(CPSignMolProperties.getSMILES(mol)); // Set an artificial title - to that both implementation works and none of them have to compute it
            Map<String,Double> pvals = model.predict(mol);
            SignificantSignature ss = model.predictSignificantSignature(mol);
            data.add(Triple.of(mol,ss,pvals));
        }
        System.err.printf("Predicted %d molecules and their atom-contributions etc%n",data.size());

        File testOutBaseDir = new File(new File("").getAbsoluteFile(), "testoutput");
        legacyOut = new File(testOutBaseDir,"legacy/grad_leg");
        newOut = new File(testOutBaseDir,"new/grad_new");
        UriUtils.createParentOfFile(legacyOut);
        UriUtils.createParentOfFile(newOut);

    }

    int figureHeight = 500, figureWidth = 400;

    // public GradientFigureBuilder getBuilder(Triple<IAtomContainer,SignificantSignature,Map<String,Double>> currentData){
    //     MoleculeGradientDepictor depictor = new MoleculeGradientDepictor(GradientFactory.getBlueRedGradient());
    //     return new GradientFigureBuilder(depictor)
    //         .background(Color.WHITE)
    //         .figureHeight(figureHeight)
    //         .figureWidth(figureWidth)
    //         .addFieldOverImg(new TitleField(currentData.getLeft().getTitle()))
    //         .addFieldUnderImg(new PValuesField(currentData.getRight()))
    //         .addFieldUnderImg(new ColorGradientField(depictor));
    // }

    public RenderInfo getInfo(Triple<IAtomContainer,SignificantSignature,Map<String,Double>> currentData){
        return new RenderInfo.Builder(currentData.getLeft(), currentData.getMiddle())
            .pValues(currentData.getRight())
            .build();
    }


    /*
    ==== RUN 1 (0.81)
    legacy: 21.751 s
    new: 17.668 s 
    ==== RUN 2 (0.78)
    legacy: 21.919 s
    new: 17.191 s
    ==== RUN 3 (0.77)
    legacy: 21.165 s
    new: 16.379 s

    // RESIZING THE FONTS + default padding
    legacy: 21.513 s (0.78)
    new: 16.9 s
     */
    @Test
    public void runTest() throws IOException {
        int numToRun = data.size(); // 10; // 
        
        
        // Do the legacy output
        Stopwatch wLegacy = new Stopwatch().start();
        // for (int i=0; i<numToRun; i++){
        //     Triple<IAtomContainer,SignificantSignature,Map<String,Double>> d = data.get(i);
        //     GradientFigureBuilder builder = getBuilder(d);
        //     builder.build(d.getLeft(), d.getMiddle().getAtomContributions()).saveToFile(new File(legacyOut.toString()+"_"+i+".png"));
        // }
        System.err.println("legacy: " + wLegacy.stop());


        // Do the new output
        Stopwatch wNew = new Stopwatch().start();
        // Create the rendering template once
        AtomContributionRenderer template = new AtomContributionRenderer.Builder()
            .colorScheme(GradientFactory.getBlueRedGradient())
            .width(figureWidth)
            .height(figureHeight)
            .background(Color.WHITE)
            .addFieldOverMol(new com.arosbio.chem.io.out.image.fields.TitleField.Builder().build())
            .addFieldUnderMol(new com.arosbio.chem.io.out.image.fields.PValuesField.Builder(labels).build()) // layout(new CustomLayout.Builder().padding(new Padding(10)).build()).build())
            .addFieldUnderMol(new com.arosbio.chem.io.out.image.fields.ColorGradientField.Builder(GradientFactory.getBlueRedGradient()).build()) //layout(new CustomLayout.Builder().padding(new Padding(10)).build()).build())
            .build();

        for (int i=0; i<numToRun; i++){
            Triple<IAtomContainer,SignificantSignature,Map<String,Double>> d = data.get(i);
            template.render(getInfo(d)).saveToFile(new File(newOut.toString()+"_"+i+".png"));
        }
        System.err.println("new: " + wNew.stop());

        // GradientFigureBuilder figBuilder = new GradientFigureBuilder(gradientDepictor);
		// 	figBuilder.setFigureHeight(gradientParams.imageHeight);
		// 	figBuilder.setFigureWidth(gradientParams.imageWidth);
		// 	if (pvals!=null)
		// 		figBuilder.addFieldUnderImg(new PValuesField(pvals));
		// 	if (probs != null) 
		// 		figBuilder.addFieldUnderImg(new ProbabilityField(probs));
		// 	if (gradientParams.depictColorScheme)
		// 		figBuilder.addFieldUnderImg(new ColorGradientField(gradientDepictor));
		// 	figBuilder.build(mol, grad).saveToFile(imgFile);

    }
    
}
