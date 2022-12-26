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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openscience.cdk.interfaces.IAtomContainer;

import java.util.Set;

import com.arosbio.chem.io.out.image.RendererTemplate.RenderInfo;
import com.arosbio.cheminf.SignificantSignature;
import com.arosbio.commons.MathUtils;
import com.arosbio.cpsign.out.OutputNamingSettings.JSON;
import com.arosbio.cpsign.out.OutputNamingSettings.PredictionOutput;
import com.arosbio.ml.cp.CPRegressionPrediction;
import com.arosbio.ml.cp.CPRegressionPrediction.PredictedInterval;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

public class ResultsHandler {

	public SignificantSignature signSign;
	public Map<String, Double> pValues, probabilities;
	public Map<Double,Set<String>> predictedLabels;
	public Double y_hat;
	public CPRegressionPrediction regressionResultConfidenceBased;
	public CPRegressionPrediction regressionResultPredictionWidthBased;
	public Map<String, Integer> generatedSignatures;
	public Double p0p1IntervalMean, p0p1IntervalMedian;

	public void setSignificantSignatureResult(SignificantSignature signSign){
		this.signSign = signSign;
	}
	public void setPvalues(Map<String, Double> pVals){
		this.pValues = pVals;
	}
	public Map<String,Double> getPvals(){
		return this.pValues;
	}
	public void setProbabilities(Map<String,Double> probs) {
		probabilities = probs;
	}
	public Map<String,Double> getProbabilities(){
		return this.probabilities;
	}

	public void setY_hat(double y_hat) {
		this.y_hat = y_hat;
	}
	public void addRegressionResultConfBased(CPRegressionPrediction regRes){
		regressionResultConfidenceBased = regRes;
		this.y_hat = MathUtils.roundTo3significantFigures(regRes.getY_hat());
	}
	public void addRegressionResultPredWidthBased(CPRegressionPrediction regRes){
		regressionResultPredictionWidthBased = regRes;
		this.y_hat = MathUtils.roundTo3significantFigures(regRes.getY_hat());
	}
	public void addPredictedLabels(double confidence, Set<String> labels){
		if (predictedLabels == null)
			predictedLabels = new LinkedHashMap<>();
		predictedLabels.put(confidence, labels);
	}
	public void setGeneratedSignatures(Map<String, Integer> sigs){
		this.generatedSignatures = sigs;
	}

	public void setP0P1Interval(double mean, double median) {
		p0p1IntervalMean = mean;
		p0p1IntervalMedian = median;
	}

