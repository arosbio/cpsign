# CPSign-API: Java API of CPSign

Package that builds on the ConfAI by including the [CDK](https://cdk.github.io/) library and adding support for reading CSV and SDF files with chemical data, computing descriptors for those molecules and expose predictor classes that directly work on `IAtomContainers` (i.e. the class CDK uses for molecules), handling all complexity of computing descriptors and applying data transformations to match how the training data was prepared before modeling.

## Exposed services
In order to allow users to inject their own custom classes CPSign uses Java ServiceLoader functionality in order to instantiate some of its features. CPSign-API uses the interface 
```
com.arosbio.cheminf.descriptors.ChemDescriptor
``` 
for computing descriptors. 
