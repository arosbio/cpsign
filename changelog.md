# Change log for CPSign versions

### 2.0.0 RC7 :
- Massive improvement in runtime for duplicate resolving transformations - e.g. new cpLogD with 2.2M compounds took 19.5 hours with old algorithm and now less than 1 second using the same machine. 
- Update slf4j and logback deps due to dependabot alert for new vulnerability. 

### 2.0.0 RC6 :
#### Potentially breaking changes
- Enforce consistent number of columns in CSV files, will cause failures unless every line has the same number of columns as the header row. This may be a breaking change for some files but will likely produce more robust results.
- Changed the default delimiter of `CSV` to "`,`", from previously being tab (i.e."`\t`"). For CLI users there is now an alias `TSV` for CSV input - which has the default delimiter set to tab.

#### Bug fixes and improvements
- Update CDK version 2.8 to 2.9. Reduces the 'fatjar' size as well as removes some vulnerabilities flagged by github build system.
- Resolved bug when using the `--echo` flag from the CLI - which apparently changed the internal state of the picocli parser and caused issues unless it was placed as the last parameter.
- Added API methods for getting `FeatureInfo` - i.e. mappings from feature index to descriptive statistics as well as check for missing values.
- Improved the `list-features` CLI method to include descriptive statics of each feature as well as checking for missing values. Information written in `--verbose` mode.
- Improved the config selection from the CLI as well as fixing general spelling errors. 


### 2.0.0 RC5 :
- Solved major bug for generating prediction images using the CLI, which was completely turned off but should now work as expected.
- Improved error messages from the CLI to indicate the parameter flags correctly. Solved minor bugs for that lookup as well as extended it for parameters based on method annotations.

### 2.0.0 RC4 :
- Solved minor bug with swapped `get[Min|Max]SplitIndex` values for the `LOOSplitter` class.
- Solved bug with filtering of property files, which caused the project version not being correctly injected into `confai.properties` which is used for keeping track of the CPSign version. Thus the version could not be found either at API or CLI level.
- Moved the [encrypt-api](encrypt-api/README.md) to a separate project which simplifies and decouples that API vs the remaining code. Now the other components can rely on a fixed version (as it is not likely to change often or at all) and not have issues with incompatibility due to transitive dependencies to the `parent` project pom (i.e. encrypt-api would rely on e.g. the 2.0.0-rc3 version whilst the remaining cpsign modules will rely on a later version of the parent pom). 
- Added two callback interfaces for progress information and monitoring while running `GridSearch` jobs, one that only get information (`ProgressCallback`) and the other one (`ProgressMonitor`) that can choose to exit the grid search e.g. due to time-restraints. Note that these are synchronous calls at this point (CPSign is not multithreaded otherwise so no use adding that for this one task). Added an implementation tailored for the CLI so that information is printed in the terminal (e.g. " - Finished 2/10 grid points") to provide more feedback about the progress so e.g. too long jobs can be aborted earlier. 

### 2.0.0 ~~RC2~~ RC3 :
**Note:** due to a minor mistake in updating the encrypt-api version to the updated 2.0.1 version in the test-utils module, the build failed at GitHub and RC1 is amended directly by release candidate 3 (RC3).
- Introduced a common `ProgressTracker` that is shared among the processing stack while loading and processing new data. This way a specific threshold of "number of allowed failures" can be adhered to instead of having individual thresholds for each level of processing. This trickles down into `--early-termination` of the CLI now stopping when it should.
 - Fixed bug where `MolAndActivityConverter` would stop when encountering the maximum specified number of conversion failures, and do it silently without causing the expected exception. This bug was also present when using the CLI `precompute` program - and is now solved.
 - Remove the possibility to use clustered POSIX flags to the CLI, which resulted in strange behavior that was hard to find the cause of.
 - Update the Fuzzy matching logics to (hopefully) improve matching of parameters to the CLI.
 - Update dependency version of Picocli.
 - Added logics for trying to deduce the user error to improve error messages to CLI users, specifically for running `precompute`. Also streamlined the output to be more coherent among `predict`, `predict-online` and `validate`. 
 - Extracted prediction logics of `predict` and `predict-online` into common utility class, again to streamline the behavior seen by the user.
 - Added more information to the `model-info` program of the CLI, i.e. in case a model can be used for generating prediction images and the number of attributes/features used.
 - Added new interface `ChemFilter` that replaces the `[set|get]MinHAC` of a `ChemDataset` (API level). Added two implementations; `HACFilter` (allows to set both minimum and maximum HAC) and `MolMassFilter` (min/max molecular mass). At the CLI level the `--min-hac` is now deprecated and will give a deprecation message if used, and is replaced by the `--chem-filters` parameter that allows to set multiple filters. To keep consistent with earlier versions there is a default `HACFilter` using the same minimum heavy atom count of 5. In order to not apply any chemical filters the user can give `--chem-filters none`. 
 - At CLI level there is a new `explain chem-filters` that give some general information as well as list the currently available filters and sub-parameters of these.
 - Improved aggregation of metrics from multi-test splits (i.e. when computing mean +/- standard deviation), also improve output from CLI to the user (i.e. remove the wording "Calibration plot" when only having a single confidence level).
