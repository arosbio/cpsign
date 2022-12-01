/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CDKConfigureAtomContainer;
import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFile;
import com.arosbio.chem.io.in.ChemFileParserUtils;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.SignaturesDescriptor;
import com.arosbio.cpsign.app.params.converters.ChemFileConverter;
import com.arosbio.cpsign.app.params.mixins.ConsoleVerbosityMixin;
import com.arosbio.cpsign.app.params.mixins.DescriptorsMixin;
import com.arosbio.cpsign.app.params.mixins.EchoMixin;
import com.arosbio.cpsign.app.params.mixins.LogfileMixin;
import com.arosbio.cpsign.app.params.mixins.OutputChemMixin;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.MissingParam;
import com.arosbio.cpsign.app.utils.CLIConsole.ParamComb;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.cpsign.out.OutputNamingSettings;
import com.arosbio.cpsign.out.PredictionResultsWriter;
import com.arosbio.cpsign.out.ResultsHandler;
import com.arosbio.cpsign.out.OutputNamingSettings.ProgressInfoTexts;
import com.arosbio.io.IOUtils;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;


@Command(
		name = GenerateSignatures.CMD_NAME, 
		description = GenerateSignatures.CMD_DESCRIPTION, 
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER, 
		header = GenerateSignatures.CMD_HEADER
		)
public class GenerateSignatures implements RunnableCmd {

	private static final Logger LOGGER = LoggerFactory.getLogger(GenerateSignatures.class);

	public static final String CMD_NAME = "gensign";
	public static final String CMD_HEADER = "Generate signatures molecular descriptors for compounds";
	public static final String CMD_DESCRIPTION = "Gensign generates the signatures for given molecules. Signature generation is automatic"+
			" within CPSign when running all other programs, this program is only intended to give the user a"+
			" grasp of how signature descriptors will be generated and how signature heights affect the generated"+
			" descriptors.";

	/*****************************************
	 * INTERNAL STATE
	 *****************************************/
	
	@Spec private CommandSpec spec;
	private CLIConsole console = CLIConsole.getInstance();
	private PredictionResultsWriter resultsOutputter;
	private SignaturesDescriptor signDescriptor = null;

	
	/*****************************************
	 * OPTIONS
	 *****************************************/

	@ArgGroup(multiplicity = "1..*")
	public AvailableInput toCompute;


	public static class AvailableInput {
		@Option(names = {"-sm", "--smiles"}, 
				description = "SMILES string to generate signatures of",
				required = false,
				paramLabel = ArgumentType.SMILES)
		private String smiles;

		@Option(names = {"-i", "--input"}, 
				description = "File to compute signatures for",
				parameterConsumer = ChemFileConverter.class,
				paramLabel = ArgumentType.CHEM_FILE_ARGS,
				required=false)
		private ChemFile inputFile;
	}

	// Signatures Section
	@Mixin
	private DescriptorsMixin descriptorSection = new DescriptorsMixin();

	// Output Section
	@Mixin
	private OutputChemMixin outputSection = new OutputChemMixin();

	// General Section

	@Mixin
	private ConsoleVerbosityMixin consoleArgs;

	@Mixin
	private LogfileMixin loggingArgs;

	@Mixin
	private EchoMixin echo;

	/*****************************************
	 * END OF OPTIONS
	 *****************************************/

	@Override
	public String getName() {
		return CMD_NAME;
	}

	/**
	 * Validate the arguments (after arguments has been parsed by JCommander and pre-validated
	 * as good as can be 
	 */
	private void validateArguments(){


		// Validate the arguments!
		if (toCompute.smiles == null && toCompute.inputFile == null) {
			console.failDueToMissingParameters(ParamComb.AND_OR,"",
					new MissingParam("smiles", "SMILES", AvailableInput.class),
					new MissingParam("inputFile", "INPUT_FILE", AvailableInput.class));
		}


	}

