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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.text.WordUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.tests.suites.UnitTest;
import com.arosbio.tests.utils.TestUtils;

@Category(UnitTest.class)
public class TestStringUtils {

	static boolean print = false;

	@Test
	public void testWrap() {
		String[] txts = new String[] {
				"Writing crosssssss results to file: "+"/private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"Writing cross \n   dadsf\n   dsfadsfvalidation results to file: "+"/private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"\tThis is and very long texts, how\twill this be handled?",
		};

		for (String t: txts) {
			assertWrappWorks(t, null);
		}
	}

	@Test
	public void testWrapWithIndent() {
		String[] txts = new String[] {
				"Writing crosssssss results to file: "+"/private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"Writing cross \n   dadsf\n   dsfadsfvalidation results to file: "+"/private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"\tThis is and very long texts, how\twill this be handled?",
		};

		for (String t: txts) {
			assertWrappWorks(t, "    ");
		}

		for (String t: txts) {
			assertWrappWorks(t, "    - ");
		}
	}


	void assertWrapWorks(String txt) {
		assertWrappWorks(txt, null);
	}

	void assertWrappWorks(String text, String indent){



		for (int width : new int[] {20,30,50,70}) {

			String textWRAPPED = (indent !=null ? StringUtils.wrap(text, width, indent) : StringUtils.wrap(text, width)) ;

			String[] textWRAPPED_SPLITTED = textWRAPPED.split("\n");

			for (String line: textWRAPPED_SPLITTED) {
				if (indent == null) {
					if (line.trim().length()>width) {
						Assert.assertEquals("Longer lines than expected must be 'unbreakable'",1,line.split("\\s").length);
					}
				}
				else {
					Assert.assertTrue("each line should start with the indentation",line.startsWith(indent));
					String indentRemoved = line.substring(indent.length(), line.length());
					if (line.trim().length()>width) {
						Assert.assertEquals("Longer lines than expected must be 'unbreakable': "+indentRemoved + " w:"+line.length()+" exp:" + width,1,indentRemoved.split("\\s").length);
					}
				}
				//				else
				//					Assert.fail("Text not wrapped: " + line + " w:" +line.length() + " expected: " + width);
			}
			if (print)
				System.out.println("'"+textWRAPPED+"'");
		}
	}

	@Test
	public void testWrapWithIndentNoFirstIndent() {
		String[] txts = new String[] {
				"Writing crosssssss results to file: /private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"Writing cross \n   dadsf\n   dsfadsfvalidation results to file: "+"/private/var/folders/5_/_ndtqgjn17l9y3vc3vcrygym0000gp/T/crossvalidationRes6723143346650463830.tsv",
				"\tThis is and very long texts, how will this be handled?",
		};

		for (String t: txts) {
			assertWrappWorksNotFirstLine(t, "\t");
		}

		for (String t: txts) {
			assertWrappWorksNotFirstLine(t, "    - ");
		}
	}

	void assertWrappWorksNotFirstLine(String text, String indent){

		for (int width : new int[] {20,30,50,70}) {

			String textWRAPPED = StringUtils.wrap(text, width, indent, false);
			String[] textWRAPPED_SPLITTED = textWRAPPED.split("\n");
			int i=0;
			for (String line: textWRAPPED_SPLITTED) {
				String indentRemoved = line;
				if (i != 0) {
					Assert.assertTrue("each line should start with the indentation: '" + line +'\'',line.startsWith(indent));
					indentRemoved = line.substring(indent.length(), line.length());
				}
				if (line.length()>width) {
					Assert.assertEquals("Longer lines than expected must be 'unbreakable': '"+line + "' w:"+line.length()+" exp:" + width,1,indentRemoved.split("\\s").length);
				}
				i++;
				//				if (line.length()>width)
				//					Assert.assertEquals("Longer lines than expected must be 'unbreakable'",1,line.split("\\s").length);
				//				else
				//					Assert.fail("Text not wrapped: " + line);
			}
			if (print)
				System.out.println("'"+textWRAPPED+"'");
		}
	}
	
	@Test
	public void testWrapDifferentIntialIndent() {
		String lorem = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.";
		String fixed = StringUtils.wrap(lorem, 30, "\t", "---");
		if (print)
			System.out.println(fixed);;
		Assert.assertTrue(fixed.startsWith("---"));
		String[] lines = fixed.split("\n");
		for (int i=0;i<lines.length;i++) {
			Assert.assertTrue(lines[i].trim().length() <= 30);
			if (i != 0)
				Assert.assertTrue(lines[i].startsWith("\t"));
		}
	}

	
//	@Test
	public void testWordWrap() {
		
		String wrapped = WordUtils.wrap("long texts, how will", 20-1,"\n ",false);
		System.err.println(wrapped);
		System.err.println(wrapped.length());
	}

	@Test
	public void testFailingWrapCV() {
		String text = "Output format, options:\n" + 
				"  (1) json\n" + 
				"  (2) text/plain\n" + 
				"  (3) TSV";
		String wrapped = StringUtils.wrap(text, 80, "       ");
		if (print)
			System.out.println("'\n"+wrapped+"'");
	}

