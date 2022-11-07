# CPSign 

Conformal Prediction <br>
with the signatures molecular descriptor and SVM. <br>
(C) Copyright 2022, Aros Bio AB, [arosbio.com](https://arosbio.com)

CPSign is a machine learning and QSAR software package purely written in Java, leveraging the popular [LIBSVM](https://github.com/cjlin1/libsvm) and [Liblinear](https://github.com/bwaldvogel/liblinear-java) packages for machine learning and the [CDK](https://cdk.github.io/) for handling chemistry. It allows directly reading in molecular data in CSV format (requiring SMILES for molecular structure) or SDF (v2000 and v3000), compute descriptors and build machine learning models. The generated model files contain all information for later predicting new compounds without having to manually compute descriptors and apply data transformations. CPSign implements the inductive [Conformal Prediction](http://www.alrw.net/) algorithms ICP and ACP (or their more recent name; Split Conformal Predictors) for both classification and regression problems, as well as transductive conformal prediction (TCP) and [Cross Venn-ABERS](http://www.alrw.net/articles/13.pdf) probabilistic prediction for classification problems.


In order to minimize memory footprint, the project has been split up into several sub-projects, making it possible to only use as much of the code base that is needed. I.e. the [ConfAI](confai/README.md) includes all data processing and modeling, but without including the large [CDK](https://cdk.github.io/) package which is only needed when handling chemistry. The [CPSign-API](cpsign-api/README.md) is for users that deals with chemical data sets, but wish to use the Java API instead of the terminal CLI.


## Sub-projects
This repo is split up into several child-projects:
* [depict](depict/README.md) - An extension of the CDK depictions code which allows for generating 'blooming' molecules - i.e. visually appealing depictions with highlights that fade of and mix. Intended mainly for displaying which atoms contributed the most in a given prediction.
* [encrypt-api](encrypt-api/README.md) - A single interface for including encryption of saved prediction models or precomputed data sets. Contact [Aros Bio](https://arosbio.com) in case you wish to purchase such an extension that secures models by only allowing predictions when also having the encryption key.
* [test-utils](test-utils/REAME.md) - Project that include test resources such as SVMLight files and some QSAR datasets used in the tests.
* [confai](confai/README.md) - Conformal and probabilistic predictors, data, processing etc, excluding CDK and chemistry specific code. Thus provides a software package that allows training CP and Venn-ABERS models for non-chemical data, without the overhead of including the complete CDK package.
* [cpsign-api](cpsign-api/README.md) - Java API of CPSign, including chemistry and the CDK library.
* [cpsign](cpsign/README.md) - Final CLI version of CPSign.


Note: The documentation of CPSign is located in a separate [CPSign docs repo](https://github.com/arosbio/cpsign_docs).

## License
CPSign is dual licensed, where the user can choose between the [GNU General Public License](http://www.gnu.org/licenses/gpl-3.0.html) or a [commercial license](license/META-INF/comm-license.txt).

## Java version
CPSign is written and developed on Java 11, but can with a few changes compile and run on Java 8. 


## TODOs before publication
Publish to Maven central. Some links;
* https://entzik.medium.com/how-to-publish-open-source-java-libraries-to-maven-central-70f9232462f5
* https://medium.com/@scottyab/how-to-publish-your-open-source-library-to-maven-central-5178d9579c5
* https://maven.apache.org/repository/index.html

## Building
The project is built and managed using [Maven](https://maven.apache.org/) and building the jars is as straightforward as running (assuming that you have maven installed) from the base directory:
```
mvn package 
``` 
or, alternatively (if you do not wish to run all tests):
```
mvn package -DskipTests=true
```

__Note:__ As there is a dependence between the projects you may need to use `mvn install` instead of the `package` goal, for the "earlier" projects to be available to latter ones in the hierarchy. 


## Future work
- [ ] Implement isotonic regression in a Java project, to replace the [pairAdjacentViolators](https://github.com/sanity/pairAdjacentViolators) dependency which also require bundling in Kotlin standard lib - leading to an increased memory footprint of the jars.