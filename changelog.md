# Change log for CPSign versions

### 2.0.0 RC2 :
- Introduced a common `ProgressTracker` that is shared among the processing stack while loading and processing new data. This way a specific threshold of "number of allowed failures" can be adhered to instead of having individual thresholds for each level of processing. This trickles down into `--early-termination` of the CLI now stopping when it should.
 - Fixed bug where `MolAndActivityConverter` would stop when encountering the maximum specified number of conversion failures, and do it silently without causing the expected exception. This bug was also present when using the CLI `precompute` program - and is now solved.
 - Remove the possibility to use clustered POSIX flags to the CLI, which resulted in strange behaviour that was hard to find the cause of.
 - Update the Fuzzy matching logics to (hopefully) improve matching of parameters to the CLI.
 - Update dependency version of Picocli.
 - Added logics for trying to deduce the user error to improve error messages to CLI users, specifically for running `precompute`. Also streamlined the output to be more coherent among `predict`, `predict-online` and `validate`. 
 - Extracted prediction logics of `predict` and `predict-online` into common utility class, again to streamline the behaviour seen by the user.