	public JsonObject getJSON(){
		JsonObject json = new JsonObject();
		JsonObject predictionSection = new JsonObject();

		// Significant signature
		if (signSign!=null){
			// Top level object
			JsonObject signSigJson = new JsonObject();

			// If we have a signature / atom-mapping
			if (signSign.getSignature() != null) {
				JsonObject atomVals = new JsonObject();
				List<Integer> atomIndices = new ArrayList<>(signSign.getAtomContributions().keySet());
				Collections.sort(atomIndices);
				for (int m : atomIndices){
					Number value = MathUtils.roundTo3significantFigures(signSign.getAtomContributions().get(m));
					atomVals.put("atom"+m,value);
				}
				signSigJson.put(JSON.ATOM_VALS_KEY, atomVals);
				signSigJson.put(JSON.SIGNIFICANT_SIGNATURE_KEY, signSign.getSignature());
				signSigJson.put(JSON.SIGNIFICANT_SIGNATURE_HEIGHT_KEY, signSign.getHeight());

			}
			if (signSign.getAdditionalFeaturesGradient()!=null && !signSign.getAdditionalFeaturesGradient().isEmpty()) {
				JsonObject additionalFeats = new JsonObject(signSign.getAdditionalFeaturesGradient());
				signSigJson.put(JSON.FEATURES_GRADIENT_KEY, additionalFeats);
			}
			json.put(JSON.GRADIENT_RESULTS_SECTION_KEY, signSigJson);
		}

		// Classification results
		if (pValues != null){
			JsonObject pValsJson = new JsonObject();
			for (Entry<String, Double> pVal : pValues.entrySet()){
				pValsJson.put(pVal.getKey(), pVal.getValue());
			}

			predictionSection.put(JSON.CLASS_PVALS_KEY, pValsJson);
		}
		if (predictedLabels != null && ! predictedLabels.isEmpty()){
			JsonArray labelsArray = new JsonArray();
			for(Entry<Double, Set<String>> labels: predictedLabels.entrySet()){
				JsonObject oneSet = new JsonObject();
				oneSet.put(JSON.CONFIDENCE_KEY, labels.getKey());
				oneSet.put(JSON.CLASS_LABELS_KEY, new ArrayList<>(labels.getValue()));
				labelsArray.add(oneSet);
			}

			predictionSection.put(JSON.CLASS_PREDICTED_LABELS_KEY, labelsArray);
		}

		if (probabilities !=null) {
			predictionSection.put(JSON.CLASS_PROBABILITIES_KEY, new JsonObject(probabilities));
		}

		if (p0p1IntervalMean !=null) {
			predictionSection.put(JSON.CVAP_PROBABILITY_INTERVAL_MEAN, p0p1IntervalMean);
		}
		if (p0p1IntervalMedian != null) {
			predictionSection.put(JSON.CVAP_PROBABILITY_INTERVAL_MEDIAN, p0p1IntervalMedian);
		}

		// Regression results
		JsonArray regArray = new JsonArray();
		if (y_hat != null)
			predictionSection.put(JSON.REG_MIDPOINT_KEY, MathUtils.roundTo3significantFigures(y_hat));
		if (regressionResultConfidenceBased != null) {
			if (!predictionSection.containsKey(JSON.REG_MIDPOINT_KEY)) {
				predictionSection.put(JSON.REG_MIDPOINT_KEY, MathUtils.roundTo3significantFigures(regressionResultConfidenceBased.getY_hat()));
			}
			List<PredictedInterval> orderedIntervals = new ArrayList<>(regressionResultConfidenceBased.getIntervals().values());
			for (PredictedInterval interval : orderedIntervals) {
				regArray.add(cpRegr2JSON(interval));
			}

		}
		if (regressionResultPredictionWidthBased != null) {
			if (!predictionSection.containsKey(JSON.REG_MIDPOINT_KEY)) {
				predictionSection.put(JSON.REG_MIDPOINT_KEY, MathUtils.roundTo3significantFigures(regressionResultPredictionWidthBased.getY_hat()));
			}
			List<PredictedInterval> orderedIntervals = new ArrayList<>(regressionResultPredictionWidthBased.getWidthToConfidenceBasedIntervals().values());
			for (PredictedInterval interval : orderedIntervals) {
				regArray.add(cpRegr2JSON(interval));
			}
		}
		if (!regArray.isEmpty())
			predictionSection.put(JSON.REG_INTERVALS_SECTION_KEY, regArray);

		// Add the predictions-section
		if (!predictionSection.isEmpty())
			json.put(JSON.PREDICTING_SECTION_KEY, predictionSection);

		// Generated signatures
		if (generatedSignatures != null){
			JsonObject signMapJSON = new JsonObject(generatedSignatures);
			json.put(JSON.GENERATED_SIGNATURES_SECTION_KEY, signMapJSON);
		}

		return json;
	}

	private static JsonObject cpRegr2JSON(PredictedInterval result) {

		JsonObject cobj = new JsonObject();

		Number lower = Double.NaN,
				upper = Double.NaN,
				confidence = Double.NaN,
				width = Double.NaN,
				lowerCapped = Double.NaN,
				upperCapped = Double.NaN;

		if (result.getInterval()!=null){
			lower = MathUtils.roundTo3significantFigures(result.getInterval().lowerEndpoint());
			upper = MathUtils.roundTo3significantFigures(result.getInterval().upperEndpoint());
		}
		if (result.getCappedInterval()!=null){
			lowerCapped = MathUtils.roundTo3significantFigures(result.getCappedInterval().lowerEndpoint());
			upperCapped = MathUtils.roundTo3significantFigures(result.getCappedInterval().upperEndpoint());
		}
		if (!Double.isNaN(result.getConfidence()))
			confidence = MathUtils.roundTo3significantFigures(result.getConfidence());
		if (!Double.isNaN(result.getIntervalWidth()))
			width = MathUtils.roundTo3significantFigures(result.getIntervalWidth());

		cobj.put(JSON.REG_RANGE_LOWER_KEY, lower);
		cobj.put(JSON.REG_RANGE_UPPER_KEY, upper);
		cobj.put(JSON.CONFIDENCE_KEY, confidence);
		cobj.put(JSON.REG_WIDTH_KEY, width);
		cobj.put(JSON.REG_RANGE_LOWER_CAPPED_KEY, lowerCapped);
		cobj.put(JSON.REG_RANGE_UPPER_CAPPED_KEY, upperCapped);

		return cobj;
	}

	public RenderInfo toRenderInfo(IAtomContainer mol){
		return new RenderInfo.Builder(mol,signSign)
			.pValues(pValues)
			.probabilities(probabilities)
			.build();
	}

