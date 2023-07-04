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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiRenderer;

import com.arosbio.chem.io.in.CSVFile;
import com.arosbio.chem.io.in.JSONFile;
import com.arosbio.chem.io.in.SDFile;
import com.arosbio.cheminf.ChemFilter;
import com.arosbio.cheminf.descriptors.CDKPhysChemWrapper;
import com.arosbio.cheminf.descriptors.ChemDescriptor;
import com.arosbio.cheminf.descriptors.DescriptorFactory;
import com.arosbio.cheminf.descriptors.fp.FPDescriptor;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.FuzzyMatcher;
import com.arosbio.commons.FuzzyServiceLoader;
import com.arosbio.commons.config.CharConfig;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.DescribedConfig;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.config.StringConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.commons.mixins.Described;
import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;
import com.arosbio.cpsign.app.params.converters.ListOrRangeConverter;
import com.arosbio.cpsign.app.utils.CLIConsole;
import com.arosbio.cpsign.app.utils.CLIConsole.PrintMode;
import com.arosbio.cpsign.app.utils.CLIProgramUtils;
import com.arosbio.cpsign.app.utils.MultiArgumentSplitter;
import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;
import com.arosbio.data.transform.Transformer;
import com.arosbio.data.transform.duplicates.DuplicatesResolverTransformer;
import com.arosbio.data.transform.feature_selection.FeatureSelector;
import com.arosbio.data.transform.filter.Filter;
import com.arosbio.data.transform.filter.LabelRangeFilter;
import com.arosbio.data.transform.impute.Imputer;
import com.arosbio.data.transform.scale.FeatureScaler;
import com.arosbio.data.transform.scale.VectorScaler;
import com.arosbio.ml.algorithms.Classifier;
import com.arosbio.ml.algorithms.MLAlgorithm;
import com.arosbio.ml.algorithms.MultiLabelClassifier;
import com.arosbio.ml.algorithms.PseudoProbabilisticClassifier;
import com.arosbio.ml.algorithms.Regressor;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.NCM;
import com.arosbio.ml.cp.nonconf.classification.NCMMondrianClassification;
import com.arosbio.ml.cp.nonconf.regression.NCMRegression;
import com.arosbio.ml.cp.tcp.TCPClassifier;
import com.arosbio.ml.metrics.Metric;
import com.arosbio.ml.metrics.MetricFactory;
import com.arosbio.ml.metrics.SingleValuedMetric;
import com.arosbio.ml.sampling.SamplingStrategy;
import com.arosbio.ml.testing.TestingStrategy;
import com.arosbio.ml.vap.avap.AVAPClassifier;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.TextTable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

@Command(
		name = ExplainArgument.CMD_NAME,
		header = ExplainArgument.CMD_HEADER,
		description = ExplainArgument.CMD_DESCRIPTION,
		descriptionHeading = CPSignApp.DESCRIPTION_HEADER,
		subcommands = {
				ExplainArgument.ChemFormatInfo.class,
				ExplainArgument.DescriptorInfo.class,
				ExplainArgument.ExclusiveDatasetsInfo.class,
				ExplainArgument.LabelsInfo.class,
				ExplainArgument.MetricsInfo.class,
				ExplainArgument.MLAlgInfo.class,
				ExplainArgument.NCMInfo.class,
				ExplainArgument.SamplingStratInfo.class,
				ExplainArgument.SyntaxInfo.class,
				ExplainArgument.TestSamplingInfo.class,
				ExplainArgument.TuneParamsInfo.class,
				ExplainArgument.TransformerInfo.class,
				ExplainArgument.ChemFiltersInfo.class,
				ExplainArgument.ListSyntaxInfo.class,
		}

		)
public class ExplainArgument implements Named {

	// Formatting stuff
	private final static String LEFT_DESCRIPTION = 	"  Description";
	private final static String LEFT_NAME = 		"- Name";
	private final static String LEFT_NAMES = 		"- Names";
	private final static String LEFT_ARGUMENT = 	"  Parameter";
	private final static String LEFT_ARGUMENTS = 	"  Parameters";
	private final static String LEFT_FEATURES = 	"  Features";
	private final static String LEFT_EXTRAS = 		"  Extras";
	private final static String LEFT_ID = 			"  ID";
	private final static String LEFT_APPLIES_TO = 	"  Applies to";
	private static final String LEFT_EVAL_POINTS = 	"  Eval points";
	private static final String CODE_EXAMPLE_LINE_START = "> ";
	private static final String SUPPORTS_MULTICLASS= "Supports multi-label classification";
	private static final String PROBABILITY_ML = 	"Produces probability-estimates";
	private static final String NON_BREAKING_SPACE = "\u00A0";

	public static final String CMD_NAME = "explain";
	public static final String CMD_HEADER = "Get detailed info about a parameter or flag";
	public static final String CMD_DESCRIPTION = "Get more detailed information about a given parameter in the CPSign CLI. Run @|bold explain param|@ for more detail about the specific parameter/concept.";

	@Spec private CommandSpec spec;
	private static final int CONSOLE_WIDTH = CLIConsole.getInstance().getTextWidth();

	private final static Help.Ansi ANSI = Help.Ansi.AUTO;

	private static Help.TextTable getTable(){
		return getTable(14);
	}

	private static Help.TextTable getTable(int w){
		if (w < 1)
			throw new IllegalArgumentException("width of first column cannot be less than 1");
		Help.Column[] cols = new Help.Column[2];
		cols[0] = new Help.Column(w, 0, Help.Column.Overflow.TRUNCATE);
		cols[1] = new Help.Column(CONSOLE_WIDTH-w-2, 1, Help.Column.Overflow.SPAN);

		TextTable tt = Help.TextTable.forColumns(Help.defaultColorScheme(ANSI), cols);
		return tt;
	}

	private static  void appendImplementation(TextTable tt, Object obj) {
		if (obj instanceof Named) {
			addNameRow(tt, (Named)obj);
		}
		if (obj instanceof HasID) {
			addIDRow(tt,(HasID)obj);
		}
		if (obj instanceof Described) {
			addDescription(tt, (Described)obj);
		}
		if (obj instanceof Configurable) {
			addConfs(tt, (Configurable) obj);
		}
		tt.addEmptyRow();
	}

	private static void addNameRow(TextTable tt, Named o) {
		if (o instanceof Aliased) {
			tt.addRowValues("@|bold "+LEFT_NAMES+"|@", "@|bold "+o.getName()+
					", " + StringUtils.join(((Aliased)o).getAliases(),", ")+"|@");
		} else {
			tt.addRowValues("@|bold "+LEFT_NAME+"|@", "@|bold "+o.getName()+"|@");
		}
	}

	private static void addNameRow(TextTable tt, List<String> names) {
		if (names==null || names.isEmpty()) {
			tt.addRowValues("@|bold "+LEFT_NAME+"|@", "<none>");
		} else if (names.size()>1) {
			tt.addRowValues("@|bold "+LEFT_NAMES+"|@", "@|bold " +
					StringUtils.join(names,", ")+"|@");
		} else {
			tt.addRowValues("@|bold "+LEFT_NAME+"|@", "@|bold "+names.get(0)+"|@");
		}
	}

	private static void addIDRow(TextTable tt, HasID o) {
		tt.addRowValues(LEFT_ID, ""+o.getID());
	}

	private static void addDescription(TextTable tt, Described o) {
		tt.addRowValues(LEFT_DESCRIPTION, o.getDescription());
	}

