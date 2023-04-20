/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.params.mixins;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.cpsign.app.params.converters.ConfigUtils;
import com.arosbio.cpsign.app.params.converters.MLAlgorithmConverter;
import com.arosbio.cpsign.app.params.converters.MLTypeConverters;
import com.arosbio.cpsign.app.params.converters.NCMConverter;
import com.arosbio.cpsign.app.params.converters.PValueCalulatorConverter;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.ScoringClassifier;
import com.arosbio.ml.algorithms.linear.LogisticRegression;
import com.arosbio.ml.algorithms.svm.C_SVC;
import com.arosbio.ml.algorithms.svm.EpsilonSVR;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.algorithms.svm.NuSVC;
import com.arosbio.ml.algorithms.svm.NuSVR;
import com.arosbio.ml.algorithms.svm.PlattScaledC_SVC;
import com.arosbio.ml.algorithms.svm.PlattScaledNuSVC;
import com.arosbio.ml.algorithms.svm.SVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.icp.ICPClassifier;
import com.arosbio.ml.cp.icp.ICPRegressor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.calc.StandardPValue;
import com.arosbio.ml.cp.nonconf.classification.InverseProbabilityNCM;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.classification.PositiveDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.classification.ProbabilityMarginNCM;
import com.arosbio.ml.cp.nonconf.regression.AbsDiffNCM;
import com.arosbio.ml.cp.nonconf.regression.LogNormalizedNCM;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.sampling.FoldedSampling;
import com.arosbio.ml.sampling.FoldedStratifiedSampling;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.sampling.RandomStratifiedSampling;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.vap.avap.AVAPClassifier;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

public abstract class PredictorMixinClasses {

	private static final Logger LOGGER = LoggerFactory.getLogger(PredictorMixinClasses.class);

	public static class AllPTMixin {

		@Mixin
		PTMixin pt = new PTMixin();

		@Mixin
		SamplingMixin ss = new SamplingMixin();

		@Mixin
		NCMMixin ncm = new NCMMixin();

		@Mixin
		PValueCalcMixin pval = new PValueCalcMixin();

		@Mixin
		ScorerModelParams alg = new ScorerModelParams();

		@Mixin
		ErrorModelParams errorModel = new ErrorModelParams();

		public Predictor getPredictor(CLIConsole cons) {
			return PredictorMixinClasses.getPredictor(pt, ss, ncm, alg, errorModel, pval, cons);
		}
	}

	public static class TCPMixin {

		@Mixin
		NCMMixin ncm = new NCMMixin();

		@Mixin
		PValueCalcMixin pval = new PValueCalcMixin();

		@Mixin
		ScorerModelParams alg = new ScorerModelParams();

		public Predictor getPredictor(CLIConsole cons) {
			PTMixin pt = new PTMixin();
			pt.predictorType = PredictorType.TCP_CLASSIFICATION;
			return PredictorMixinClasses.getPredictor(pt, null, ncm, alg, null, pval, cons);
		}

	}


	static class PTMixin {

		// PREDICTOR TYPE
		@Option(names = { "-pt", "--ptype", "--predictor-type" }, 
				description = "Predictor type:%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(1) ACP_Classification%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(2) ACP_Regression%n" + 
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(3) TCP_Classification%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + "(5) VAP_Classification%n"+
						ParameterUtils.DEFAULT_VALUE_LINE,
						defaultValue = "1",
						converter = MLTypeConverters.MLTypeConverter.class,
						paramLabel = ArgumentType.ID_OR_TEXT
				)
		public PredictorType predictorType = PredictorType.ACP_CLASSIFICATION;

	}

	static class SamplingMixin {

		@Option(names = {"-ss", "--sampling-strategy"}, 
				description = "Strategy used for sampling data to aggregated models (non TCP). Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON + "explain sampling"+ParameterUtils.ANSI_OFF+
				" to get all available strategies and their parameters. "+
				"The standard strategies are the following:%n" +
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+RandomSampling.ID +") " + RandomSampling.NAME +"%n"+ 
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+RandomStratifiedSampling.ID +") " + RandomStratifiedSampling.NAME +"    (classification only)%n"+ 
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+FoldedSampling.ID +") " + FoldedSampling.NAME +"%n"+ 
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+FoldedStratifiedSampling.ID +") " + FoldedStratifiedSampling.NAME +"    (classification only)%n"+
				ParameterUtils.DEFAULT_VALUE_LINE,
				defaultValue = "1",
				converter = SamplingStrategyConverter.class,
				paramLabel = ArgumentType.ID_OR_TEXT) 
		public SamplingStrategy samplingStrat = new RandomSampling();

	}

