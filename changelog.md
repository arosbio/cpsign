# Change log for CPSign versions

### 2.0.0 RC2 :
- Introduced a common `ProgressTracker` that is shared among the processing stack while loading and processing new data. This way a specific threshold of "number of allowed failures" can be adhered to instead of having individual thresholds for each level of processing. This trickles down into `--early-termination` of the CLI now stopping when it should.
 - Fixed bug where `MolAndActivityConverter` would stop when encountering the maximum specified number of conversion failures, and do it silently without causing the expected exception. This bug was also present when using the CLI `precompute` program - and is now solved.
 - Remove the possibility to use clustered POSIX flags to the CLI, which resulted in strange behaviour that was hard to find the cause of.
 - Update the Fuzzy matching logics to (hopefully) improve matching of parameters to the CLI.
 - Update dependency version of Picocli.
 - Added logics for trying to deduce the user error to improve error messages to CLI users, specifically for running `precompute`. Also streamlined the output to be more coherent among `predict`, `predict-online` and `validate`. 
 - Extracted prediction logics of `predict` and `predict-online` into common utility class, again to streamline the behaviour seen by the user.
 - Added more information to the `model-info` program of the CLI, i.e. in case a model can be used for generating prediction images and the number of attributes/features used.
 - Added new interface `ChemFilter` that replaces the `[set|get]MinHAC` of a `ChemDataset` (API level). Added two implementations; `HACFilter` (allows to set both minimum and maximum HAC) and `MolMassFilter` (min/max molecular mass). At the CLI level the `--min-hac` is now deprecated and will give a deprecation message if used, and is replaced by the `--chem-filters` parameter that allows to set multiple filters. To keep consistent with earlier versions there is a default `HACFilter` using the same minimum heavy atom count of 5. In order to not apply any chemical filters the user can give `--chem-filters none`. 
 - At CLI level there is a new `explain chem-filters` that give some general information as well as list the currently available filters and sub-parameters of these.
 - Improved aggregation of metrics from mulit-test splits (i.e. when computing mean +/- standard deviation), also improve output from CLI to the user (i.e. remove the wording "Calibration plot" when only having a single confidence level).
