# ${MODEL_NAME}

## Model details
${MODEL_TYPE:-CPSign} model trained on QSAR descriptors, which could be faulon signatures [Faulon2003], physicochemical descriptors calculated using CDK [CDK], ECFP/MACCS Fingerprints, any user-given descriptor or a combination of these. The underlying ML model can be based on models from the LIBLINEAR [Fan2008] or LIBSVM [Chang2011] packages, or from custom extensions like Deeplearning from DL4J library. 

__Data info__
Number of observations: ${NUM_OBSERVATIONS}  
Number of features (total): ${NUM_FEATURES}
Number of features (signatures): ${NUM_SIGNATURES}
  
__Predictor info__
Predictor type: ${PREDICTOR_TYPE}
Underlying scorer implementation: ${SCORER_TYPE}
 

## References

[Faulon2003]	Faulon, J.-L., Visco, Jr, D. P., and Pophale, R. S.  
The signature molecular descriptor. 1. using extended valence sequences in QSAR and QSPR studies.  
_J Chem Inf Comput Sci_ 43, 3 (2003), 707-20.  

[CDK]	Willighagen et al. 
The Chemistry Development Kit (CDK) v2.0: atom typing, depiction, molecular formulas, and substructure searching. 
_J. Cheminform_ 9, 3 (2017), doi:10.1186/s13321-017-0220-4.

[Fan2008]	Fan, R.-E., Chang, K.-W., Hsieh, C.-J., Wang, X.-R., and Lin, C.-J.  
LIBLINEAR: A library for large linear classification.  
_Journal of Machine Learning Research_ 9 (2008), 1871-1874.  

[Chang2011]	Chang, C.-C.; Lin, C.-J.  
LIBSVM: A library for support vector machines.   
_ACM Trans. Intell. Syst. Technol._ 2011, 2, 27.  