	static class NCMMixin {

		@Option(names = {"--ncm", "--nonconf-measure"}, 
				converter = NCMConverter.class,
				description = "Nonconformity measure that should be used, see documentation for clarifications and further customizations available. "
						+ "Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain ncm"+ParameterUtils.ANSI_OFF+" to get further information %nOptions (Regression):%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + LogNormalizedNCM.ID +")  "+ LogNormalizedNCM.IDENTIFIER +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + NormalizedNCM.ID +")  "+ NormalizedNCM.IDENTIFIER +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + AbsDiffNCM.ID +")  "+ AbsDiffNCM.IDENTIFIER +"%n"+
						"Options (Classification):%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + NegativeDistanceToHyperplaneNCM.ID +") "+ NegativeDistanceToHyperplaneNCM.IDENTIFIER +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + PositiveDistanceToHyperplaneNCM.ID +") "+ PositiveDistanceToHyperplaneNCM.IDENTIFIER +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + InverseProbabilityNCM.ID +") "+ InverseProbabilityNCM.IDENTIFIER +" (Only for Probabilistic scorers - slower to compute)%n" +
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + ProbabilityMarginNCM.ID +") "+ ProbabilityMarginNCM.IDENTIFIER +" (Only for Probabilistic scorers - slower to compute)%n"+
						ParameterUtils.DEFAULT_PRE_TEXT+"1 or 11 (regression / classification)",
						paramLabel = ArgumentType.ID_OR_TEXT
				) 
		public NCM nonconf;

		public NCM getNCMInstance(PredictorType mlType, MLAlgorithm impl, MLAlgorithm errImpl, CLIConsole console) {

			if (mlType.isAVAP()) {
				return null; // No NCM for VAP
			}

			// CLASSIFICATION
			if (mlType.isClassification()) {

				if (nonconf == null) {
					if (impl instanceof SVC) {
						return new NegativeDistanceToHyperplaneNCM((SVC) impl);
					} else if (impl instanceof PseudoProbabilisticClassifier) {
						return new InverseProbabilityNCM((PseudoProbabilisticClassifier)impl);
					} else {
						LOGGER.debug("No explicit NCM given, defaulting to the NegativeDistance-based NCM (classification), but faulty ML impl: {}", impl.getName());
						console.failWithArgError("NCM " + NegativeDistanceToHyperplaneNCM.IDENTIFIER + " can only be used with SVC type scoring algorithm");
					}
				}

				if (! (nonconf instanceof NCMMondrianClassification)) {
					LOGGER.debug("User chose NCM {} which is not allowed for CP Classification",nonconf.getName());
					console.failWithArgError("NCM " + nonconf.getName() + " not allowed for CP Classification");
				}

				NCMMondrianClassification ncmClass = (NCMMondrianClassification) nonconf;

				try {
					if (impl instanceof Classifier)
						ncmClass.setModel((Classifier)impl);
				} catch (Exception e) {
					console.failWithArgError("NCM {} does not support an ML Algorithm of type {}",
							nonconf.getName(), impl.getName());
				}

				LOGGER.debug("Initialized NCM of type: {}", nonconf.getName());
				return nonconf;
			}
			else {

				if (! (impl instanceof Regressor)) {
					console.failWithArgError("ML implementation must be of type Regressor for ACP Regression");
				}
				Regressor regModel = (Regressor)impl;

				// default NCM
				if (nonconf == null) {
					return new LogNormalizedNCM(regModel);
				}

				if (! (nonconf instanceof NCMRegression)) {
					LOGGER.debug("User chose NCM {} which is not allowed for CP Regression",nonconf.getName());
					console.failWithArgError("NCM " + nonconf.getName() + " not allowed for ACP Regression");
				}

				NCMRegression ncmReg = (NCMRegression) nonconf;

				try {
					ncmReg.setModel(regModel);
				} catch (Exception e) {
					console.failWithArgError("NCM " + nonconf.getName() + " does not support an ML Algorithm of type " + impl.getName());
				}

				try {
					if (ncmReg.requiresErrorModel()) {
						if (errImpl != null) {
							ncmReg.setErrorModel(errImpl);
						} else {
							ncmReg.setErrorModel(regModel.clone());
						}
					}
				} catch (Exception e) {
					console.failWithArgError("NCM " + nonconf.getName() + " does not support error model of ML Algorithm type " + impl.getName());
				}

				return ncmReg;

			}
		}
	}


	static class PValueCalcMixin {

		@Option(names = "--pvalue-calc",
				description = "Choose the calculation of p-values (and nonconformity score for regression). Available options:%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + StandardPValue.ID +") "+ StandardPValue.NAME +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + SmoothedPValue.ID +") "+ SmoothedPValue.NAME +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + LinearInterpolationPValue.ID +") "+ LinearInterpolationPValue.NAME +"%n"+
						ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '(' + SplineInterpolatedPValue.ID +") "+ SplineInterpolatedPValue.NAME +"%n"+
						ParameterUtils.DEFAULT_VALUE_LINE,
						defaultValue = "2",
						converter = PValueCalulatorConverter.class,
						paramLabel = ArgumentType.ID_OR_TEXT
				)
		public PValueCalculator pValueCalculator = new SmoothedPValue();

	}

	public static class ScorerModelParams {

		@Option(names = { "-sc", "--scorer" }, 
				description = "Scoring algorithm (i.e. underlying machine learning implementation). Run "+ParameterUtils.RUN_EXPLAIN_ANSI_ON+"explain scorer"+ParameterUtils.ANSI_OFF
				+ " for available parameters for each scoring algorithm (and possibly extra scoring algorithms that "
				+ "can be added through ServiceLoader-functionality).%nRegression algorithms:%n"+
				// REGRESSION
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ LinearSVR.ALG_ID +") "+LinearSVR.ALG_NAME+"%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ EpsilonSVR.ALG_ID +") " + EpsilonSVR.ALG_NAME + "%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ NuSVR.ALG_ID +") " + NuSVR.ALG_NAME+

				// CLASSIFICATION
				"%nClassification algorithms:%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ LinearSVC.ALG_ID +") "+LinearSVC.ALG_NAME+"%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ C_SVC.ALG_ID +") " + C_SVC.ALG_NAME + "%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ NuSVC.ALG_ID +") " + NuSVC.ALG_NAME + "%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ PlattScaledC_SVC.ALG_ID +") "+PlattScaledC_SVC.ALG_NAME+"%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ PlattScaledNuSVC.ALG_ID +") " + PlattScaledNuSVC.ALG_NAME + "%n"+
				ParameterUtils.MULTIPLE_OPTIONS_INDENTATION + '('+ LogisticRegression.ALG_ID +") " + LogisticRegression.ALG_NAME+ "%n"+

				ParameterUtils.DEFAULT_PRE_TEXT+"1 or 11 (regression / classification)",
				paramLabel = ArgumentType.ID_OR_TEXT,
				converter = MLAlgorithmConverter.class)
		public MLAlgorithm scorerAlgorithm = null;

		public MLAlgorithm getMLAlg(boolean isClassification, CLIConsole console) {

			if (scorerAlgorithm != null) {
				return scorerAlgorithm;
			} else {
				return isClassification ? new LinearSVC() : new LinearSVR();
			}

		}

	}

	static class ErrorModelParams {

		@Option(names = { "-es", "--error-scorer" }, 
				description = "Error model algorithm (only available for the NCMs that use an error-model for normalizing the nonconformity based on difficulty of the examples)."
						+ " The same algorithms are available as per the "+ParameterUtils.PARAM_FLAG_ANSI_ON+ "--scorer"+ParameterUtils.ANSI_OFF+" flag.",
						converter = MLAlgorithmConverter.class,
						paramLabel = ArgumentType.ID_OR_TEXT
				)
		public MLAlgorithm errScorerAlgorithm = null;

	}


	@SuppressWarnings("null")
	static Predictor getPredictor(PTMixin predictorType, 
			SamplingMixin samplingStrat, 
			NCMMixin ncm, 
			ScorerModelParams mlAlgs, 
			ErrorModelParams errParams,
			PValueCalcMixin pval, 
			CLIConsole console) {
		Predictor pred = null;

		if (predictorType == null)
			console.failWithArgError("Parameter --predictor-type is required");

		// Make sure to set the RNG seed (which could have been given by the user)
		PValueCalculator calculator = pval.pValueCalculator;
		calculator.setRNGSeed(GlobalConfig.getInstance().getRNGSeed());

		switch (predictorType.predictorType) {
		case ACP_CLASSIFICATION:
			MLAlgorithm alg = mlAlgs.getMLAlg(true, console);
			NCMMondrianClassification ncmClass = (NCMMondrianClassification) ncm.getNCMInstance(
					predictorType.predictorType, 
					alg, // Scoring alg
					(errParams.errScorerAlgorithm!=null ? errParams.errScorerAlgorithm: alg.clone()), // error-scorer
					console);
			pred = new ACPClassifier(new ICPClassifier(ncmClass), samplingStrat.samplingStrat);
			((ACPClassifier)pred).getICPImplementation().setPValueCalculator(calculator);
			break;
		case ACP_REGRESSION:
			MLAlgorithm scorer = mlAlgs.getMLAlg(false, console);
			NCMRegression ncmReg = (NCMRegression) ncm.getNCMInstance(
					predictorType.predictorType, 
					scorer,
					(errParams.errScorerAlgorithm!=null? errParams.errScorerAlgorithm : scorer.clone()), 
					console);
			pred = new ACPRegressor(new ICPRegressor(ncmReg), samplingStrat.samplingStrat);
			((ACPRegressor)pred).getICPImplementation().setPValueCalculator(calculator);
			break;
		case VAP_CLASSIFICATION:
			MLAlgorithm avapScorer = mlAlgs.getMLAlg(true, console);
			if (!(avapScorer instanceof ScoringClassifier))
				console.failWithArgError("Venn ABERS can only be run with a ScoringClassifier type algorithm");
			pred = new AVAPClassifier((ScoringClassifier) avapScorer, samplingStrat.samplingStrat);
			break;
		case VAP_REGRESSION:
			console.failWithArgError("Parameter --predictor-type invalid: VAP Regression not possible");
			break;
		case TCP_CLASSIFICATION:
			MLAlgorithm tpcAlg = mlAlgs.getMLAlg(true, console);
			NCMMondrianClassification ncmClassTCP = (NCMMondrianClassification) ncm.getNCMInstance(
					predictorType.predictorType, 
					tpcAlg, // Scoring alg
					null, // err alg
					console);
			pred = new TCPClassifier(ncmClassTCP);
			((TCPClassifier)pred).setPValueCalculator(calculator);
			break;
		case TCP_REGRESSION:
			console.failWithArgError("Parameter --predictor-type invalid: TCP Regression not possible");
			break;
		default:
			console.failWithArgError("Parameter --predictor-type invalid: " + predictorType );
		}

		// Stratified only allowed for classification
		if (!predictorType.predictorType.isClassification() && samplingStrat.samplingStrat.isStratified()) {
			console.failWithArgError("Stratified sampling is only supported for classification");
		}

		return pred;
	}


	public static class SamplingStrategyConverter implements ITypeConverter<SamplingStrategy> {

		@Override
		public SamplingStrategy convert(String text) {
			LOGGER.debug("Trying to parse a sampling strategy from input: {}", text);
			String[] splits = text.split(":");

			SamplingStrategy ss = null;
			try {
				ss = FuzzyServiceLoader.load(SamplingStrategy.class, splits[0]);
			} catch (IllegalArgumentException e) {
				LOGGER.debug("Failed configuring sampling strategy with input: " + text);
				throw new TypeConversionException("Sampling strategy "+(ss!=null? ss.getName()+" " : "") + "could not be configured with given parameters: " + e.getMessage());
			}

			if (splits.length>1) {
				LOGGER.debug("Parameters were given, will try to configure the sampling strategy");
				List<String> args = new ArrayList<>();
				for (int i=1; i<splits.length; i++)
					args.add(splits[i]);
				ConfigUtils.setConfigs(ss, args, text);
			}

			return ss;

		}

	}

}
