/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleHTMLTagTokenizer {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(SimpleHTMLTagTokenizer.class);
	private final static Set<FontType> RECOGNIZED_TAGS = ImmutableSet.of(FontType.BOLD, FontType.ITALIC);
	private List<TextSection> sections=new ArrayList<>();
	private int index = 0;
	private final String text;
	
	public SimpleHTMLTagTokenizer(String text) throws MalformattedHTMLException{
		this.text = text;
		parseText(0, 0, new Stack<FontType>());
	}
	
	private void parseText(int sectionStartIndex, int currIndex, Stack<FontType> tags) throws MalformattedHTMLException {
		LOGGER.debug("(parseText) startIndex={}, currIndex={}, tags={}",sectionStartIndex,currIndex,tags);
		int potentialNewTagIndex = text.indexOf('<', currIndex);
		if (potentialNewTagIndex >= 0){
			LOGGER.debug("potential tag found at index={}",potentialNewTagIndex);
			// We have a potential tag
			Set<FontType> currentTags = new HashSet<>(tags);
			Triple<Boolean,Integer, Stack<FontType>> state = parseTag(potentialNewTagIndex, tags);
			if (state.getLeft()){
				// it was a new tag - finish and add the current TextSection
				int sectionEndIndex = Math.max(potentialNewTagIndex,0);
				if (sectionStartIndex < sectionEndIndex)
					addSection(new TextSection(text.substring(sectionStartIndex, sectionEndIndex), currentTags));			// <----- NEW SECTION ADDED
				parseText(state.getMiddle(), state.getMiddle(), state.getRight());
			} else {
				LOGGER.debug("found not to be a tag");
				// no new tag detected
				parseText(sectionStartIndex, currIndex+state.getMiddle(), tags);
			}
		} else {
			// No more tags - check that there is no open tags and finish of if everything was OK
			if (!tags.empty())
				throw new MalformattedHTMLException("No closing found for tag(s): "+tags.toString());
			if(text.length() > currIndex)
				addSection(new TextSection(text.substring(currIndex), new HashSet<FontType>()));							// <----- NEW SECTION ADDED
		}
	}
	
	
	
	
	
	private Triple<Boolean,Integer,Stack<FontType>> parseTag(int tagStartIndex, Stack<FontType> tags){
		if (!tags.empty()){
			// Check if the current tag is being closed
			FontType lastTag = tags.peek();
			if (text.regionMatches(tagStartIndex, lastTag.closingTag, 0, lastTag.closingTag.length())){
				// closed the current tag
				tags.pop();
				int nextIndex=tagStartIndex+lastTag.closingTag.length();
				LOGGER.debug("Closing tag: {}, located at index={}, continuing at index={}",lastTag,tagStartIndex, nextIndex);
				return ImmutableTriple.of(true, nextIndex, tags);
			}
		}
		
		// We did not close the lastly opened tag - look for all potential tags
		for (FontType tag: RECOGNIZED_TAGS){
			if (text.regionMatches(tagStartIndex, tag.openingTag, 0, tag.openingTag.length())){
				// Found an opening tag, make sure that it does not exist before in the stack
				if (tags.search(tag) != -1)
					throw new MalformattedHTMLException("Illegal nesting of tags found, multiple occurrances of tag: " + tag);
				// here we should add the new tag - it was valid
				tags.push(tag);
				return ImmutableTriple.of(true,tagStartIndex+tag.openingTag.length(), tags);
				
			} else if (text.regionMatches(tagStartIndex, tag.closingTag, 0, tag.closingTag.length())){
				throw new MalformattedHTMLException("Illegal nesting of tags found, closing tag: " +tag.closingTag + " found, but not linked to any opening tag");
			}
			// did not find this tag 
		}
		
		// no recognized tag found - this was just a "<" part of the text
		return ImmutableTriple.of(false,tagStartIndex+1, tags);
	}
	
	private void addSection(TextSection section){
		if(section.text == null || section.text.isEmpty())
			return;
		LOGGER.debug("Adding text='{}', with fonts={}", section.text,section.tags);
		sections.add(section);
	}
	
	public boolean hasNext(){
		return index < sections.size();
	}
	
	public TextSection next() throws IllegalAccessException {
		if (!hasNext())
			throw new IllegalAccessException("No more sections");
		return sections.get(index++);
	}
	
	public List<TextSection> getSections(){
		return sections;
	}
	
	public AttributedString toAttributedString(){
		StringBuilder sb = new StringBuilder();
		for(TextSection sec: sections)
			sb.append(sec.text);
		
		AttributedString str = new AttributedString(sb.toString());
		int indexInString=0;
		for(TextSection sec: sections){
			int sectionLength = sec.text.length();
			if (sec.tags.contains(FontType.ITALIC))
				str.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, indexInString, indexInString+sectionLength);
			if (sec.tags.contains(FontType.BOLD))
				str.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, indexInString, indexInString+sectionLength);
			indexInString+=sectionLength;
		}
		
		return str;
	}
	
	public static class TextSection {
		private final String text;
		private final Set<FontType> tags;
		private TextSection(String text, Set<FontType> tags) {
			this.text = text;
			this.tags = tags;
		}
		public String getText(){
			return text;
		}
		public Set<FontType> getFontTypes(){
			return tags;
		}
		public int getFontStyle(){
			if(tags.isEmpty())
				return Font.PLAIN;
			int style = 0;
			for (FontType tag: tags)
				style +=tag.fontStyle;
			return style;
		}
		
		public String toString(){
			String res = text;
			for(FontType tag: tags)
				res = tag.openingTag + res + tag.closingTag;
			return res;
		}
	}
	
	public enum FontType {
		ITALIC ("<i>", "</i>", Font.ITALIC), 
		BOLD ("<b>", "</b>", Font.BOLD);
		
		private final String openingTag, closingTag;
		private final int fontStyle;
		
		private FontType(String startText, String closingText, int fontStyle){
			this.openingTag = startText;
			this.closingTag = closingText;
			this.fontStyle = fontStyle;
		}
		
		public String toString(){
			return openingTag;
		}
		
		public String getOpeningTag(){
			return openingTag;
		}
		
		public String getClosingTag(){
			return closingTag;
		}
		
		public int getStyle(){
			return fontStyle;
		}
	}
	
	public class MalformattedHTMLException extends IllegalArgumentException {
		private static final long serialVersionUID = 5787697106048939440L;

		public MalformattedHTMLException(String exceptionText){
			super(exceptionText);
		}
	}

}
