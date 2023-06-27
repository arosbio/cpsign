/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.ConfAI;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig.Defaults.PredictorType;
import com.arosbio.commons.Version;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.IOSettings;
import com.arosbio.io.JarDataSource;
import com.arosbio.io.JarWrapperOutputStream;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.cp.acp.ACP;
import com.arosbio.ml.cp.tcp.TCP;
import com.arosbio.ml.interfaces.AggregatedPredictor;
import com.arosbio.ml.interfaces.ClassificationPredictor;
import com.arosbio.ml.interfaces.Predictor;
import com.arosbio.ml.interfaces.RegressionPredictor;
import com.arosbio.ml.io.ModelIO.ModelJarProperties.Directories;
import com.arosbio.ml.io.impl.PropertyFileStructure;
import com.arosbio.ml.io.impl.PropertyFileStructure.InfoSection;
import com.arosbio.ml.io.impl.PropertyFileStructure.ParameterSection;
import com.arosbio.ml.io.impl.PropertyFileStructure.ResourceSection;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.arosbio.ml.vap.avap.AVAP;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.google.common.collect.ImmutableMap;

public class ModelIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelIO.class);

    public static class ModelJarProperties {

        public static final String HELP_FILE_NAME = "help.md";
        public final static String JSON_PROPERTY_FILE = "cpsign.json";
        public final static String ECLIPSE_PROJECT_FILE = ".project";
        public final static String RUNME_FILE = "RunMe.class";
        public static final String ICON_FILE_NAME = "radioactive.png";

        /**
         * Directory layout of the serialized model
         */
        public static class Directories {
            public static final String HELP_DIRECTORY = "help";
            public static final String MODEL_DIRECTORY = "models";
            public static final String DATA_DIRECTORY = "data";
            public static final String ICONS_DIRECTORY = "icons";
        }

        /**
         * Specifies a meta-data tag that should be added to each file of custom saved
         * data
         * in the form of {@link MountData}.
         */
        public static final String USER_DEFINED_TAG = "USER_DEFINED";
    }

    public static enum ModelType {
    
        /** Precomputed data set saved using ModelSerializer from CPSign */
        PRECOMPUTED_DATA("precomputedData", "isPrecomputed"),
        /** Chemical Predictor saved using ModelSerializer from CPSign */
        CHEM_PREDICTOR("chemPredictor", "isSignaturesPredictor"),
        /** A Predictor 'only' saved from ConfAISerializer */
        PLAIN_PREDICTOR("sparsePredictor", "isSparsePredictor");
    
        public final String name;
        public final String legacyName;
    
        private ModelType(String name, String legacyName) {
            this.name = name;
            this.legacyName = legacyName;
        }
    
        public static <K,V> ModelType getType(Map<K, V> props) throws IOException {
            // New serialization model
            if (CollectionUtils.containsKeyArbitraryDepth(props, PropertyFileStructure.InfoSection.MODEL_TYPE_KEY)) {
                String modelTypeStr = CollectionUtils.getArbitratyDepth(props, PropertyFileStructure.InfoSection.MODEL_TYPE_KEY).toString();
                for (ModelType t : values()) {
                    if (t.name.equalsIgnoreCase(modelTypeStr)) {
                        return t;
                    }
                }
                throw new IOException("Unsupported model type: " + modelTypeStr);
            }
            // Older model type
            for (ModelType t : values()) {
                Object v = CollectionUtils.getArbitratyDepth(props, t.legacyName);
                if (v != null && (boolean) v) {
                    return t;
                }
            }
            LOGGER.debug("Failed deducing model type from properties: {}", props);
            throw new IOException("Unsupported model type");
        }
    }

    // ============================================================
    // ============================================================
    // SAVING
    // ============================================================
    // ============================================================

    private static final String MANIFEST_TEMPLATE = "resources/manifest_template.MF";
    @SuppressWarnings("unused")
    private static final String PROJECT_TEMPLATE = "resources/project_template.xml";
    private static final String ICONS_RESOURCE_PATH = "resources/radioactive.png";
    private static final String RUNME_CLASS_FILE_PATH = "resources/RunMe_class.txt";

    // ============================================================
    // IMPLEMENTING METHODS
    // ============================================================

    /**
     * Checks if the {@code location} is valid for a user to add {@link MountData}
     * in, i.e. saving some custom information in.
     * 
     * @param location the location to validate
     * @throws IllegalArgumentException if {@code location} is invalid/not allowed
     */
    public static void verifyLocationForCustomData(String location) throws IllegalArgumentException {
        if (location.equals(Directories.HELP_DIRECTORY + '/')) {
            // we don't care about help-directory - user can specify their own help files
        } else if (location.startsWith(Directories.MODEL_DIRECTORY + '/')) {
            throw new IllegalArgumentException("Not allowed mounting data in the 'data' directory");
        } else if (location.startsWith(Directories.ICONS_DIRECTORY)) {
            // don't care about icons either
        } else if (location.equals(ModelIO.ModelJarProperties.JSON_PROPERTY_FILE)) {
            throw new IllegalArgumentException(
                    "Not allowed mounting data in '" + ModelIO.ModelJarProperties.JSON_PROPERTY_FILE
                            + "' location, this is reserved for internal usage");
        }
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    public static void verifyPredictorCanBeSaved(Predictor predictor)
            throws IllegalArgumentException {
        if (predictor instanceof AggregatedPredictor &&
                ((AggregatedPredictor) predictor).isPartiallyTrained()) {
            return; // this is fine!
        } else if (predictor instanceof TCP) {
            return; // TCP does not need to be trained!
        } else if (predictor.isTrained())
            return; // also fine
        else
            throw new IllegalArgumentException("Predictor is not trained, cannot save as trained JAR model");
    }

    public static void addIcon(DataSink sink) {
        try (
                InputStream existingIcon = ConfAISerializer.class.getClassLoader()
                        .getResourceAsStream(ICONS_RESOURCE_PATH);
                OutputStream iconOutput = sink.getOutputStream(
                        Directories.ICONS_DIRECTORY + '/' + ModelIO.ModelJarProperties.ICON_FILE_NAME);) {
            IOUtils.copy(existingIcon, iconOutput);
            LOGGER.debug("copied icon");
        } catch (Exception e) {
            LOGGER.debug("Failed adding icon to jar");
        }
    }

    public static Manifest getManifest(ModelInfo info) throws IOException {
        Version modelVersion = info.getVersion();
        if (modelVersion == null)
            modelVersion = Version.defaultVersion();
        String modelSymbolicName = getSymbolicName(info.getName());
        InputStream manifestStream = ConfAISerializer.class.getClassLoader().getResourceAsStream(MANIFEST_TEMPLATE);
        Map<String, String> valueMap = ImmutableMap.of("MODEL_NAME", info.getName(),
                "MODEL_ID", modelSymbolicName,
                "MODEL_VERSION", modelVersion.toString(),
                "BUILD_VERSION", ConfAI.getVersion().toString());

        String mfText = new StringSubstitutor(valueMap).setEnableUndefinedVariableException(true)
                .replace(IOUtils.toString(manifestStream, IOSettings.CHARSET));

        return new Manifest(IOUtils.toInputStream(mfText, IOSettings.CHARSET));
    }

    @SuppressWarnings("unused")
    private static PredictorType getMLType(Predictor predictor) {

        if (predictor instanceof ClassificationPredictor) {
            // Classification
            if (predictor instanceof ACP) {
                return PredictorType.ACP_CLASSIFICATION;
            } else if (predictor instanceof AVAP) {
                return PredictorType.VAP_CLASSIFICATION;
            } else if (predictor instanceof TCP) {
                return PredictorType.TCP_CLASSIFICATION;
            }

        } else if (predictor instanceof RegressionPredictor) {
            // Regression
            if (predictor instanceof ACP) {
                return PredictorType.ACP_REGRESSION;
            }
        }

        throw new IllegalArgumentException("PredictionModel of unknown MLType=" + predictor.getClass());
    }

    public static void verifyEmptyFinalJAR(File finalJAR)
            throws IllegalArgumentException {
        if (finalJAR.exists() && finalJAR.isDirectory()) {
            LOGGER.debug("Model output file already exists and is a directory");
            throw new IllegalArgumentException("Model output file already exists and is a directory");
        } else if (finalJAR.exists()) {
            LOGGER.debug("Model output file already existed, this will be overwritten if possible");
            try {
                FileUtils.forceDelete(finalJAR);
            } catch (IOException e) {
                LOGGER.debug("Could not delete previous output model", e);
                throw new IllegalArgumentException("Model output file already exists and could not be removed");
            }
        }
    }

    public static ModelInfo getInfo(Predictor obj) {
        return (obj.getModelInfo() != null ? obj.getModelInfo() : new ModelInfo(obj.getPredictorType()));
    }

    public static void addRunmeFile(DataSink jarSink) {
		try (InputStream runClassFile = ModelIO.class.getClassLoader().getResourceAsStream(RUNME_CLASS_FILE_PATH);
				OutputStream runmeStream = jarSink.getOutputStream(ModelJarProperties.RUNME_FILE)){

			IOUtils.copy(runClassFile, runmeStream);
			LOGGER.debug("wrote RunMe.class");
		} catch (IOException e) {
			LOGGER.debug("Failed copying the RunMe.class file", e);
		}
	}

    /**
     * 
     * @param info {@link ModelInfo}
     * @param t    a {@link ModelIO.ModelType}
     * @param ts   timestamp
     * @return a map with properties for info-section
     */
    public static Map<String, Object> getInfoSection(ModelInfo info, ModelIO.ModelType t, long ts) {
        return ImmutableMap.of(
                InfoSection.MODEL_VERSION_KEY, info.getVersion().toString(),
                InfoSection.MODEL_NAME_KEY, info.getName(),
                InfoSection.MODEL_CATEGORY_KEY, info.getCategory(),
                InfoSection.BUILD_TS_KEY, new Date(ts).toString(),
                InfoSection.BUILD_SW_VERSION_KEY, ConfAI.getVersionAsString(),
                InfoSection.MODEL_TYPE_KEY, t.name);
    }

    private static String getSymbolicName(String modelName) {
        return "model.confai." + modelName.trim().toLowerCase().replaceAll(" ", "_");
    }

    public static void mountExtraData(JarOutputStream jar, MountData[] extraData, Map<String, Object> resourceSection)
            throws InvalidKeyException, IOException {
        if (extraData==null || extraData.length==0){
            LOGGER.debug("No extra data written");
            return;
        }
            
        List<String> mountLocs = new ArrayList<>();
        for (MountData data : extraData) {
            JarEntry entry = new JarEntry(data.getLocation());
            entry.setComment(ModelIO.ModelJarProperties.USER_DEFINED_TAG);
            jar.putNextEntry(entry);
            try (OutputStream ostream = new JarWrapperOutputStream(jar)) {
                data.writeData(ostream);
            }
            mountLocs.add(data.getLocation());
        }
        resourceSection.put(PropertyFileStructure.ResourceSection.CUSTOM_MOUNTED_LIST_KEY, mountLocs);
        LOGGER.debug("Written extra data");
    }


    // ============================================================
    // ============================================================
    // LOADING
    // ============================================================
    // ============================================================

    public static class URIUnpacker implements AutoCloseable {

        private final File local;
        private final boolean rmLocalFile;

        // Might be open and require closing
        private JarDataSource jar;

        public URIUnpacker(URI uri) throws IOException {
            if (!UriUtils.canReadFromURI(uri))
                throw new IOException("Could not read URI: " + uri);

            // make sure we have a local version of the model-jar
            local = UriUtils.getFile(uri);
            // If the input uri is not local, means that 'local' has been downloaded and
            // needs to be removed
            // when this instance has been closed
            rmLocalFile = !UriUtils.isLocalFile(uri);

        }

        public DataSource getSrc() throws IOException {
            jar = new JarDataSource(new JarFile(local));

            return jar;
        }

        /**
         * Closes resources - i.e. if the URI had to be downloaded from another source
         * that tmp-file
         * will be removed
         */
        public void close() throws IOException {
            if (jar != null) {
                jar.close();
            }
            if (rmLocalFile) {
                if (!local.delete()) {
                    throw new IOException(
                            "Failed cleaning up after downloading URI from remote, file needed to be removed manually: "
                                    + local);
                }
            }
        }

    }

    private static final String UNKNOWN_PARAMETER = "Unknown";

    /**
     * Get the version of CPSign used to build the given model
     * 
     * @param uri The {@link URI} of a cpsign model or data set
     * @return the CPSign {@link com.arosbio.commons.Version Version} used for
     *         building this model
     * @throws IOException faulty/missing {@code uri} or input is not a cpsign
     *                     model/data set
     */
    public static Version getModelBuildVersion(URI uri)
            throws IOException {
        return Version.parseVersion(CollectionUtils
                .getArbitratyDepth(getCPSignProperties(uri), PropertyFileStructure.InfoSection.BUILD_SW_VERSION_KEY)
                .toString());
    }

    /**
     * Get a map with parameters
     * 
     * @param uri The {@link URI} of a cpsign-model
     * @return A map with model info
     * @throws IOException faulty/missing {@code uri} or input is not a cpsign
     *                     model/data set
     */
    public static Map<String, String> getModelInfo(URI uri)
            throws IOException {
        return getModelInfo(uri, true);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getModelInfo(URI modelUri, boolean fullInfo)
            throws IOException {

        Map<String, Object> json = getCPSignProperties(modelUri);

        // Put together info
        Map<String, String> info = new LinkedHashMap<>();
        Map<String, Object> infoSection = null, testSection = null;

        if (json.containsKey(PropertyFileStructure.InfoSection.NESTING_KEY)) {

            infoSection = (Map<String, Object>) json.get(PropertyFileStructure.InfoSection.NESTING_KEY);
            info.put("CPSign build version",
                    infoSection.get(PropertyFileStructure.InfoSection.BUILD_SW_VERSION_KEY).toString());
        }
        if (json.containsKey(PropertyFileStructure.TestSection.NESTING_KEY)) {
            testSection = (Map<String, Object>) json.get(PropertyFileStructure.TestSection.NESTING_KEY);
            if (info.isEmpty()) {
                info.put("CPSign build version",
                        testSection.get(PropertyFileStructure.TestSection.TEST_CPSIGN_BUILD_VERSION_KEY).toString());
            }
        }

        if (fullInfo) {
            Map<String, Object> parametersSection = (Map<String, Object>) json
                    .get(PropertyFileStructure.ParameterSection.NESTING_KEY);
            ModelIO.ModelType mt = null;

            if (infoSection != null) {
                // New model type
                info.put("Build time", infoSection.get(InfoSection.BUILD_TS_KEY).toString());
                info.put("Model name", infoSection.get(InfoSection.MODEL_NAME_KEY).toString());
                info.put("Model version", infoSection.get(InfoSection.MODEL_VERSION_KEY).toString());
                mt = ModelIO.ModelType.getType(infoSection);
            } else {
                // Older property file layout
                if (testSection != null) {
                    info.put("Build time", testSection
                            .getOrDefault(PropertyFileStructure.TestSection.TEST_BUILD_TIME_KEY, UNKNOWN_PARAMETER)
                            .toString());
                }
                info.put("Model name", CollectionUtils
                        .getArbitratyDepth(parametersSection, PropertyNameSettings.MODEL_NAME_KEY, UNKNOWN_PARAMETER)
                        .toString());
                info.put("Model version", CollectionUtils
                        .getArbitratyDepth(parametersSection, PropertyNameSettings.MODEL_VERSION_KEY, UNKNOWN_PARAMETER)
                        .toString());
                mt = ModelIO.ModelType.getType(parametersSection);
            }
            info.put("Model endpoint", CollectionUtils
                    .getArbitratyDepth(parametersSection, PropertyNameSettings.MODELING_ENDPOINT_KEY, UNKNOWN_PARAMETER)
                    .toString());
            String modelTxt = getModelTypeAsTxt(mt, parametersSection);
            info.put("Model type", modelTxt);
            info.put("Observations used", CollectionUtils
                    .getArbitratyDepth(parametersSection, PropertyNameSettings.NUM_OBSERVATIONS_KEY, UNKNOWN_PARAMETER)
                    .toString());
            info.put("Number of features", CollectionUtils
                    .getArbitratyDepth(parametersSection, PropertyNameSettings.NUM_FEATURES_KEY, UNKNOWN_PARAMETER)
                    .toString());
            if (mt == ModelType.CHEM_PREDICTOR){
                // Percentiles only set in chem predictors, not data sets or sparse predictors
                info.put("Supports prediction images", supportPredictionImages(parametersSection));
            }
            if (mt != ModelIO.ModelType.PRECOMPUTED_DATA) {
                // Seed only set if predictor (i.e. not precomputed data)
                info.put("Seed used", CollectionUtils
                        .getArbitratyDepth(parametersSection, PropertyNameSettings.ML_SEED_VALUE_KEY, UNKNOWN_PARAMETER)
                        .toString());
            }
        }

        return info;
    }

    private static String supportPredictionImages(Map<String, Object> props){
        if (CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.LOW_PERCENTILE_KEY, null) != null && 
            CollectionUtils.getArbitratyDepth(props, PropertyNameSettings.HIGH_PERCENTILE_KEY, null) != null){
            return "Yes";
        }
        return "No";
    }

    public static Map<String, Object> getCPSignProperties(URI modelUri) throws IOException {
        try (
                URIUnpacker unpacker = new URIUnpacker(modelUri);) {
            return getCPSignProperties(unpacker.getSrc());
        }
    }

    public static Map<String, Object> getCPSignProperties(DataSource src) throws IOException {
        final IOException non_valid_model = new IOException("Input was not a valid model");

        try (InputStream stream = src.getInputStream(ModelIO.ModelJarProperties.JSON_PROPERTY_FILE)) {
            String config = IOUtils.toString(stream, IOSettings.CHARSET);
            LOGGER.debug("Parsed config file: {}", config);

            // Parse as JSON
            if (config == null || config.isEmpty())
                throw non_valid_model;

            try {
                return (JsonObject) Jsoner.deserialize(config);
            } catch (Exception e) {
                LOGGER.debug("Failed parsing config file", e);
                throw non_valid_model;
            }
        }
    }

    private static final String UNREC_MODEL_TYPE = "Unrecognized type";

    public static String getModelTypeAsTxt(Map<String, Object> properties) {
        ModelIO.ModelType mt = null;
        try {
            mt = ModelIO.ModelType.getType(properties);
        } catch (Exception e) {
            return UNREC_MODEL_TYPE;
        }
        return getModelTypeAsTxt(mt, properties);
    }

    public static String getModelTypeAsTxt(ModelIO.ModelType mt, Map<String, Object> properties) {

        String predType = null;
        switch (mt) {
            case CHEM_PREDICTOR:
                predType = "Chem ";
                break;

            case PRECOMPUTED_DATA:
                if (!CollectionUtils.containsKeyArbitraryDepth(properties, PropertyNameSettings.IS_CLASSIFICATION_KEY))
                    return "Precomputed data";
                else if ((boolean) CollectionUtils.getArbitratyDepth(properties,
                        PropertyNameSettings.IS_CLASSIFICATION_KEY, false))
                    return "Precomputed Classification data";
                else
                    return "Precomputed Regression data";

            case PLAIN_PREDICTOR:
                predType = "";
                break;
            default:
                return UNREC_MODEL_TYPE;
        }

        // Resolve Predictor type
        try {
            if (CollectionUtils.containsKeyArbitraryDepth(properties, PropertyNameSettings.ML_TYPE_NAME_KEY)) {
                PredictorType pt = PredictorType.getPredictorType(
                        "" + CollectionUtils.getArbitratyDepth(properties, PropertyNameSettings.ML_TYPE_NAME_KEY));
                return predType + pt.getName() + " predictor";
            } else if (CollectionUtils.containsKeyArbitraryDepth(properties, PropertyNameSettings.ML_TYPE_KEY)) {
                PredictorType pt = PredictorType.getPredictorType(
                        "" + CollectionUtils.getArbitratyDepth(properties, PropertyNameSettings.ML_TYPE_KEY));
                return predType + pt.getName() + " predictor";
            }
        } catch (Exception e) {
            LOGGER.debug("Could not deduce the (predictor) model type from properties: {}", properties);
        }
        return UNREC_MODEL_TYPE;

    }

    public static String getMountedDataAsString(URI uri, String location, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {
        try (
                URIUnpacker unpacker = new URIUnpacker(uri);) {
            return getMountedDataAsString(unpacker.getSrc(), location, spec);
        }
    }

    public static String getMountedDataAsString(DataSource src, String location, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {
        if (!src.hasEntry(location)) {
            throw new IOException("No location '" + location + "' in model file");
        }

        try (
                InputStream istream = src.getInputStream(location);
                BufferedInputStream buff = new BufferedInputStream(istream);) {
            if (spec != null && spec.canDecrypt(buff)) {
                LOGGER.debug("Reading mounted data from encrypted file");
                return IOUtils.toString(spec.decryptStream(buff), IOSettings.CHARSET);
            } else {
                return IOUtils.toString(buff, IOSettings.CHARSET);
            }
        } catch (IOException e) {
            LOGGER.debug("Failed reading entry from Jar", e);
            throw new IOException("Failed reading data from model");
        }

    }

    /**
     * Copies all content of the mounted data to a temporary {@link java.io.File
     * File},
     * note that this file will be removed once JVM exits, so it must be copied in
     * turn to
     * a persistent file before exiting the currently running program. Does not take
     * care of
     * encryption, only performs a byte-to-byte copy.
     * 
     * @param modelUri The URI of the model
     * @param location The specific location within the model where the file is
     *                 located
     * @return A temporary {@link java.io.File File} with the contents
     * @throws IllegalArgumentException If no data mounted at the
     *                                  <code>location</code>
     * @throws IOException              If no data mounted at the
     *                                  <code>location</code> or other issues
     *                                  occurred
     */
    public static File getMountedDataAsTmpFile(URI modelUri, String location)
            throws IOException {

        try (
                URIUnpacker unpacker = new URIUnpacker(modelUri);) {
            return getMountedDataAsTmpFile(unpacker.getSrc(), location);
        }
    }

    public static File getMountedDataAsTmpFile(DataSource src, String location)
            throws IOException {

        if (!src.hasEntry(location)) {
            throw new IOException("No location '" + location + "' in model file");
        }

        // Create the result file and set to delete it after jvm exits
        File resFile = File.createTempFile("mounted_data", ".txt");
        resFile.deleteOnExit();

        try (InputStream istream = src.getInputStream(location);) {
            FileUtils.copyToFile(istream, resFile);
            return resFile;
        } catch (IOException e) {
            LOGGER.debug("Failed reading mounted data from location '{}' from DataSource", location, e);
            throw new IOException("Could not read ");
        }

    }

    /**
     * This method copies all contents of the mounted data to a temporary
     * {@link java.io.File File},
     * note that this file will be removed once JVM exits, so it must be copied in
     * turn to
     * a persistent file before exiting the program currently running
     * 
     * @param uri      The URI of the model
     * @param location The specific location within the model where the file is
     *                 located
     * @param spec     An {@link com.arosbio.encryption.EncryptionSpecification
     *                 EncryptionSpecification} to decrypt the file with
     * @return A temporary {@link java.io.File File} with the contents
     * @throws IllegalArgumentException If no data mounted at the
     *                                  <code>location</code>
     * @throws IOException              Another IOException occurred
     * @throws InvalidKeyException      If the
     *                                  {@link com.arosbio.encryption.EncryptionSpecification
     *                                  EncryptionSpecification} was incorrect in
     *                                  any way or missing
     */
    public static File getMountedDataAsTmpFile(URI uri, String location, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {
        try (
                URIUnpacker unpacker = new URIUnpacker(uri);) {
            return getMountedDataAsTmpFile(unpacker.getSrc(), location, spec);
        }
    }

    public static File getMountedDataAsTmpFile(DataSource src, String location, EncryptionSpecification spec)
            throws IOException, InvalidKeyException {

        if (!src.hasEntry(location)) {
            throw new IOException("No location '" + location + "' in model file");
        }

        File resFile = File.createTempFile("mounted_data", ".txt");
        resFile.deleteOnExit();

        try (InputStream istream = src.getInputStream(location);
                BufferedInputStream buff = new BufferedInputStream(istream);) {
            if (spec != null && spec.canDecrypt(buff)) {
                FileUtils.copyToFile(spec.decryptStream(buff), resFile);
            } else {
                FileUtils.copyToFile(buff, resFile);
            }
            return resFile;
        } catch (IOException e) {
            LOGGER.debug("Failed reading JarEntry from Jar", e);
            throw new IOException("Could not copy mounted data from location '" + location + "'");
        }
    }

    public static List<String> listMountLocations(URI modelUri) throws IllegalArgumentException, IOException {
        if (!UriUtils.canReadFromURI(modelUri))
            throw new IllegalArgumentException("Could not read model URI: " + modelUri);

        // make sure we have a local version of the model-jar
        File localFile = UriUtils.getFile(modelUri);
        boolean isCopied = !UriUtils.isLocalFile(modelUri);

        List<String> mountedData = new ArrayList<>();

        try (final JarFile jarFile = new JarFile(localFile)) {
            Enumeration<JarEntry> jarEnums = jarFile.entries();
            while (jarEnums.hasMoreElements()) {
                JarEntry je = jarEnums.nextElement();
                if (je.isDirectory())
                    continue; // Go to next
                else if (je.getComment() != null
                        && je.getComment().equals(ModelIO.ModelJarProperties.USER_DEFINED_TAG)) {
                    mountedData.add(je.getName());
                }
            }
        } catch (Exception e) {
            // Make sure that models are deleted even if they fail - must remove junk-files
            if (isCopied) {
                localFile.delete();
            }
            throw e;
        }

        if (isCopied)
            localFile.delete(); // remove the model

        return mountedData;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validatePropertiesAndFlatten(Map<String, Object> jsonProps) {
        Map<String, Object> flattened = new HashMap<>();
        if (jsonProps.get(PropertyFileStructure.ParameterSection.NESTING_KEY) == null) {
            LOGGER.debug("Missing PARAMETERS_SECTION in cpsign.json properties file");
            throw new IllegalArgumentException("Model is not properly structured");
        }
        if (jsonProps.get(PropertyFileStructure.ResourceSection.NESTING_KEY) == null) {
            LOGGER.debug("Missing RESOURCES_SECTION in cpsign.json properties file");
            throw new IllegalArgumentException("Model is not properly structured");
        }
        flattened.putAll((Map<String, Object>) jsonProps.get(ParameterSection.NESTING_KEY));
        flattened.putAll((Map<String, Object>) jsonProps.get(ResourceSection.NESTING_KEY));
        if (jsonProps.containsKey(InfoSection.NESTING_KEY)) {
            flattened.putAll((Map<String, Object>) jsonProps.get(InfoSection.NESTING_KEY));
        }
        return flattened;
    }

}