	public Map<Object,Object> getFlatMapping(){
		Map<Object,Object> properties = new LinkedHashMap<>();

		// CP Classification 
		// p-values
		if (pValues !=null && ! pValues.isEmpty()){
			for (Map.Entry<String, Double> pVal : pValues.entrySet()) {
				properties.put(OutputNamingSettings.getPvalueForLabelProperty(pVal.getKey()), pVal.getValue());
			}
		}

		// predicted labels (for given confidence levels)
		if (predictedLabels !=null && ! predictedLabels.isEmpty()){
			for(Double conf : predictedLabels.keySet())
				properties.put(OutputNamingSettings.getPredictedLabelsProperty(conf), listToNoSpaceSetString(new ArrayList<>(predictedLabels.get(conf))));
		}

		// CP Regresson
		if (y_hat != null)
			properties.put(PredictionOutput.REGRESSION_Y_HAT_PREDICTION_PROPERTY, y_hat);

		if (regressionResultConfidenceBased !=null){
			List<PredictedInterval> orderedIntervals = new ArrayList<>(regressionResultConfidenceBased.getIntervals().values());
//			Collections.sort(orderedIntervals);
			for (PredictedInterval res: orderedIntervals) {
				properties.put(OutputNamingSettings.getPredictionIntervalLowerBoundProperty(res.getConfidence()), 
						MathUtils.roundTo3significantFigures(res.getInterval().lowerEndpoint()));
				properties.put(OutputNamingSettings.getPredictionIntervalUpperBoundProperty(res.getConfidence()), 
						MathUtils.roundTo3significantFigures(res.getInterval().upperEndpoint()));
				properties.put(OutputNamingSettings.getCappedPredictionIntervalLowerBoundProperty(res.getConfidence()), 
						MathUtils.roundTo3significantFigures(res.getCappedInterval().lowerEndpoint()));
				properties.put(OutputNamingSettings.getCappedPredictionIntervalUpperBoundProperty(res.getConfidence()), 
						MathUtils.roundTo3significantFigures(res.getCappedInterval().upperEndpoint()));
			}
		}

		if (regressionResultPredictionWidthBased !=null){
			List<PredictedInterval> orderedIntervals = new ArrayList<>(regressionResultPredictionWidthBased.getWidthToConfidenceBasedIntervals().values());
			Collections.sort(orderedIntervals);
			for (PredictedInterval res: orderedIntervals) {
				properties.put(OutputNamingSettings.getConfGivenWidthProperty(res.getIntervalWidth()), MathUtils.roundTo3significantFigures(res.getConfidence()));
			}
		}

		// CVAP
		if (probabilities != null) {
			for (Map.Entry<String, Double> ent: probabilities.entrySet()) {
				properties.put(OutputNamingSettings.getProbabilityProperty(ent.getKey()), ent.getValue());
			}
		}
		if (p0p1IntervalMean !=null)
			properties.put(PredictionOutput.CVAP_PROBABILITY_INTERVAL_MEAN_PROPERTY, p0p1IntervalMean);
		if (p0p1IntervalMedian !=null)
			properties.put(PredictionOutput.CVAP_PROBABILITY_INTERVAL_MEDIAN_PROPERTY, p0p1IntervalMedian);


		// Signatures
		if (generatedSignatures !=null && ! generatedSignatures.isEmpty()){
			properties.put(PredictionOutput.GENERATED_SIGNATURES_PROPERTY, generatedSignatures);
		}

		// Significant signature
		if (signSign!=null) {
			properties.put(PredictionOutput.SIGNIFICANT_SIGNATURE_PROPERTY, signSign.getSignature());
			properties.put(PredictionOutput.SIGNIFICANT_SIGNATURE_HEIGHT_PROPERTY, signSign.getHeight());
			properties.put(PredictionOutput.SIGNIFICANT_SIGNATURE_GRADIENT_PROPERTY, signSign.getAtomContributions());
			if (signSign.getAdditionalFeaturesGradient() != null) {
				for (Map.Entry<String, Double> grad : signSign.getAdditionalFeaturesGradient().entrySet()) {
					properties.put(OutputNamingSettings.getExtraFeatureGradientProperty(grad.getKey()), grad.getValue());
				}
			}

		}

		return properties;
	}

	private static <T> String listToNoSpaceSetString(List<T> list) {
		if (list==null || list.isEmpty()) {
			return "{\u2205}";
		}
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		for (int i=0; i<list.size()-1; i++) {
			sb.append(list.get(i));
			sb.append(", ");
		}
		sb.append(list.get(list.size()-1));
		sb.append('}');
		return sb.toString();
	}
}
