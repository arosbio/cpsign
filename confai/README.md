# ConfAI - Conformal prediction for Artificial Intelligence

The main project that includes data structures, data processing, machine learning algorithms and CP/Venn-ABERS predictor classes. 


## Custom extensions
In case you wish to extend CPSign with additional features, but not contribute it to the upstream project you can implement the following interfaces and expose them as java services:
```
com.arosbio.data.transform.Transformer
com.arosbio.ml.algorithms.MLAlgorithm
com.arosbio.ml.cp.nonconf.calc.PValueCalculator
com.arosbio.ml.cp.nonconf.NCM
com.arosbio.ml.sampling.SamplingStrategy
com.arosbio.ml.metrics.Metric
com.arosbio.ml.testing.TestingStrategy
```

This way CPSign can use them just as the classes native to CPSign, although all testing is up the user.
