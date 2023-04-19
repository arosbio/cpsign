package com.arosbio.cpsign.app.params.mixins;

import picocli.CommandLine.Option;

public class ListFailedRecordsMixin {
    
    @Option(names = {"--list-failed"},
    description = "List @|bold all|@ failed molecules, such as invalid records, molecules removed due to Heavy Atom Count or failures at descriptor calculation. "+
    "The default is otherwise to only list the summary of the number of failed records.")
    public boolean listFailedRecords = false;

}
