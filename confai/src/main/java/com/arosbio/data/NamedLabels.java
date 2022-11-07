/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NamedLabels {
	
	private Map<Integer, String> mapping = new HashMap<>();
	private Map<String, Integer> reverseMapping = new HashMap<>();
	
	public NamedLabels() {
	}
	
	public NamedLabels(String... sequentialLabels) {
		this(Arrays.asList(sequentialLabels));
	}
	
	public NamedLabels(List<String> sequentialLabels){
		for(int i=0; i<sequentialLabels.size(); i++){
			mapping.put(i, sequentialLabels.get(i).trim());
			reverseMapping.put(sequentialLabels.get(i), i);
		}
	}
	
	public NamedLabels(Map<Integer, String> labels){
		mapping = new HashMap<>(labels);
		updateReverseMapping();
	}
	
	public static NamedLabels fromReversedLabels(Map<String,Integer> reverseLabels) {
		Map<Integer,String> correctLabels = new HashMap<>();
		for (Map.Entry<String, Integer> entry: reverseLabels.entrySet()) {
			correctLabels.put(entry.getValue(), entry.getKey());
		}
		return new NamedLabels(correctLabels);
	}
	
	public NamedLabels(NamedLabels labels) {
		mapping = new HashMap<>(labels.mapping);
		reverseMapping = new HashMap<>(labels.reverseMapping);
	}
	
	public boolean isEmpty() {
		return mapping.isEmpty();
	}
	
	public NamedLabels clone() {
		return new NamedLabels(mapping);
	}
	
	public void addLabel(int numericValue, String label){
		mapping.put(numericValue, label);
		reverseMapping.put(label, numericValue);
	}
	
	public void setLabels(Map<Integer, String> labels){
		if (labels == null)
			mapping.clear();
		else {
			mapping = labels;
			updateReverseMapping();
		}
	}
	
	private void updateReverseMapping(){
		reverseMapping.clear();
		for(Map.Entry<Integer, String> label: mapping.entrySet())
			reverseMapping.put(label.getValue(), label.getKey());
	}
	
	public void updateLabels(Map<String,String> newLabels) throws IllegalArgumentException {
		if (newLabels == null || newLabels.isEmpty())
			throw new IllegalArgumentException("New labels cannot be null or empty");
		for (Map.Entry<Integer, String> label: mapping.entrySet()){
			if (newLabels.containsKey(label.getValue()))
				label.setValue(newLabels.get(label.getValue()));
		}
		updateReverseMapping();
	}
	
	/**
	 * Returns a <b>copy</b> - setting new values must be done through this class
	 * @return the mapping between numerical values to textual labels
	 */
	public Map<Integer, String> getLabels(){
		return new HashMap<>(mapping);
	}
	
	public Map<String,Integer> getReverseLabels(){
		return new HashMap<>(reverseMapping);
	}
	
	/**
	 * Returns a <b>copy</b> of the labels
	 * @return The textual labels used
	 */
	public Set<String> getLabelsSet(){
		return new HashSet<>(mapping.values());
	}
	
	public int getNumLabels(){
		return mapping.size();
	}
	
	public boolean contain(String label){
		return reverseMapping.containsKey(label);
	}
	
	public Integer getValue(String label) throws IllegalArgumentException{
		Integer val = reverseMapping.get(label);
		if (val == null)
			throw new IllegalArgumentException("Label not found: " + label);
		return val;
	}
	
	public String getLabel(int numericValue) throws IllegalArgumentException{
		String label = mapping.get(numericValue);
		if (label == null)
			throw new IllegalArgumentException("Label not found for numeric value: " + numericValue);
		return label;
	}
	
	public String toString() {
		return mapping.toString();
	}
	
	public int size() {
		return (mapping!=null? mapping.size() : 0);
	}
	
	public Map<String,Double> convert(Map<Integer,Double> input){
		Map<String,Double> res = new HashMap<>();
		for (Map.Entry<Integer, Double> entry: input.entrySet()) {
			res.put(mapping.get(entry.getKey()), entry.getValue());
		}
		return res;
	}
	
	public Map<Integer,Double> reverse(Map<String,Double> textual){
		Map<Integer,Double> res = new HashMap<>();
		for (Map.Entry<String, Double> entry: textual.entrySet()) {
			res.put(reverseMapping.get(entry.getKey()), entry.getValue());
		}
		return res;
	}
	
}