	private static void addConfs(TextTable tt, Configurable o) {
		List<ConfigParameter> params = o.getConfigParameters();
		if (params == null || params.isEmpty())
			return;

		boolean first = true;

		for (ConfigParameter param : params) {
			ConfigParameter p = param;
			if (param instanceof DescribedConfig) {
				p = ((DescribedConfig) param).getOriginalConfig();
			}
			String left = "";
			if (first) {
				left = (params.size()>1 ? LEFT_ARGUMENTS : LEFT_ARGUMENT);
			}
			first=false;

			StringBuilder right = new StringBuilder();
			right.append("- @|bold ");
			right.append(StringUtils.join(p.getNames(),", "));
			right.append("|@ [");
			right.append(p.getType());
			right.append("]");
			tt.addRowValues(left, right.toString());


			StringBuilder defSb = new StringBuilder("  default: ");

			if (p.getDefault() != null) {
				if (p instanceof StringConfig || p instanceof CharConfig)
					defSb.append(com.arosbio.commons.StringUtils.quoteEscapes(p.getDefault().toString()));
				else 
					defSb.append(p.getDefault());
			} else {
				defSb.append("<none>");
			}

			tt.addRowValues("",defSb.toString());

			if (p instanceof EnumConfig || p instanceof IntegerConfig || p instanceof NumericConfig) {
				StringBuilder allowedSb = new StringBuilder("  allowed values: ");
				boolean added = false;
				if (p instanceof EnumConfig) {
					allowedSb.append(StringUtils.join(((EnumConfig<?>) p).getEnumValues(),", "));
					added=true;
				} else if (p instanceof IntegerConfig) {
					if (((IntegerConfig) p).getAllowedRange() != null) {
						allowedSb.append(((IntegerConfig) p).getAllowedRange());
						added=true;
					}
				}  else if (p instanceof NumericConfig) {
					if (((NumericConfig) p).getAllowedRange() != null) {
						allowedSb.append(((NumericConfig) p).getAllowedRange());
						added=true;
					}
				}
				if (added)
					tt.addRowValues("",allowedSb.toString());
			}


			if (param instanceof Described) {
				tt.addRowValues("","  "+((Described)param).getDescription());
			}
		}

	}

	@Override
	public String getName() {
		return CMD_NAME;
	}

	private static void addHeading(StringBuilder sb, String header) {
		sb.append("%n");
		if (CLIConsole.getInstance().ansiON()) {
			// Bold underline
			com.arosbio.commons.StringUtils.paddBeforeCentering(sb, header, CONSOLE_WIDTH);
			sb.append("@|bold,underline ");
			sb.append(header.toUpperCase(Locale.ENGLISH));
			sb.append("|@%n%n");
		} else {
			// wide underline of normal text
			sb.append(StringUtils.center(header,CONSOLE_WIDTH));
			sb.append("%n");
			sb.append(StringUtils.repeat('-', CONSOLE_WIDTH));
			sb.append("%n%n");
		}
	}

	private static void addSubHeading(StringBuilder sb, String heading) {
		sb.append("%n");
		if (CLIConsole.getInstance().ansiON()) {
			sb.append("@|bold ");
			sb.append(heading.toUpperCase(Locale.ENGLISH));
			sb.append("|@");
		} else {
			sb.append(StringUtils.center(heading, CONSOLE_WIDTH));
			sb.append("%n");
			sb.append(StringUtils.center(StringUtils.repeat('-', heading.length()), CONSOLE_WIDTH));
		}
		sb.append("%n");
	}

	private static String addParamStyle(String param) {
		return "@|yellow "+param+"|@";
	}

	private static String addArgumentStyle(String argTxt) {
		return "@|italic "+argTxt + "|@";
	}

	private static String addNameStyle(String name) {
		return "@|bold " + name + "|@";
	}

	private static String addParamStyles(final String text, String...flags) {
		String txt = text;
		for (String f : flags) {
			txt = txt.replace(f, addParamStyle(f));
		}
		return txt;
	}

	private static String addRunCMDStyle(String cmd) {
		return "@|red "+cmd+"|@"; 
	}

	private static String replaceRunCMDStyles(final String text, String...cmds) {
		String txt = text;
		for (String c : cmds) {
			txt = txt.replace(c, addRunCMDStyle(c));
		}
		return txt;
	}

	public static class IDSorter implements Comparator<HasID>{

		@Override
		public int compare(HasID o1, HasID o2) {
			return Integer.compare(o1.getID(), o2.getID());
		}

	}

	@Command(helpCommand=true,
			name = LabelsInfo.SUB_NAME,
			aliases = LabelsInfo.SUB_ALIAS,
			description = LabelsInfo.SUB_DESCRIPTION)
	public static class LabelsInfo implements RunnableCmd {

		public final static String SUB_NAME = "labels";
		public final static String SUB_ALIAS = "class-labels";
		public final static String SUB_DESCRIPTION = "How classification labels are given as input";

		private int consWidth = CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME).getTextWidth();

