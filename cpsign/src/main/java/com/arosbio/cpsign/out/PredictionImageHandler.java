/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.out;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.io.out.GradientFigureBuilder;
import com.arosbio.chem.io.out.SignificantSignatureFigureBuilder;
import com.arosbio.chem.io.out.depictors.MoleculeGradientDepictor;
import com.arosbio.chem.io.out.depictors.MoleculeSignificantSignatureDepictor;
import com.arosbio.chem.io.out.fields.ColorGradientField;
import com.arosbio.chem.io.out.fields.HighlightExplanationField;
import com.arosbio.chem.io.out.fields.PValuesField;
import com.arosbio.chem.io.out.fields.ProbabilityField;
import com.arosbio.color.gradient.ColorGradient;
import com.arosbio.color.gradient.GradientFactory;
import com.arosbio.cpsign.app.params.CLIParameters;
import com.arosbio.cpsign.app.params.converters.ColorConverter;
import com.arosbio.cpsign.app.params.converters.ColorGradientConverter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class PredictionImageHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(PredictionImageHandler.class);

	public static class GradientImageOpts {
		
		@Option(names = {"-gi","--gradient-images"}, 
				description ="Create a Gradient image for each predicted molecule")
		public boolean createImgs;
		
		@Option(names = {"-gi:o", "--gi:output"}, 
				description = "Path to where generated images should be saved, can either be "+
				"a path to a specific folder or a full path including a file name (only .png file ending supported)."+
				" Every image will be named '[name]-[count].png' or '[name]-[$cdk:title].png' where name is either a default name or "+
				"the specified name to this parameter" +
				" (e.g. '.' - current folder using default file name, '/tmp/imgs/DefaultImageName.png' "+
				"- use '/tmp/imgs' as directory and use 'DefaultImageName' as file name)%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				paramLabel = ArgumentType.FILE_PATH,
				defaultValue = "images/GradientDepiction.png")
		public File imageFile = new File("images/GradientDepiction.png");
		
		@Option(names = {"-gi:cs", "--gi:color-scheme"}, 
				converter = ColorGradientConverter.class,
				description = "The specified color-scheme (case in-sensitive), options:%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) blue:red%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) red:blue%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) red:blue:red%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(4) cyan:magenta%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(5) rainbow%n"+
				"Appart from these pred-defined options you can specify either 2 or 3 colors, separated by a \":\" for a custom "+
				"gradient, e.g. forestgreen:rgb(15,16,100):#730707. The colors can be a mix of common color-names, RGB-values and hex-codes. "+
				"You may have to include quotation marks around this when using the # character for hex-codes.\n" +
				"For further customizations - contact Aros Bio%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				paramLabel = ArgumentType.ID_OR_TEXT,
				defaultValue = "1")
		public ColorGradient colorScheme = GradientFactory.getBlueRedGradient();
		
		@Option(names = {"--gi:legend"}, 
				description = "Add a color legend at the bottom of the image")
		public boolean depictColorScheme;
		
		@Option(names = "--gi:atom-numbers", 
				description = "Depict atom numbers. Accepts an optional argument of the color to depict the numbers with, default is blue if no extra argument is given.",
				fallbackValue = "BLUE",
				arity = "0..1",
				converter = ColorConverter.class,
				paramLabel = ArgumentType.COLOR)
		public Color atomNumberColor;
		
		@Option(names={"-gi:h","--gi:height"}, 
		description="The height of the generated images (in pixels)%n"+
		ParameterUtils.DEFAULT_VALUE_LINE,
		defaultValue=""+CLIParameters.DEFAULT_IMAGE_HEIGHT,
		paramLabel = ArgumentType.IMG_HEIGHT)
		public int imageHeight = CLIParameters.DEFAULT_IMAGE_HEIGHT;
		
		@Option(names={"-gi:w","--gi:width"}, 
		description="The width of the generated images (in pixels)%n"+
		ParameterUtils.DEFAULT_VALUE_LINE,
		defaultValue=""+CLIParameters.DEFAULT_IMAGE_WIDTH,
		paramLabel = ArgumentType.IMG_WIDTH)
		public int imageWidth = CLIParameters.DEFAULT_IMAGE_WIDTH;
	}

	public static class SignificantSignatureImageOpts {

		@Option(names = {"-si","--signature-images"}, 
				description ="Create a Significant Signature image for each predicted molecule")
		public boolean createImgs;
		
		@Option(names = {"-si:o", "--si:output"}, 
				description = "Path to where generated images should be saved, can either be "+
				"a path to a specific folder or a full path including a file name (only .png file ending supported)."+
				" Every image will be named '[name]-[count].png' or '[name]-[$cdk:title].png' where name is either a default name or "+
				"the specified name to this parameter" +
				" (e.g. '.' - current folder using default file name, '/tmp/imgs/DefaultImageName.png' "+
				"- use '/tmp/imgs/' as directory and use 'DefaultImageName' as file name)%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				defaultValue="images/SigificantSignatureDepiction.png",
				paramLabel = ArgumentType.FILE_PATH)
		public File imageFile = new File("images/SigificantSignatureDepiction.png");
		
		@Option(names = {"-si:c", "--si:color"}, 
				description = "The color that should be used for the highlighting of the significant signature%n"+
						ParameterUtils.DEFAULT_VALUE_LINE,
				defaultValue="BLUE",
				converter = ColorConverter.class,
				paramLabel = ArgumentType.COLOR)
		public Color highlightColor = Color.BLUE;
		
		@Option(names = {"--si:legend"}, 
				description = "Add a color legend at the bottom of the image")
		public boolean depictColorScheme;
		
		@Option(names = "--si:atom-numbers", 
				description = "Depict atom numbers. Accepts an optional argument of the color to depict the numbers with, default is blue if no extra argument is given.",
				converter = ColorConverter.class,
				fallbackValue = "BLUE",
				arity = "0..1",
				paramLabel = ArgumentType.COLOR
		)
		public Color atomNumberColor;
		
		@Option(names={"-si:h","--si:height"}, 
				description="The height of the generated images (in pixels)%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				defaultValue=""+CLIParameters.DEFAULT_IMAGE_HEIGHT,
				paramLabel = ArgumentType.IMG_HEIGHT)
		public int imageHeight = CLIParameters.DEFAULT_IMAGE_HEIGHT;
		
		@Option(names={"-si:w","--si:width"}, 
				description="The width of the generated images (in pixels)%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				defaultValue=""+CLIParameters.DEFAULT_IMAGE_WIDTH,
				paramLabel = ArgumentType.IMG_WIDTH)
		public int imageWidth = CLIParameters.DEFAULT_IMAGE_WIDTH;
	}

	private GradientImageOpts gradientParams;
	private SignificantSignatureImageOpts significantSignatureParams;

	private MoleculeGradientDepictor gradientDepictor;
	private ImageFileHandler gradientFileHandler;
	private MoleculeSignificantSignatureDepictor signatureDepictor;
	private ImageFileHandler signatureFileHandler;

	public PredictionImageHandler(GradientImageOpts gradientParams, SignificantSignatureImageOpts signatureParams) {
		this.gradientParams = gradientParams;
		this.significantSignatureParams = signatureParams;
		setupImageDepictors();
	}

	public boolean isUsed() {
		return gradientDepictor!=null || signatureDepictor != null;
	}

	public boolean isPrintingSignatureImgs() {
		return signatureDepictor != null;
	}

	public boolean isPrintingGradientImgs() {
		return gradientDepictor != null;
	}

	public void writeSignificantSignatureImage(IAtomContainer mol, 
			Set<Integer> ss, 
			Map<String,Double> pvals,
			Map<String,Double> probs,
			String label){
		// Get the file
		try{
			File imgFile = signatureFileHandler.getNextImageFile(mol);

			SignificantSignatureFigureBuilder figBuilder = new SignificantSignatureFigureBuilder(signatureDepictor);
			figBuilder.setFigureHeight(significantSignatureParams.imageHeight);
			figBuilder.setFigureWidth(significantSignatureParams.imageWidth);
			if (pvals != null) {
				figBuilder.addFieldUnderImg(new PValuesField(pvals));
			}
			if (probs != null) {
				figBuilder.addFieldUnderImg(new ProbabilityField(probs));
			}
			if (significantSignatureParams.depictColorScheme) {
				if (label != null && pvals != null)
					figBuilder.addFieldUnderImg(new HighlightExplanationField(
							(signatureDepictor).getHighlightColor(),
							label));
				else // Regression and CVAP
					figBuilder.addFieldUnderImg(new HighlightExplanationField(
							(signatureDepictor).getHighlightColor()));
			}
			figBuilder.build(mol, ss).saveToFile(imgFile);
		} catch(Exception e){
			LOGGER.debug("Failed depicting molecule",e);
		} 

	}

	public void writeGradientImage(IAtomContainer mol, 
			Map<Integer,Double> grad, 
			Map<String,Double> pvals,
			Map<String,Double> probs){
		// Get the file
		try{
			File imgFile = gradientFileHandler.getNextImageFile(mol);

			GradientFigureBuilder figBuilder = new GradientFigureBuilder(gradientDepictor);
			figBuilder.setFigureHeight(gradientParams.imageHeight);
			figBuilder.setFigureWidth(gradientParams.imageWidth);
			if (pvals!=null)
				figBuilder.addFieldUnderImg(new PValuesField(pvals));
			if (probs != null) 
				figBuilder.addFieldUnderImg(new ProbabilityField(probs));
			if (gradientParams.depictColorScheme)
				figBuilder.addFieldUnderImg(new ColorGradientField(gradientDepictor));
			figBuilder.build(mol, grad).saveToFile(imgFile);
		} catch(Exception e){
			LOGGER.debug("Failed depicting molecule",e);
		} 
	}

	private static class ImageFileHandler {
		private String imageBaseName = "image";
		private int count = 0;
		private File directory;

		public ImageFileHandler(File outputDir) throws IOException {
			if (outputDir == null || outputDir.equals(new File("")) || (outputDir.getParentFile()== null && outputDir.getName().equals("."))){
				LOGGER.debug("Image output directory is the current directory with base name '{}'", imageBaseName);
				directory = new File("").getAbsoluteFile().getParentFile();
				return;
			}


			if (outputDir.exists() && outputDir.isDirectory()){
				directory = outputDir;
				LOGGER.debug("Image output directory is '{}' with base name '{}'",directory,imageBaseName);
				return;
			}


			else if (outputDir.getName().contains(".")){
				LOGGER.debug("User has set a specific file name: " + outputDir.getName());

				imageBaseName = outputDir.getName().substring(0, outputDir.getName().lastIndexOf('.'));

				if (outputDir.getParentFile()!= null && !outputDir.getParentFile().exists()){
					try {
						FileUtils.forceMkdir(outputDir.getParentFile());
					} catch (IOException e) {
						LOGGER.debug("Could not create the parent directories for image-folder",e);
						throw e;
					}
				}

				directory = outputDir.getParentFile();

			} else {
				LOGGER.debug("User has not given any explicit file name");

				try{
					FileUtils.forceMkdir(outputDir);
					directory = new File(outputDir.getAbsolutePath()); // Need to update in case the folder did not exist before
				} catch(IOException e){
					LOGGER.debug("Could not create the image folder", e);
					throw e;
				}

			}

			LOGGER.debug("Image ouput directory is '{}' with base name '{}'",directory,imageBaseName);

		}

		public File getNextImageFile(IAtomContainer mol) throws IOException{
			File imageFile = null;
			if (mol.getProperty(CDKConstants.TITLE) != null) {
				// Use the molecule name in the file name
				imageFile = new File(directory, imageBaseName + "-"+(String) mol.getProperty(CDKConstants.TITLE) + ".png");
			} else {
				imageFile = new File(directory, imageBaseName + "-" + count + ".png");
			}
			count++;
			return imageFile;
		}
		
		public boolean equals(Object o) {
			if (!(o instanceof ImageFileHandler))
				return false;
			ImageFileHandler other = (ImageFileHandler) o;
			if (!imageBaseName.equals(other.imageBaseName))
				return false;
			if (directory == null && other.directory == null)
				return true;
			return directory.equals(other.directory);
		}
	}



	private void setupImageDepictors() throws IllegalArgumentException {
		if (gradientParams.createImgs){
			if (gradientParams.imageFile == null)
				throw new IllegalArgumentException("Image file must be given when creating images");

			try{
				// set up the output folder
				gradientFileHandler = new ImageFileHandler(gradientParams.imageFile);
			} catch(IOException e){
				LOGGER.debug("Could not set up the image output folder",e);
				throw new IllegalArgumentException("could not setup the output image folder: "+
						gradientParams.imageFile);
			}


			gradientDepictor = new MoleculeGradientDepictor(gradientParams.colorScheme);
			gradientDepictor.setDepictAtomNumbers(gradientParams.atomNumberColor!=null);
			gradientDepictor.setAtomNumColor(gradientParams.atomNumberColor);
			LOGGER.debug("finished configuring gradient depictor");
		}

		// Do the same for significant signature depictor
		if (significantSignatureParams.createImgs){
			if (significantSignatureParams.imageFile == null)
				throw new IllegalArgumentException("Image file must be given when creating images");

			try{
				// set up the output folder
				signatureFileHandler = new ImageFileHandler(significantSignatureParams.imageFile);
			} catch(IOException e){
				LOGGER.debug("Could not set up the image output folder",e);
				throw new IllegalArgumentException("Image file must be given when creating images");
			}


			signatureDepictor = new MoleculeSignificantSignatureDepictor(significantSignatureParams.highlightColor);
			signatureDepictor.setDepictAtomNumbers(significantSignatureParams.atomNumberColor!=null);
			signatureDepictor.setAtomNumColor(significantSignatureParams.atomNumberColor);
			LOGGER.debug("finished configuring significant signature depictor");
		}
		
		if (gradientParams.createImgs && 
				significantSignatureParams.createImgs &&
				 gradientFileHandler.equals(signatureFileHandler)) {
			throw new IllegalArgumentException("Gradient images and Significant signature images cannot be saved in the same location with the same name");
		}
	}

}
