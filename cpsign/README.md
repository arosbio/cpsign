# CPSign - Conformal Prediction with Signatures molecular descriptor

Conformal Prediction <br>
with the signatures molecular descriptor and SVM. <br>
(C) Copyright 2022, Aros Bio AB, [arosbio.com](https://arosbio.com)

============================================================

CPSign is the final sub-project of this repository and include all of its predecessor and make up the final CLI software. 


## CLI usage

The CLI can be executed as a normal JAR file using: 

`java -jar cpsign-[version].jar [command] [arguments]`


### Accepted program commands
* precompute
* train
* fast-aggregate
* predict
* predict-online, online-predict
* tune, gridsearch
* tune-scorer, tune-algorithm
* crossvalidate, cv
* validate
* aggregate
* fast-aggregate
* gensign
* model-info, check-version
* list-features, list-descriptors
* explain
* generate-key, key


Running by only giving the `[command]` after invocation of cpsign will output the help-text for that given command, i.e.; 

```
java -jar cpsign-[version].jar train
```
Will list the help for running the **train** command, including all of its available arguments.


## Developer info

### Progress bar
The progress bar is semi-manual work to make consistent. It uses the implementation from [Progressbar](https://github.com/ctongfei/progressbar/), wrapped in the `CLIProgressBar` class. Each CLI program should call the `CLIProgramUtils.config()` method to init the progress-bar. Stepping through each "section" is then done manually using the `<progress-bar>.stepProgress()` method. This method both updates the "section name" in the end of the progress bar as well as moving progress bar further. This is something that can be improved and tested further to make sure it works properly. 