		@Override
		public Integer call() throws Exception {
			StringBuilder sb = new StringBuilder();
			addHeading(sb, "CLASS LABELS");

			// main text
			StringBuilder toWrap = new StringBuilder("The class labels should correspond to labels used in the data file(s). Labels could ")
					.append("be either textual or numerical values. (Note that some earlier CPSign versions had ") 
					.append("issues with negative numerical values, but that is no longer the case.) Examples:");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), consWidth));

			sb.append("%n%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--labels")).append(addArgumentStyle(" -1 1")).append("                 (whitespace character separated list)%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--labels")).append(addArgumentStyle(" -1,1")).append("                 (comma-separated list)%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--labels")).append(addArgumentStyle(" mutagen nonmutagen")).append("%n") 
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--labels")).append(addArgumentStyle(" mutagen,nonmutagen")).append("%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--labels")).append(addArgumentStyle(" \"Label A\" \"Label B\"")).append("  (labels containing spaces)").append("%n%n") ;

			CLIConsole.getInstance().println(sb.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = SyntaxInfo.SUB_NAME,
			aliases = SyntaxInfo.SUB_ALIAS,
			description = SyntaxInfo.SUB_DESCRIPTION)
	public static class SyntaxInfo implements RunnableCmd {
		public final static String SUB_NAME = ":-syntax";
		public final static String SUB_ALIAS = "syntax";
		public final static String SUB_DESCRIPTION = "Information on how to use :-syntax";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Override
		public Integer call() throws Exception {
			StringBuilder sb = new StringBuilder();
			addHeading(sb, ": - SYNTAX");

			// main text
			StringBuilder toWrap = new StringBuilder()
					.append("To reduce the number of parameters-flags in CPSign and to create a more natural grouping of ")
					.append("arguments, CPSign supports ':-syntax' for many of its arguments. What this means is that ")
					.append("sub-parameters are specified together with the parameter itself by separating the arguments ") 
					.append("and subsequent sub-arguments with a ':' character. E.g. when specifying the scorer-implementation and ")
					.append("its unique arguments such as kernel-type, kernel-parameters, cost, epsilon, etc. The available ")
					.append("sub-arguments are specific for each scorer-implementations and can be retrieved from the ") 
					.append("corresponding help-menu (explain").append(NON_BREAKING_SPACE).append("scorer).");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			sb.append("%n");
			addSubHeading(sb, "Syntax");

			toWrap = new StringBuilder()
					.append("The :-syntax can either be specified with the sub-parameters explicitly named or by using the ") 
					.append("order of the parameters. The general usage is either (in explicit form):%n%n")
					.append("<param-flag> <main-argument>:<sub-param-1-name>=<sub-param-1-value>:<sub-param-2-name>=<sub-param-2-value>%n%n")
					.append("or, if the order of the sub-parameters is known:%n%n")
					.append("<param-flag> <main-argument>:<sub-param-1-value>:<sub-param-2-value>%n%n")
					.append("In the first case, the order of the parameters is not important, whereas in the second, ")
					.append("short hand syntax, the order is critical. Mixing of explicit and short hand arguments is ")
					.append("allowed as long as the short hand parameters all come before any argument in explicit ")
					.append("form (otherwise the order is ambiguous). Note that the order of sub-parameters may change " )
					.append("between versions of CPSign so the explicit version should be preferred for setting up ")
					.append("scripts that can be used over a longer time.");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			sb.append("%n");

			addSubHeading(sb, "Example");
			toWrap = new StringBuilder("Consider the example of setting the scorer to be LinearSVC and setting the parameters cost and epsilon to 100 and 0.01, respectively. ")
					.append("Running explain scorer shows that cost is the first parameter and epsilon is the second, thus the following arguments are identical.");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			sb.append("%n%n");
			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--scorer")).append(' ').append(addArgumentStyle("LinearSVC:100:0.01")).append("%n");
			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--scorer")).append(' ').append(addArgumentStyle("LinearSVC:epsilon=0.01:cost=100")).append("%n");
			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--scorer")).append(' ').append(addArgumentStyle("LinearSVC:100:epsilon=0.01")).append("%n");


			// end main text
			sb.append("%n");

			String txt = replaceRunCMDStyles(sb.toString(), "explain scorer");

			CLIConsole.getInstance().println(AnsiRenderer.render(txt), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}
	}

	@Command(helpCommand=true,
			name = ExclusiveDatasetsInfo.SUB_NAME,
			description = ExclusiveDatasetsInfo.SUB_DESCRIPTION)
	public static class ExclusiveDatasetsInfo implements RunnableCmd {

		public final static String SUB_NAME = "exclusive-data";
		public final static String SUB_DESCRIPTION = "Information about @|italic exclusive|@ datasets";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Override
		public Integer call() throws Exception {
			StringBuilder sb = new StringBuilder();
			addHeading(sb, "EXCLUSIVE DATASETS");

			// main text
			StringBuilder toWrap = new StringBuilder("Compounds can be marked as only being part of the calibration or proper training set. This ")
					.append("is handled by removing these compounds from the 'normal' training set and giving them as ")
					.append("separate files to one of the two flags:%n%n")
					.append(CODE_EXAMPLE_LINE_START).append("--model-data          Mark compounds to proper training set%n")
					.append(CODE_EXAMPLE_LINE_START).append("--calibration-data    Mark compounds to calibration set%n%n")
					.append("The compounds given to these flags will always be included in the specified training set, ")
					.append("i.e. if specifying a set of compounds to --calibration-data, these compounds will always ")
					.append("be added to calibration set (in all splits/models).");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));

			// end main text
			sb.append("%n%n");

			// Replace parts that should have other ansi-style
			String text = addParamStyles(sb.toString(), "--model-data","--calibration-data");

			CLIConsole.getInstance().println(text, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}
	}


	@Command(helpCommand=true,
			name = MetricsInfo.SUB_NAME,
			description = MetricsInfo.SUB_DESCRIPTION)
	public static class MetricsInfo implements RunnableCmd {

		public final static String SUB_NAME = "metrics";
		public final static String SUB_DESCRIPTION = "Available performance metrics for models";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		private static enum Arg implements Named, Aliased {
			CP_REGRESSION ("cp-regression","conformal-regression"),
			CP_CLASSIFICATION ("cp-classification","conformal-classification"),
			VENN_ABERS ("venn-abers","cvap"),
			ALL ("all");

			public final String name;
			public final String[] aliases;

			private Arg(String name,String... aliases) {
				this.name=name;
				this.aliases = aliases;
			}

			public String toString() {
				return name;
			}

			@Override
			public String[] getAliases() {
				return aliases;
			}

			@Override
			public String getName() {
				return name;
			}
		}

		@Option(names = {"--info"},
				description = "Write info about metrics")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available metrics, either all of them using only '--list' or with any of the following options: "+
						"cp-regression, cp-classification, venn-abers|cvap, all",
						fallbackValue="all",
						paramLabel = ArgumentType.TEXT,
						arity="0..*",
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP
				)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(Arg.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(e.getMessage());
					}
				}
			}
		}
		private List<Arg> list;

		public MetricsInfo() {}

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();

			addHeading(text, SUB_NAME.toUpperCase());

			if (!info && (list==null || list.isEmpty())) {
				info = true;
				list = Arrays.asList(Arg.ALL);
			}
			if (info) {
				appendInfo(text);
			}
			if (list!=null && !list.isEmpty()) {
				appendList(text);
			}

			CLIConsole.getInstance().println(text.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			StringBuilder internal = new StringBuilder("There are several metrics that can be used to report statistics for a model, given a test set of withheld test examples. ")
					.append("Different models can be evaluated using a sub-set of the metrics, which is handled out of the box with most of CPSign commands. ")
					.append("However, certain tasks such as tuning parameters allows the user to set their desired metric, even though good defaults are given, ")
					.append("and as such, we here provide the full list of available metrics - and which predictor models they can be used for. ")
					.append("Note, however, that there are more metrics - these are the only ones that produce single values that allows for easy comparison between models.");

			sb.append(WordUtils.wrap(internal.toString(), CONSOLE_WIDTH));
			sb.append("%n");
		}

		private void appendList(StringBuilder sb) {

			for (Arg a : list) {
				switch (a) {
				case CP_CLASSIFICATION:
					addCPClass(sb);
					break;

				case CP_REGRESSION:
					addCPReg(sb);
					break;

				case VENN_ABERS:
					addVennABERS(sb);
					break;

				case ALL:
				default:
					addCPClass(sb);
					addCPReg(sb);
					addVennABERS(sb);
					break;
				}
			}

		}

		private void addCPClass(StringBuilder sb) {
			addSubHeading(sb, "Conformal Prediction Classification Metrics");
			TextTable table = getTable();
			for (Metric m : MetricFactory.getCPClassificationMetrics(false)) {
				if (m instanceof SingleValuedMetric)
					appendImplementation(table, m);
			}
			table.toString(sb);
		}

		private void addCPReg(StringBuilder sb) {
			addSubHeading(sb, "Conformal Prediction Regression Metrics");
			TextTable table = getTable();
			for (Metric m : MetricFactory.getACPRegressionMetrics()) {
				if (m instanceof SingleValuedMetric)
					appendImplementation(table, m);
			}
			table.toString(sb);
		}

		private void addVennABERS(StringBuilder sb) {
			addSubHeading(sb, "Venn-ABERS Probabilistic Metrics");
			TextTable table = getTable();
			for (Metric m : MetricFactory.getAVAPClassificationMetrics()) {
				if (m instanceof SingleValuedMetric)
					appendImplementation(table, m);
			}
			table.toString(sb);
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = NCMInfo.SUB_NAME,
			aliases = NCMInfo.SUB_ALIAS,
			description = NCMInfo.SUB_DESCRIPTION)
	public static class NCMInfo implements RunnableCmd {

		public static final String SUB_NAME = "ncm";
		public static final String SUB_ALIAS = "nonconf-measure";
		public static final String SUB_DESCRIPTION = "Available Nonconformity Measures (NCMs) and their parameters";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		private static enum Arg {
			REGRESSION, CLASSIFICATION, ALL;
			public String toString() {
				return name().toLowerCase();
			}
		}

		@Option(names = {"--info"},
				description = "Write info about NCMs")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available NCMs, either all of them using only '--list' or with any of the following options: "+
						"regression, classification, all",
						fallbackValue="all",
						paramLabel = ArgumentType.TEXT,
						arity="0..*",
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP
				)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(Arg.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(a);
					}
				}
			}
		}
		private List<Arg> list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();

			addHeading(text, "NONCONFORMITY MEASURES");
			if (!info && (list==null || list.isEmpty())) {
				info = true;
				list = Arrays.asList(Arg.ALL);
			}
			if (info) {
				appendInfo(text);
			}
			if (list!=null && !list.isEmpty()) {
				appendList(text);
			}

			CLIConsole.getInstance().println(text.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			String before_text = 
					"The Nonconformity Measure (NCM) decides the function that is used for calibrating the predictions made by a Conformal Predictor. "
							+ "See below for all available NCMS, it is possible to define your own NCM by using java ServiceLoader functionality (see more information on arosbio.com).";

			sb.append(WordUtils.wrap(before_text, CONSOLE_WIDTH));
			sb.append("%n");
		}

		private void appendList(StringBuilder sb) {
			Iterator<NCM> ncmsIter = FuzzyServiceLoader.iterator(NCM.class);
			List<NCM> regressionNCMs = new ArrayList<>();
			List<NCM> classificationNCMs = new ArrayList<>();
			while (ncmsIter.hasNext()) {
				NCM n = ncmsIter.next();
				if (n instanceof NCMRegression) {
					regressionNCMs.add(n);
				} else if (n instanceof NCMMondrianClassification) {
					classificationNCMs.add(n);
				}
			}
			Collections.sort(regressionNCMs, new IDSorter());
			Collections.sort(classificationNCMs, new IDSorter());
			list = CollectionUtils.getUnique(list);

			for (Arg a : list) {
				switch (a) {
				case CLASSIFICATION:
					doAppend(sb, "Classification NCMs", classificationNCMs);
					break;
				case REGRESSION:
					doAppend(sb, "Regression NCMs", regressionNCMs);
					break;
				case ALL:
				default:
					doAppend(sb, "Classification NCMs", classificationNCMs);
					doAppend(sb, "Regression NCMs", regressionNCMs);
					break;
				}
			}

		}

		private void doAppend(StringBuilder sb, String subHeader, List<NCM> ncms) {
			addSubHeading(sb, subHeader);
			TextTable table = getTable();
			for (NCM ncm : ncms) {
				appendImplementation(table, ncm);
			}
			table.toString(sb);
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = TuneParamsInfo.SUB_NAME,
			aliases = TuneParamsInfo.SUB_ALIAS,
			description = TuneParamsInfo.SUB_DESCRIPTION)
	public static class TuneParamsInfo implements RunnableCmd {

		public static final String SUB_NAME = "tune-parameters";
		public static final String SUB_ALIAS = "grid-params";
		public static final String SUB_DESCRIPTION = "Available parameters that can be tuned, and how to specify them";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		private static enum Arg implements Named, Aliased{
			PREDICTOR("predictor","conformal","venn-abers","cvap"),
			NCM("ncm","nonconformity", "nonconformity-score"),
			SCORER("scorer","algorithms","ml-algorithm","model","underlying-model"),
			SAMPLING("sampling","sampling-strategy"),
			ALL("all");

			public final String name;
			public final String[] aliases;

			private Arg(String name, String... aliases) {
				this.name = name;
				this.aliases = aliases;
			}

			public String toString() {
				return name;
			}

			@Override
			public String[] getAliases() {
				return aliases;
			}

			@Override
			public String getName() {
				return name;
			}
		}

		@Option(names = {"--info"},
				description = "Write info about tune parameters")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available tunable objects, either all of them using only '--list' or using any of the following options: "+
						"predictor, ncm, scorer, sampling, all",
						fallbackValue="all",
						paramLabel = ArgumentType.TEXT,
						arity="0..*",
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(Arg.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(a);
					}
				}
			}
		}
		private List<Arg> list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();

			addHeading(text, "TUNE PARAMETERS");

			if (!info && (list==null || list.isEmpty())) {
				info = true;
				list = Arrays.asList(Arg.ALL);
			}

			if (info) {
				appendInfo(text);
			}
			if (list!=null && !list.isEmpty()) {
				appendList(text);
			}

			String txt = replaceRunCMDStyles(text.toString(), "explain");

			CLIConsole.getInstance().println(txt, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			String beforeTxt = "Tune parameters are given dynamically to the -g/--grid parameter and can follow one of the following syntaxes;";
			sb.append(com.arosbio.commons.StringUtils.wrap(beforeTxt, CONSOLE_WIDTH));
			sb.append("%n%n");
			// Syntax 
			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("-g")).append(addArgumentStyle(" <KEY>=<VALUE>")).
			append("        e.g., ").append(addParamStyle("-g")).append(addArgumentStyle(" COST=5,10,100")).append("%n").
			append(CODE_EXAMPLE_LINE_START).append(addParamStyle("-g")).append(addArgumentStyle("=<KEY>=<VALUE>")).
			append("        e.g., ").append(addParamStyle("-g")).append(addArgumentStyle("=COST=5,10,100")).append("%n").
			append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--grid")).append(addArgumentStyle(" <KEY>=<VALUE>")).
			append("    e.g., ").append(addParamStyle("--grid")).append(addArgumentStyle(" COST=5,10,100")).append("%n").
			append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--grid")).append(addArgumentStyle("=<KEY>=<VALUE>")).
			append("    e.g., ").append(addParamStyle("--grid")).append(addArgumentStyle("=COST=5,10,100")).append("%n%n");

			sb.append(com.arosbio.commons.StringUtils.wrap(
					"When there is a default set of evaluation points (see info for each parameter) the '=<VALUES>' can be omitted;",
					CONSOLE_WIDTH));
			sb.append("%n%n");

			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("-g")).append(addArgumentStyle(" <KEY>"))
			.append("                e.g., ").append(addParamStyle("-g")).append(addArgumentStyle(" COST"))
			.append("%n%n")
			.append(com.arosbio.commons.StringUtils.wrap(
					"in order to use the default evaluation points. If more that one parameter should be searched in the grid, those are given as separate arguments to -g/--grid;",
					CONSOLE_WIDTH))
			.append("%n%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("-g")).append(addArgumentStyle(" COST=5,10,100")).append(' ')
			.append(addParamStyle("-g")).append(addArgumentStyle(" GAMMA=.001,0.01,0.1")).append("%n%n");
			String afterText ="Keys are case-insensitive. All parameters are not applicable to all implementations, "+ 
					"e.g., the kernel parameters are not available for Linear SVM models. Refer to usage help texts and the more detailed information "+ 
					"found from running explain for the particular parameter. Here follows the parameters that can be configured in the parameter grid, grouped per type and sorted alphabetically.";

			sb.append(com.arosbio.commons.StringUtils.wrap(afterText, CONSOLE_WIDTH));
			sb.append("%n");
		}

		private void appendList(StringBuilder sb) {
			for (Arg a : list) {
				switch (a) {
				case PREDICTOR:
					appendPred(sb);
					break;
				case NCM:
					appendNCM(sb);
					break;
				case SCORER:
					appendMLAlg(sb);
					break;
				case SAMPLING:
					appendSamplingStrat(sb);
					break;
				case ALL:
				default:
					appendPred(sb);
					appendNCM(sb);
					appendMLAlg(sb);
					appendSamplingStrat(sb);
					break;
				}
			}

		}

		private void appendPred(StringBuilder sb) {
			// Predictor level
			addSubHeading(sb, "Predictor parameters");
			List<Configurable> predictors = Arrays.asList(
					new ACPClassifier(),
					new ACPRegressor(),
					new TCPClassifier(),
					new AVAPClassifier());
			addAllCP(sb, predictors.iterator());
		}

		private void appendNCM(StringBuilder sb) {
			// NCM level
			addSubHeading(sb, "NCM parameters");
			addAllCP(sb, FuzzyServiceLoader.iterator(NCM.class));
		}

		private void appendMLAlg(StringBuilder sb) {
			// Scoring level
			addSubHeading(sb, "ML Algorithm parameters");
			addAllCP(sb, FuzzyServiceLoader.iterator(MLAlgorithm.class));
		}

		private void appendSamplingStrat(StringBuilder sb) {
			// Sampling strategy
			addSubHeading(sb, "Sampling strategy parameters");
			addAllCP(sb, FuzzyServiceLoader.iterator(SamplingStrategy.class));
		}

		private void addAllCP(StringBuilder sb, Iterator<? extends Configurable> config) {
			Map<String,ConfigParameter> params = new LinkedHashMap<>();
			while (config.hasNext()) {
				Configurable cls = config.next();
				tuneHelperGetUniqueCPs(params, cls.getConfigParameters());
			}

			List<String> paramNames = new ArrayList<>(params.keySet());
			Collections.sort(paramNames);

			TextTable table = getTable();
			for (String pName : paramNames) {
				appendConfigParam(table, params.get(pName));
				table.addEmptyRow();
				//				sb.append("%n");
			}
			table.toString(sb);
		}

		private void tuneHelperGetUniqueCPs(Map<String,ConfigParameter> prev, 
				Collection<ConfigParameter> newOnes) {
			for (ConfigParameter cp : newOnes) {
				String standardized = CLIProgramUtils.getPrefName(cp.getNames()).toLowerCase();
				if (! prev.containsKey(standardized)) {
					prev.put(standardized, cp);
				}
			}
		}

		private void appendConfigParam(TextTable tbl, ConfigParameter cp) {
			List<String> names = cp.getNames();
			addNameRow(tbl, names);
			if (cp instanceof Described) {
				addDescription(tbl, (Described)cp);
			}

			if (cp.getDefaultGrid()==null || cp.getDefaultGrid().isEmpty()) {
				tbl.addRowValues(LEFT_EVAL_POINTS, "None");
			} else {
				tbl.addRowValues(LEFT_EVAL_POINTS, StringUtils.join(cp.getDefaultGrid(),", "));
			}
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = DescriptorInfo.SUB_NAME,
			description = DescriptorInfo.SUB_DESCRIPTION)
	public static class DescriptorInfo implements RunnableCmd {

		public static final String SUB_NAME = "descriptors";
		public static final String SUB_DESCRIPTION = "Available chemical descriptors and their parameters";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		private enum DescriptorType implements Named, Aliased {
			CDK_PHYSCHEM("physchem","physicochem","physicochemical","cdk","cdk-physchem"), 
			FINGERPRINTS("fingerprints","fp"),
			OTHERS("others","signatures","cpsign"), 
			ALL("all");

			public final String name;
			public final String[] aliases;

			private DescriptorType(String name, String... aliases) {
				this.name = name;
				this.aliases = aliases;
			}
			public String toString() {
				return name;
			}
			@Override
			public String getName() {
				return name;
			}
			@Override
			public String[] getAliases() {
				return aliases;
			}

		}

		@Option(names = {"--info"},
				description = "Write info about descriptors")
		private boolean info;

		@Option(names= {"--list"},
				description = 
				"List available Descriptors, either all of them using only '--list' or using any of the following options: "+
						"physchem|physicochemical, others|cpsign, fingerprints|fp, all",
						fallbackValue="all",
						arity="0..*",
						paramLabel = ArgumentType.TEXT,
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP
				)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(DescriptorType.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(a);
					}
				}
			}
		}
		private List<DescriptorType> list;

		@Override
		public Integer call() throws Exception {

			StringBuilder text = new StringBuilder();
			addHeading(text, SUB_NAME);

			if ((list==null || list.isEmpty()) && !info) {
				info = true;
			}

			if (info) {
				appendInfo(text);
			}
			if (list!=null && !list.isEmpty()) {
				appendList(text);
			}

			String txt = replaceRunCMDStyles(text.toString(), "explain :-syntax", "explain "+SUB_NAME);

			CLIConsole.getInstance().println(txt, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {

			StringBuilder toWrap = new StringBuilder("CPSign can compute several molecular descriptors, and the user can add custom descriptors using Java ServiceLoader functionality if different ones are desired. ").
					append("The general usage is ");
			Ansi.ansi(toWrap).render(addParamStyle("--descriptors")+' '+addArgumentStyle("<descriptor"+NON_BREAKING_SPACE+"name>"));
			toWrap.append(". Some descriptors can have configurable parameters, these are set by the ':-syntax' (read more on explain :-syntax). ").
			append("Here are two equivalent arguments, using either the short-hand syntax or the explicit parameter names;");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			sb.append("%n%n");

			// Syntax example

			sb.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--descriptors")).append(addArgumentStyle(" signatures:1:2")).append("%n").
			append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--descriptors")).append(addArgumentStyle(" signatures:startHeight=1:endHeight=2")).append("%n%n");

			// More to wrap
			toWrap = new StringBuilder("The default descriptor is the Signatures molecular descriptor, and most ML algorithms in CPSign are tuned with appropriate defaults for being ").
					append("used in conjunction with this descriptor. Several of the CDK PhysChem descriptors are more time-consuming to compute and requires both tuning ").
					append("of the ML algorithm and possibly data transformations such as scaling and filtering to perform well.");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));

			sb.append("%n%nTo list each type of descriptor, write one of the following (after explain ")
			.append(SUB_NAME)
			.append(");%n%n")
			.append("--list cpsign | others (cpsign native descriptors)%n")
			.append("--list cdk | physchem | physicochemical%n")
			.append("--list fp | fingerprints%n")
			.append("--list all%n%n");

			//			sb.append("%n");
		}

		private void appendList(StringBuilder text) {

			Iterator<ChemDescriptor> allDescriptors = DescriptorFactory.getInstance().getDescriptors();
			List<CDKPhysChemWrapper> cdkDescriptors = new ArrayList<>();
			List<ChemDescriptor> otherDescriptors = new ArrayList<>();
			List<ChemDescriptor> fps = new ArrayList<>();

			while (allDescriptors.hasNext()) {
				ChemDescriptor d = allDescriptors.next();
				if (d instanceof CDKPhysChemWrapper)
					cdkDescriptors.add((CDKPhysChemWrapper) d);
				else if (d instanceof FPDescriptor)
					fps.add(d);
				else 
					otherDescriptors.add(d);
			}

			for (DescriptorType t : list) {
				switch (t) {
				case OTHERS:
					appendOthers(text, otherDescriptors);
					break;

				case FINGERPRINTS:
					appendFPs(text, fps);
					break;

				case CDK_PHYSCHEM:
					appendCDKPhysChem(text, cdkDescriptors);
					break;

				case ALL:
				default:
					appendOthers(text, otherDescriptors);
					appendCDKPhysChem(text, cdkDescriptors);
					break;
				}
			}

		}

		private void appendFPs(StringBuilder text, List<ChemDescriptor> ds) {
			addSubHeading(text, "Fingerprint (FP) descriptors");

			for (ChemDescriptor d : ds) {
				appendDescriptorInfo(text, d);
				text.append("%n");
			}
		}

		private void appendOthers(StringBuilder text, List<ChemDescriptor> ds) {
			addSubHeading(text, "CPSign native descriptors");

			for (ChemDescriptor d : ds) {
				appendDescriptorInfo(text, d);
				text.append("%n");
			}
		}

		private void appendCDKPhysChem(StringBuilder text, List<CDKPhysChemWrapper> ds) {
			// The special cases
			addSubHeading(text, "Special cases");
			TextTable table = getTable();
			table.addRowValues(LEFT_NAME,addNameStyle("all-cdk"));
			table.addRowValues(LEFT_DESCRIPTION,"Use all available CDKs 'IMolecularDescriptor' that @|italic does not require|@ 3D coordinates");
			table.addRowValues(LEFT_NAME,addNameStyle("all-cdk-3d"));
			table.addRowValues(LEFT_DESCRIPTION,"Use all available CDKs 'IMolecularDescriptor' that @|italic requires|@ 3D coordinates");

			table.toString(text);

			text.append("%n");

			addSubHeading(text, "Physicochemical descriptors");
			for (ChemDescriptor d : ds) {
				appendDescriptorInfo(text, d);
				text.append("%n");
			}
		}

		private void appendDescriptorInfo(StringBuilder text, ChemDescriptor d) {
			TextTable table = getTable();
			addNameRow(table, d);
			if (d instanceof Described)
				addDescription(table, (Described)d);

			if (d.requires3DCoordinates()) {
				table.addRowValues(LEFT_EXTRAS,"Requires 3D coordinates - either explicit in the input or these will be calculated when possible (molecules will fail if CDK cannot generate 3D coordinates automatically)");
			}

			addConfs(table, d);

			List<String> feats = d.getFeatureNames();

			if (!feats.isEmpty()) {

				for (int i=0; i<feats.size(); i++) {
					if (i==0)
						table.addRowValues(LEFT_FEATURES, i+": "+feats.get(i));
					else
						table.addRowValues("", i+": "+feats.get(i));
				}
			}
			table.toString(text);
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = TransformerInfo.SUB_NAME,
			description = TransformerInfo.SUB_DESCRIPTION)
	public static class TransformerInfo implements RunnableCmd {

		public static final String SUB_NAME = "transformations";
		public static final String SUB_DESCRIPTION = "Available data transformations";

		private enum TransformerType implements Named, Aliased{
			DUPLICATE_RESOLVE("duplicate","duplicate-resolve"),
			FILTER("filter","filters"),
			IMPUTATION("impute","imputation"),
			FEATURE_SELECT("feature-selection","attribute-selection"),
			FEATURE_SCALE("feature-scale","scaling"),
			VECTOR_SCALE("vector-scale"), 
			ALL("all");

			public final String name;
			public final String[] aliases;

			private TransformerType(String name, String... aliases) {
				this.name = name;
				this.aliases = aliases;
			}

			public String toString() {
				return name;
			}

			@Override
			public String[] getAliases() {
				return aliases;
			}

			@Override
			public String getName() {
				return name;
			}
		}

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Option(names = {"--info"},
				description = "Write info about Transformations")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available Transformations, either all of them using only '--list' or with any of the following options: "+
						"duplicate-resolve, filter, imputation, feature-select, feature-scale, vector-scale, all",
						fallbackValue="all",
						arity="0..*",
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP,
						paramLabel = ArgumentType.TEXT
				)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(TransformerType.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(a);
					}
				}
			}
		}
		private List<TransformerType> list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();
			addHeading(text, SUB_NAME);

			if ((list==null || list.isEmpty()) && !info) {
				info = true;
				list = Arrays.asList(TransformerType.ALL);
			}

			if (info)
				appendInfo(text);
			if (list!=null && !list.isEmpty())
				appendList(text);

			String txt = addParamStyles(text.toString(), "--transform");
			txt = replaceRunCMDStyles(txt, "explain syntax");

			CLIConsole.getInstance().println(txt, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			String before_text = new StringBuilder("There are many transformations and modifications needed prior to modeling to achieve good model performance, ")
					.append("e.g. feature scaling, feature selection and filtration of outliers. Although CPSign is not intended for extensive changes ")
					.append("to data, some basic transformations are supported and listed below and users can add custom transformers using Java ")
					.append("if that would be desired. The transformers parameter follows the :-syntax (run explain syntax for more info). E.g.;%n%n")

					.append(CODE_EXAMPLE_LINE_START).append("--transform ").append(addArgumentStyle(new LabelRangeFilter().getName()+ ":min=-10:max=10")).append("%n%n")

					.append("Several transformations can be applied, and they are applied in the sequence given (i.e. (1) filter out outliers, (2) remove duplicates, (3) reduce dimensionality, (4) scale features). ")
					.append("These transformations can either be given as separate arguments, or at the same time (only the order is important), e.g.;%n%n")
					// Multi-transformer example
					.append(CODE_EXAMPLE_LINE_START).append("--transform ").append(addArgumentStyle("A B")).append("%n")
					.append(CODE_EXAMPLE_LINE_START).append("--transform ").append(addArgumentStyle("A")).append(' ').append("--transform ").append(addArgumentStyle("B"))
					.toString();

			sb.append(com.arosbio.commons.StringUtils.wrap(before_text, CONSOLE_WIDTH));
			sb.append("%n%nwould be identical.%n%n");
		}

		private void appendList(StringBuilder text) {
			Iterator<Transformer> allTrans = FuzzyServiceLoader.iterator(Transformer.class);

			List<Transformer> dups = new ArrayList<>();
			List<Transformer> filters = new ArrayList<>();
			List<Transformer> imputers = new ArrayList<>();
			List<Transformer> reducers = new ArrayList<>();
			List<Transformer> scalers = new ArrayList<>();
			List<Transformer> vecScalers = new ArrayList<>();

			List<Transformer> others = new ArrayList<>();

			while (allTrans.hasNext()) {
				Transformer t = allTrans.next();
				if (t instanceof DuplicatesResolverTransformer)
					dups.add(t);
				else if (t instanceof Filter)
					filters.add(t);
				else if (t instanceof Imputer)
					imputers.add(t);
				else if (t instanceof FeatureSelector)
					reducers.add(t);
				else if (t instanceof FeatureScaler)
					scalers.add(t);
				else if (t instanceof VectorScaler)
					vecScalers.add(t);
				else 
					others.add(t);
			}

			for (TransformerType t : list) {
				switch (t) {
				case DUPLICATE_RESOLVE:
					appendTransformerSection(text,"Duplicate resolving transformers", dups);
					break;

				case FILTER:
					appendTransformerSection(text,"Filter transformers", filters);
					break;

				case FEATURE_SCALE:
					appendTransformerSection(text,"Feature scaling transformers", scalers);
					break;

				case FEATURE_SELECT:
					appendTransformerSection(text,"Feature selection transformers", reducers);
					break;

				case VECTOR_SCALE:
					appendTransformerSection(text, "Vector scaling transformers",vecScalers);
					break;

				case IMPUTATION:
					appendTransformerSection(text,"Imputation transformers", imputers);
					break;

				case ALL:
				default:
					appendTransformerSection(text,"Duplicate resolving transformers", dups);
					appendTransformerSection(text,"Filter transformers", filters);
					appendTransformerSection(text,"Imputation transformers", imputers);
					appendTransformerSection(text,"Feature selection transformers", reducers);
					appendTransformerSection(text,"Feature scaling transformers", scalers);
					appendTransformerSection(text,"Vector scaling transformers",vecScalers);
					if (!others.isEmpty())
						appendTransformerSection(text,"Other transformers", others);
					break;
				}
			}
		}

		private void appendTransformerSection(StringBuilder text, String subHeading, List<Transformer> list) {
			addSubHeading(text, subHeading);
			TextTable table = getTable();
			for (Transformer d : list) {
				appendTransformerInfo(table, d);
				table.addEmptyRow();
			}
			table.toString(text);
		}

		private void appendTransformerInfo(TextTable text, Transformer d) {

			addNameRow(text, d);
			String right = "-";
			if (d.applicableToRegressionData() && d.applicableToClassificationData()) {
				right = "regression & classification data";
			} else if (d.applicableToClassificationData()) {
				right = "classification data";
			} else if (d.applicableToRegressionData()) {
				right = "regression data";
			} 
			text.addRowValues(LEFT_APPLIES_TO, right);

			addDescription(text, d);
			addConfs(text, d);

		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand=true,
			name = ChemFiltersInfo.SUB_NAME,
			aliases = ChemFiltersInfo.SUB_ALIAS,
			description = ChemFiltersInfo.SUB_DESCRIPTION)
	public static class ChemFiltersInfo implements RunnableCmd {

		public static final String SUB_NAME = "chem-filters";
		public static final String SUB_ALIAS = "hac";
		public static final String SUB_DESCRIPTION = "Available filters based on chemical structure and their parameters";
		

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();
			addHeading(text, SUB_NAME);
			
			StringBuilder toWrap = new StringBuilder("Apply one or several filters based on chemical structure, i.e. to filter out too small or too large molecules based on their Heavy Atom Count (HAC) or molecular mass. ")
				.append("One example is the HAC filter, where sub-parameters are set using the :-syntax, e.g.;");
			text.append(
				com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH))
				.append("%n%n")
				.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--chem-filter")).append(addArgumentStyle(" HAC:min=6:max=60")).append("\t\tKeep only molecules with minimum 6 and maximum 60 heavy atoms%n");

			addSubHeading(text, "Available filters");
			TextTable table = getTable();
			Iterator<ChemFilter> iter = ServiceLoader.load(ChemFilter.class).iterator();
			
			while(iter.hasNext()){
				ChemFilter filter = iter.next();
				appendImplementation(table, filter);
			}
			
			table.toString(text);

			CLIConsole.getInstance().println(text.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}
		@Override
		public String getName() {
			return SUB_NAME;
		}

		

	}

	@Command(helpCommand=true,
			name = MLAlgInfo.SUB_NAME,
			aliases = MLAlgInfo.SUB_ALIAS,
			description = MLAlgInfo.SUB_DESCRIPTION)
	public static class MLAlgInfo implements RunnableCmd {

		public static final String SUB_NAME = "scorer";
		public static final String SUB_ALIAS = "ml-algorithms";
		public static final String SUB_DESCRIPTION = "Available ML algorithms and their parameters";

		private enum ArgType {
			REGRESSION, CLASSIFICATION, ALL;
			public String toString() {
				return name().toLowerCase();
			}
		}

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Option(names = {"--info"},
				description = "Get info about the algorithms")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available algorithms, either all of them using only '--list' or with any of the following options: "+
						"regression, classification, all",
						fallbackValue="all",
						arity="0..*",
						paramLabel = ArgumentType.TEXT,
						split = ParameterUtils.SPLIT_WS_COMMA_REGEXP
				)
		private void addArgs(List<String> input) {
			if (input == null || input.isEmpty())
				return;
			if (list==null) {
				list = new ArrayList<>();
			}
			for (String in : input) {
				List<String> args = MultiArgumentSplitter.split(in);
				for (String a : args) {
					try {
						list.add(new FuzzyMatcher().match(EnumSet.allOf(ArgType.class), a));
					} catch (Exception e) {
						throw new TypeConversionException(a);
					}
				}
			}
		}
		private List<ArgType> list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();
			addHeading(text, "ML ALGORITHMS");

			if ((list==null || list.isEmpty()) && !info) {
				info = true;
				list = Arrays.asList(ArgType.ALL);
			}

			if (info)
				appendInfo(text);
			if (list!=null && !list.isEmpty())
				appendList(text);

			String txt = replaceRunCMDStyles(text.toString(), "explain syntax");

			CLIConsole.getInstance().println(txt, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			StringBuilder toWrap = new StringBuilder("CPSign currently supports a few learning algorithms implemented in the LIBSVM and LIBLINEAR packages. ")
					.append("These algorithms have parameters that can be altered by using the :-syntax (explain syntax), e.g.;");
			sb.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			sb.append("%n%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--scorer")).append(' ').append(addArgumentStyle(LinearSVC.ALG_NAME+":100")).append("%n")
			.append(CODE_EXAMPLE_LINE_START).append(addParamStyle("--scorer")).append(' ').append(addArgumentStyle(LinearSVC.ALG_NAME+":C=100")).append("%n%n");
			sb.append(com.arosbio.commons.StringUtils.wrap("Both these lines choses the LinearSVC algorithm and sets the SVM-Cost value to 100.", CONSOLE_WIDTH)).append("%n");	

		}

		private void appendList(StringBuilder text) {
			Iterator<MLAlgorithm> algsIter = FuzzyServiceLoader.iterator(MLAlgorithm.class); 
			List<MLAlgorithm> classifiers = new ArrayList<>();
			List<MLAlgorithm> regressors = new ArrayList<>();
			while (algsIter.hasNext()) {
				MLAlgorithm alg = algsIter.next();
				if (alg instanceof Classifier) {
					classifiers.add(alg);
				} else if (alg instanceof Regressor) {
					regressors.add(alg);
				}
			}

			Collections.sort(classifiers, new IDSorter());
			Collections.sort(regressors, new IDSorter());

			for (ArgType a : list) {
				switch (a) {
				case CLASSIFICATION:
					appendClassifiers(text, classifiers);
					break;
				case REGRESSION:
					appendRegressors(text, regressors);
					break;
				case ALL:
				default:
					appendClassifiers(text, classifiers);
					appendRegressors(text, regressors);
					break;
				}
			}



		}

		private void appendClassifiers(StringBuilder text, List<MLAlgorithm> classifiers) {
			addSubHeading(text, "Classification algorithms");
			TextTable table = getTable();
			for (MLAlgorithm clf : classifiers) {
				appendMLInfo(table, clf);
				table.addEmptyRow();
			}
			table.toString(text);
		}

		private void appendRegressors(StringBuilder text, List<MLAlgorithm> regressors) {
			addSubHeading(text, "Regression algorithms");
			TextTable table = getTable();
			for (MLAlgorithm reg : regressors) {
				appendMLInfo(table, reg);
				table.addEmptyRow();
			}
			table.toString(text);
		}

		private void appendMLInfo(TextTable table, MLAlgorithm ml) {

			addNameRow(table, ml);
			addIDRow(table, ml);
			addDescription(table, ml);
			if (ml instanceof MultiLabelClassifier) {
				table.addRowValues(LEFT_EXTRAS, SUPPORTS_MULTICLASS);
			}
			if (ml instanceof PseudoProbabilisticClassifier) {
				table.addRowValues(LEFT_EXTRAS, PROBABILITY_ML);
			}
			addConfs(table, ml);

		}

		@Override
		public String getName() {
			return SUB_NAME;
		}
	}

	/*
	private String compileMLAlgorithmExplain() {
		StringBuilder text = new StringBuilder();
		centerHeading(text, "ML ALGORITHMS");
		String before_text = 
				"CPSign currently supports a few learning algorithms implemented in the LIBSVM and LIBLINEAR packages. These algorithms have parameters that can be altered by using the ':'-syntax (explain syntax), E.g.:%n"
						+ "\"[algorithm_name]:[parameter_name]=[parameter_value]:\"%nOR if supplying the parameters in the listed order using the short hand syntax:%n"
						+ "\"[algorithm_name]:[parameter_1_value]:[parameter_2_value]...\"%n"
						+ "E.g. --scorer "+LinearSVC.ALG_NAME+":100  (change the cost-value to 100)%n";

		text.append(WordUtils.wrap(before_text, textWidth));


		Iterator<MLAlgorithm> algsIter = FuzzyServiceLoader.iterator(MLAlgorithm.class); 
		List<MLAlgorithm> classifiers = new ArrayList<>();
		List<MLAlgorithm> regressors = new ArrayList<>();
		while (algsIter.hasNext()) {
			MLAlgorithm alg = algsIter.next();
			if (alg instanceof Classifier) {
				classifiers.add(alg);
			} else if (alg instanceof Regressor) {
				regressors.add(alg);
			}
		}

		text.append("%n");
		centerSubHeading(text, "Regression algorithms");
		for (MLAlgorithm reg : regressors) {
			appendMLInfo(text, reg);
			text.append("%n");
		}

		centerSubHeading(text, "Classification algorithms");
		for (MLAlgorithm clf : classifiers) {
			appendMLInfo(text, clf);
			text.append("%n");
		}

		return text.toString();
	}

	private void appendMLInfo(StringBuilder text, MLAlgorithm d) {

		appendNames(text, d);
		appendDescription(text, d);
		if (d instanceof MultiLabelClassifier) {
			text.append(SUPPORTS_MULTICLASS);
			text.append("%n");
		}
		if (d instanceof PseudoProbabilisticClassifier) {
			text.append(PROBABILITY_ML);
			text.append("%n");
		}

		appendConfigStuff(text, d.getConfigParameters());

	} 
	 */

	@Command(helpCommand=true,
			name = SamplingStratInfo.SUB_NAME,
			aliases = SamplingStratInfo.SUB_ALIAS,
			description = SamplingStratInfo.SUB_DESCRIPTION)
	public static class SamplingStratInfo implements RunnableCmd {

		public static final String SUB_NAME = "sampling";
		public static final String SUB_ALIAS = "sampling-strategy";
		public static final String SUB_DESCRIPTION = "Available sampling strategies for aggregated predictor models";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Option(names = {"--info"},
				description = "Get info about the sampling strategies")
		private boolean info;

		@Option(names= {"--list"},
				description = "List available strategies."
				)
		private boolean list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();
			addHeading(text, "SAMPLING STRATEGIES");

			if (!list && !info) {
				info = true;
				list = true;
			}

			if (info)
				appendInfo(text);
			if (list)
				appendList(text);


			CLIConsole.getInstance().println(text.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			String before_text = 
					"There are many different sampling strategies that can be used for the aggregated type predictors (i.e. not TCP). "
							+ "Here are the currently available ones and their respective parameters.";

			sb.append(com.arosbio.commons.StringUtils.wrap(before_text, CONSOLE_WIDTH));
			sb.append("%n");
		}

		private void appendList(StringBuilder text) {
			addSubHeading(text, "Available strategies");

			Iterator<SamplingStrategy> ssIterator = FuzzyServiceLoader.iterator(SamplingStrategy.class);

			TextTable table = getTable();
			while (ssIterator.hasNext()) {
				SamplingStrategy ss = ssIterator.next();
				appendImplementation(table, ss);
			}
			table.toString(text);
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}
	}

	@Command(helpCommand = true,
			name = TestSamplingInfo.SUB_NAME,
			aliases = TestSamplingInfo.SUB_ALIAS,
			description = TestSamplingInfo.SUB_DESCRIPTION)
	public static class TestSamplingInfo implements RunnableCmd {

		public static final String SUB_NAME = "testing";
		public static final String SUB_ALIAS = "test-strategy";
		public static final String SUB_DESCRIPTION = "Available testing strategies";

		static {
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME);
		}

		@Option(names = {"--info"},
				description = "Get info about the sampling strategies"
				)
		private boolean info;

		@Option(names= {"--list"},
				description = "List available strategies"
				)
		private boolean list;

		@Override
		public Integer call() throws Exception {
			StringBuilder text = new StringBuilder();
			addHeading(text, "TEST STRATEGIES");

			if (!list && !info) {
				info = true;
				list = true;
			}

			if (info)
				appendInfo(text);
			if (list)
				appendList(text);


			CLIConsole.getInstance().println(text.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		private void appendInfo(StringBuilder sb) {
			String before_text = 
					"There are a few available testing strategies in CPSign and they have a few parameters that can be tweaked (e.g. stratification and number of repeats). "+
							"Choosing the best testing strategy is problem-dependent, e.g., depending on how much data is available, potential class imbalance etc.";

			sb.append(WordUtils.wrap(before_text, CONSOLE_WIDTH));
			sb.append("%n");
		}

		private void appendList(StringBuilder text) {
			addSubHeading(text, "Available strategies");

			Iterator<TestingStrategy> ssIterator = FuzzyServiceLoader.iterator(TestingStrategy.class);

			TextTable table = getTable();
			while (ssIterator.hasNext()) {
				TestingStrategy ss = ssIterator.next();
				appendImplementation(table, ss);
				table.addEmptyRow();
			}
			table.toString(text);
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand = true,
			name = ChemFormatInfo.SUB_NAME,
			aliases = ChemFormatInfo.SUB_ALIAS,
			description = ChemFormatInfo.SUB_DESCRIPTION
			)
	public static class ChemFormatInfo implements RunnableCmd {

		public static final String SUB_NAME = "chemical-files";
		public static final String SUB_ALIAS = "chem-formats";
		public static final String SUB_DESCRIPTION = "Supported file formats and how they are specified";

		static{
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME).getTextWidth();
		}

		@Override
		public Integer call() throws Exception {
			StringBuilder mainText = new StringBuilder();

			addHeading(mainText, "CHEMICAL FILE FORMATS");

			//   ------------------------------------------------------------------------------------------
			mainText.append("Chemical file formats currently supported: @|bold SDF|@, @|bold CSV|@ and @|bold JSON|@.%n");
			addSubHeading(mainText, "SYNTAX");

			mainText.append("Syntax 1:  @|italic,red <flag> |@@|italic,blue <format> |@@|italic,green [optional arguments] |@@|italic,yellow <URI | path>|@%n");
			mainText.append("Syntax 2:  @|italic,red <flag> |@@|italic,blue <format>|@@|italic,green [:key=value] |@@|italic,yellow <URI | path>|@%n%n");

			TextTable syntaxTable = getTable();
			syntaxTable.addRowValues(ANSI.text("@|red,italic <flag>|@"),
					ANSI.text("Dependent on the program you are running, e.g., --train-data"));
			syntaxTable.addRowValues(ANSI.text("@|blue,italic <format>|@"),
					ANSI.text("Specify file format (see the allowed ones underneath)"));
			syntaxTable.addRowValues(ANSI.text("@|green,italic [arguments]|@"),
					ANSI.text("Optional list of extra arguments, separated by a space (or new line if "+
							"specified in a file) or by ':' when using :-syntax. The available arguments depend on the file format being used (see "+
							"further info below). Each argument is specified in the syntax; " +
							"key=value, e.g., \"delim=\\t\""));
			syntaxTable.addRowValues(ANSI.text("@|yellow,italic <URI | path>|@"),
					ANSI.text("A relative or absolute path, or a full URI (Unified Resource Identifier)"));

			// Add the text table
			syntaxTable.toString(mainText);

			addSubHeading(mainText, "FORMATS");

			TextTable formatTable = getTable();
			appendImplementation(formatTable, new SDFile(null));
			appendImplementation(formatTable, new CSVFile(null));
			appendImplementation(formatTable, new JSONFile(null));

			formatTable.toString(mainText);

			String txt = addParamStyles(mainText.toString(),"--train-data");
			txt = Ansi.ansi().render(txt).toString();

			CLIConsole.getInstance().println(txt, PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;
		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}

	@Command(helpCommand = true,
			name = ListSyntaxInfo.SUB_NAME,
			aliases = ListSyntaxInfo.SUB_ALIAS,
			description = ListSyntaxInfo.SUB_DESCRIPTION
			)
	public static class ListSyntaxInfo implements RunnableCmd {

		public static final String SUB_NAME = "list-syntax";
		public static final String SUB_ALIAS = "list-numbers";
		public static final String SUB_DESCRIPTION = "Syntax for specifying multiple numbers";

		static{
			CLIConsole.getInstance().setRunningCMD(CMD_NAME + ' ' +SUB_NAME).getTextWidth();
		}

		@Option(names= {"--test"}, 
				description="Give an optional test-text that will be checked and enumerated according to this syntax")
		private String test;

		@Override
		public Integer call() throws Exception {
			CLIConsole cons = CLIConsole.getInstance();

			if (test!=null && !test.isEmpty()) {
				List<Double> l = null;
				try {
					l = new ListOrRangeConverter().convert(test);
				} catch (Exception e) {
					cons.printStdErr("Invalid input: " + e.getMessage(), PrintMode.SILENT);
					return ExitStatus.USER_ERROR.code;
				}
				StringBuilder sb = new StringBuilder();
				for (int i=0; i<l.size(); i++) {
					sb.append(l.get(i));
					if (i<l.size()-1)
						sb.append(", ");
				}

				cons.println("Resulting list of numbers: %s", PrintMode.SILENT,sb.toString());

				return 0;
			}

			StringBuilder mainText = new StringBuilder();

			addHeading(mainText, "LIST NUMBERS SYNTAX");

			// main text
			StringBuilder toWrap = new StringBuilder()
					.append("Specifying a list of numbers, e.g. when choosing multiple confidence values or specifying which parameters to try out in tuning, can be done in several different ways. ")
					.append("The first and most straight forward way is to list them explicitly, but this can be tedious if you wish to specify several numbers. ")
					.append("An alternative way is to specify them using a syntax of ")
					.append(addArgumentStyle("<start>:<stop>[:step]")).append(" or ").append(addArgumentStyle("<start>:<stop>[:step][:base]"))
					.append(" where cpsign will generate a list automatically. Note ")
					.append("that you can try this syntax and see the generated list by running e.g. ").append(addRunCMDStyle("explain "+SUB_NAME + " --test b10:1:5"))
					.append(". Further note that it is fully possible to combine the syntax for enumerating values and listing explicit numbers, like e.g. ").append(addArgumentStyle("b=2:-10:-2,10,100"))
					.append(" where an enumeration is performed using b=2:-10:-2 and then further use the explicit values 10 and 100.");
			mainText.append(com.arosbio.commons.StringUtils.wrap(toWrap.toString(), CONSOLE_WIDTH));
			mainText.append("%n");

			// Syntax
			addSubHeading(mainText, "Syntax");

			// Without base
			mainText.append(addArgumentStyle("<start>:<stop>[:step]")).append("%n");
			TextTable tt = getTable(10);
			tt.addRowValues("",
					"Two or more numbers can be given, separated by a colon (:) character, which will be interpreted as a starting value and stopping value. All values between these "+
							"will then be enumerated, using the "+addArgumentStyle("step") + " (default 1), e.g., 0:10 will be 0, 1, 2, ..., 10. When three numbers are given, the third is interpreted as "
							+ "the 'step' between succeeding numbers in the enumeration, .e.g, 0:10:2 will enumerate the list 0, 2, 4, 6, 8, 10. See more examples below."
					);
			tt.toString(mainText);

			// With base
			mainText.append("%n").append(addArgumentStyle("<start>:<stop>[:step][:base]")).append("%n");
			tt = getTable(10);
			tt.addRowValues("",
					"In a similar fashion as for the " + addArgumentStyle("<start>:<stop>[:step]") + " above, a 'base' can be added. If a base is given, the enumerated values given of the 'start:stop[:step]' "
							+ "section will be applied as exponents to the 'base'. The base can be given in any position of the values separated by the colon character and is recognized by writing 'b=<number>' "
							+ "or 'base=<number>' (case insensitive). E.g. base=2:1:3 will enumerate the list '1, 2, 3' of the 'start:stop' portion and then apply these values as exponent and "
							+ "result in the final list 2^1, 2^2, 2^3. See more examples below. This is useful, e.g., when giving tuning parameters that can be very small or large, or with an exponential "
							+ "spacing between tested values."
					);
			tt.toString(mainText);

			// Examples
			addSubHeading(mainText, "Examples");

			mainText.append(addArgumentStyle("0:10")).append("\t\t0, 1, .., 10%n")
			.append(addArgumentStyle("0:10:2")).append("\t\t0, 2, .., 10%n")
			.append(addArgumentStyle("base=10:0:5:2")).append("\t1, 100, 10000  Note: the stop-value (5) is not included%n")
			.append(addArgumentStyle("0.01:0.99:0.01")).append("\t0.01, 0.02, .., 0.99  E.g. specifying many confidence levels%n")
			.append(addArgumentStyle("-10:-2:2:b=2")).append("\t2^-10, 2^-8, .., 2^-2  E.g. specifying gamma values in tune%n")
			.append(addArgumentStyle("0:10:2,100,1000")).append("\t0, 2, .., 10, 100, 1000  Combine enumeration and explicit numbers%n%n");

			cons.println(mainText.toString(), PrintMode.NORMAL);

			return ExitStatus.SUCCESS.code;

		}

		@Override
		public String getName() {
			return SUB_NAME;
		}

	}



}
