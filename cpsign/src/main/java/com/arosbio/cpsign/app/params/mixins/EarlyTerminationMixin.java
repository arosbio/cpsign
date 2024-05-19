package com.arosbio.cpsign.app.params.mixins;

import com.arosbio.cpsign.app.utils.ParameterUtils;
import com.arosbio.cpsign.app.utils.ParameterUtils.ArgumentType;

import picocli.CommandLine.Option;

public class EarlyTerminationMixin {

    @Option(names= {"--early-termination","--early-termination-after"}, 
            description = "Early termination stops parsing data once passing this number of failed records and fails execution of the program. "
                + "Specifying a value less than 0 means there is no early termination and parsing will continue until the input file is read completely.%n"
                + ParameterUtils.DEFAULT_VALUE_LINE,
            paramLabel = ArgumentType.INTEGER,
            defaultValue = "-1")
    public int maxFailuresAllowed = -1;
    
}