	@Override
	public Integer call(){

		CLIProgramUtils.doFullProgramConfig(this);

		try {
			console.print(OutputNamingSettings.ProgressInfoTexts.VALIDATING_ARGS, PrintMode.NORMAL);
			validateArguments();

			try {
				if (outputSection.printInChI)
					CPSignMolProperties.setupInChIGenerator();
			} catch (IllegalStateException e){
				LOGGER.debug("Failed initializing the InChiGeneratorFactory",e);
				console.printlnWrapped("WARNING: Could not initiate the InChiGenerator - output will not contain InChi or InChiKey", PrintMode.NORMAL);
			}

			List<ChemDescriptor> descriptors = descriptorSection.descriptors;
			if (descriptors.isEmpty()) {
				console.failWithArgError("No descriptors given");
			}

			if (descriptors.size() != 1 || ! (descriptors.get(0) instanceof SignaturesDescriptor)) {
				console.failWithArgError("Program " + CMD_NAME + " can only generate signatures at this time");
			}

			signDescriptor = (SignaturesDescriptor) descriptors.get(0);

			// Initialize the descriptor
			signDescriptor.initialize();

			// Finish the validation slightly early as the resultsOutputter will possibly grab the sys-out 
			console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);


			if (outputSection.outputFile!=null) {
				// results printed to file - nothing to sys-out 
				console.print("Computing signatures... ", PrintMode.NORMAL);
			} else {
				console.println("Computing signatures...", PrintMode.NORMAL);
			}

			// Set Results outputter
			resultsOutputter = CLIProgramUtils.setupResultsOutputter(this, 
					outputSection.outputFormat,
					outputSection.outputFile, 
					toCompute.inputFile, 
					outputSection.printInChI, 
					outputSection.compress);


			// Start computing signatures
			int count=0;

			if (toCompute.smiles!=null){
				try {
					IAtomContainer mol = ChemFileParserUtils.parseSMILES(toCompute.smiles);
					CPSignMolProperties.setRecordIndex(mol, "cmd-line-input");

					// Compute and output molecule signatures
					doOneMolecule(mol);
					count++;
				} catch (Exception e) {
					LOGGER.debug("Exception for --smiles molecule", e);
					console.printlnStdErr("Error generating signatures for "+
							CLIProgramUtils.getParamName(this, "smilesToPredict", "SMILES")+": " + e.getMessage(), PrintMode.NORMAL);
				}
			}

			if (toCompute.inputFile!=null){
				int totalNumMolecules=0;
				try {
					totalNumMolecules = toCompute.inputFile.countNumRecords();
				} catch (IllegalArgumentException | IOException e){
					LOGGER.debug("Could not parse the inputFile="+toCompute.inputFile, e);
					console.failWithNoMoleculesCouldBeLoaded(toCompute.inputFile);
				}
				int progressInterval = 100;
				if (totalNumMolecules > 10000)
					progressInterval = 1000;
				if (totalNumMolecules > 100000)
					progressInterval = 10000;

				try {
					Iterator<IAtomContainer> molsIterator = toCompute.inputFile.getIterator();

					boolean generationDone = false;
					IAtomContainer mol;
					while (molsIterator.hasNext()){
						try {
							mol = molsIterator.next();

							doOneMolecule(mol);

							generationDone = true;
							count++;

							if ( count % progressInterval == 0){
								// Print progress to stdout
								console.print(" - Generated signatures for %d/%d molecules%n", PrintMode.NORMAL,count,totalNumMolecules);
							}

						} catch (CDKException e) {
							LOGGER.error("Error predicting molecule in ChemFile: " + e.getMessage());
						} catch (IOException e){
							LOGGER.debug("IOException writing results",e);
						}
					}


					if (! generationDone)
						console.failWithNoMoleculesCouldBeLoaded(toCompute.inputFile);
				} catch (Exception e){
					LOGGER.debug("Failed computing signatures for inputFile",e);
					console.failWithArgError(
							"Failed computing signatures from "+toCompute.inputFile.getURI());
				}
			}

			if (outputSection.outputFile!=null)
				console.println(ProgressInfoTexts.DONE_TAG, PrintMode.NORMAL);

			// Finish program
			console.println("", PrintMode.NORMAL);
			return ExitStatus.SUCCESS.code;

		} finally {
			IOUtils.closeQuietly(resultsOutputter);
		}
	}

	private void doOneMolecule(IAtomContainer mol) throws CDKException, IOException{
		CDKConfigureAtomContainer.configMolecule(mol);
		ResultsHandler resHandler = new ResultsHandler();

		// Compute and output molecule signatures as JSON
		resHandler.generatedSignatures = signDescriptor.generateSignatures(mol);

		resultsOutputter.write(mol,resHandler);
		resultsOutputter.flush();
	}

}