	@Test
	public void testToCamelCase() {
		Assert.assertEquals("efficiencyPlot(fraction multi label predictions)",StringUtils.toCamelCase("Efficiency plot(fraction multi label predictions)"));

		Assert.assertEquals("(fraction multi label predictions)",StringUtils.toCamelCase("(fraction multi label predictions)"));
		Assert.assertEquals("(fraction (multi label) predictions)",StringUtils.toCamelCase("(fraction (multi label) predictions)"));
		Assert.assertEquals("expectedAccuracy",StringUtils.toCamelCase("Expected Accuracy"));
		Assert.assertEquals("calibrationPlot", StringUtils.toCamelCase("Calibration Plot"));
		Assert.assertEquals("R^2", StringUtils.toCamelCase("R^2"));
		Assert.assertEquals("RMSE", StringUtils.toCamelCase("RMSE"));
	}

	@Test
	public void testStandardizeInput() {
		Assert.assertEquals("Logistic Regression CVM",StringUtils.standardizeTextSplits("LogisticRegression-CVM"));
		Assert.assertEquals("C SVM",StringUtils.standardizeTextSplits("C_SVM"));
		Assert.assertEquals("C SVM",StringUtils.standardizeTextSplits("C-SVM"));
		Assert.assertEquals("C SVM",StringUtils.standardizeTextSplits("C SVM"));
	}


	//	@Test


	//	@Test
	public void testNonBreakingSpace() throws IOException {
		File tmpFile = TestUtils.createTempFile("tmp", ".csv");

		String txt = WordUtils.wrap("Saving model to file: " +  tmpFile + "\u00A0... ", 20);
		System.out.print(txt);
		System.out.print("Some more text");
		//		\u00A0
	}
	
	@Test
	public void testStringSplit() {
		String someString = "Here is some, text;what is the\tOutput going-to be?";
		Assert.assertEquals(Arrays.asList("Here","is","some","","text","what","is","the","Output","going-to","be?"), Arrays.asList(someString.split("[\\s,;]")));
	}
	
	@Test
	public void testEscapeSpecial() {
		Assert.assertEquals("\\n\\r",StringUtils.quoteEscapes("\n\r"));
		Assert.assertEquals("some_txt\\t",StringUtils.quoteEscapes("some_txt\t"));
	}
	
//	private static String addParamStyle(String param) {
//		return "@|yellow "+param+"|@";
//	}

	private static String addArgumentStyle(String argTxt) {
		return "@|italic "+argTxt + "|@";
	}
	private static String addRunCMDStyle(String cmd) {
		return "@|red "+cmd+"|@"; 
	}
	
	private static void addHeading(StringBuilder sb, String header, int consWidth) {
		// Bold underline
					com.arosbio.commons.StringUtils.paddBeforeCentering(sb, header, consWidth);
					sb.append("@|bold,underline ");
					sb.append(header.toUpperCase(Locale.ENGLISH));
					sb.append("|@%n%n");
	}
	
	@Test
	public void testStrangeCLIBug() {
		int CONSOLE_WIDTH = 247; // 209
		
		String SUB_NAME = "list-syntax";
		
		StringBuilder mainText = new StringBuilder();
		addHeading(mainText, "LIST NUMBERS SYNTAX", CONSOLE_WIDTH);
		
		StringBuilder toWrap = new StringBuilder()
				.append("Specifing a list of numbers, e.g. when chosing multiple confidence values or specify which parameters to try out in tuning, can be done in several different ways. ")
				.append("The first and most straight forward way is to list them explicitly, but this can be tedious if you wish to specifiy several numbers. ")
				.append("An alternative way is to specify them using a syntax of ")
				.append(addArgumentStyle("<start>:<stop>[:step]")).append(" or ").append(addArgumentStyle("<start>:<stop>[:step][:base]"))
				.append(" where cpsign will generate a list automatically. Note ")
				.append("that you can try this syntax and see the generated list by running e.g. ").append(addRunCMDStyle("explain "+SUB_NAME + " --test b10:1:5"))
				.append(". Further note that it is fully possible to combine the syntax for enumerating values and listing explicit numbers, like e.g. ").append(addArgumentStyle("b=2:-10:-2,10,100"))
				.append(" where an enumeration is performed using b=2:-10:-2 and then further use the explicit values 10 and 100.");
		
		mainText.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
		
//		System.err.println(mainText);
	}
	
	@Test
	public void testMatchFlagFormat() {
		Assert.assertTrue(matchFlagFmt("-td"));
		Assert.assertTrue(matchFlagFmt("--train-data"));
		Assert.assertTrue(matchFlagFmt("--tolerance"));
	}
	
	private static boolean matchFlagFmt(String txt) {
		return txt.matches("-{1,2}[a-z]+.*"); //.matches("^--\\p{Lower}++") || txt.matches("^-\\p{Lower}++");
	}
}

