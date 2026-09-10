import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import java.util.stream.Collectors;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import org.yaml.snakeyaml.Yaml;

import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;

import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.control.Header;

import java.security.KeyStore;
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException

import java.net.URLEncoder;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @file Get reporting data - general info and resources data.groovy
 * @brief Get information from Kubernetes (k3s) about pods in the namespace and
 *        information about resource usage from Prometheus.
 *        Store received data in CSV for persistancy and send data to Influx DB.
 * @detail Achives the following actions:
 *         1) Checks if a truststore does exist and if the certificate is present inside.
 *            Otherwise, new truststore with proper certificate will be created from the
 *            kubernetes config yaml.
 *         2) Uses truststore to create an instance of custom SSL context HTTP client to
 *            receive data from Kubernetes and Prometheus.
 *            Namespece pod's information received from Kubernetes:
 *                - App name
 *                - Image
 *                - CPU
 *                - Memory
 *                - Restarts
 *            Data received from Prometheus:
 *                - CPU metric (in milicores)
 *                - CPU limit (in milicores)
 *                - Memory metric (in megabytes)
 *                - Memory limit (in megabytes)
 *         3) Creates CSV file with general test information:
 *                - Test start timing
 *                - Test maximum load timing
 *                - Test finish timing
 *                - Number of agent threads
 *                - Time to add one Agent thread (RampUp / Agents)
 *                - Number of Client threads
 *                - Time to add one Client thread (RampUp / Clients)
 *         4) Creates CSV files to store CPU and Memory data and their corresponding
 *            limits for each pod.
 *         5) Sends CPU and Memory resources data and their corresponding limits to InfluxDB.
 */

/**
 * @class InvalidResponseException
 * @brief Exception thrown when the HTTP response status code does not indicate success (non-2XX status codes).
 */
public class InvalidResponseStatusException extends Exception {
    public InvalidResponseStatusException(String message) {
        super(message);
    }
}

/**
 * @class EmptyContentException
 * @brief Exception thrown when the HTTP response has a status code of 204 (No Content)
 *        or has empty response body.
 */
public class EmptyContentException extends Exception {
    public EmptyContentException(String message) {
        super(message);
    }
}

/**
 * @class MissingValueException
 * @brief Exception thrown when the HTTP response does not contain the expected data.
 */
public class MissingValueException extends Exception {
    public MissingValueException(String message) {
        super(message);
    }
}

/**
 * @class GlobalVariableManager
 * @brief Class to manage global jmeter variables.
 */
public class GlobalVariableManager {

    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        absolute path to catalogue.
     * @details This parameter represents an absolute path in the system where the repository is located
     *          (e.g., C:/directory/repositories/ OR /opt/repositories/).
     */
    private static String cataloguePath;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        relative path inside repository.
     * @details This parameter represents continuation of repository path to JMeter inside repository
     *          (e.g., repository_name/jmeter/).
     */
    private static String repDataPath;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        test build tag.
     * @details This parameter represents the name of the build under test.
     */
    private static String buildTag;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        path to Kubernetes configuration yaml file.
     * @details This parameter represents the path to Kubernetes configuration file.
     */
    private static String kubeConfigPath;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        name of the Kubernetes configuration yaml file.
     * @details This parameter represents the name of Kubernetes yaml configuration file.
     */
    private static String kubeConfigYaml;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Kubernetes domain name.
     * @details This parameter represents the Kubernetes (rancher k3s) domain name.
     */
    private static String kubeDomainName;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Kubernetes IP address.
     * @details This parameter represents the Kubernetes (rancher k3s) IP address.
     */
    private static String kubeServerIP;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Kubernetes auth token.
     * @details This parameter represents the Kubernetes (rancher k3s) auth token.
     *          For safety reasons the user must be handmade with all nessesary permissions,
     *          and auth token must not be taken from the real user!
     */
    private static String kubeApiAuthToken;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        namespace name on Kubernetes' cluster.
     * @details This parameter represents the namespace name on cluster.
     */
    private static String namespace;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Prometheus domain name.
     * @details This parameter represents the Prometheus domain name.
     */
    private static String promDomainName;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Prometheus IP address.
     * @details This parameter represents the Prometheus IP address.
     */
    private static String promServerIP;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        step for queries to Prometheus.
     * @details This parameter represents the step (interval) between values used for Prometheus queries.
     */
    private static String promStep;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        URL address to Influx DB.
     * @details This parameter represents the URL address of Influx DB.
     */
    private static String influxdbUrl;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        number of Agent user threads.
     * @details This parameter represents the number of Agent user threads.
     */
    private static String concurrentAgentNumber;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Agent user ramp up.
     * @details This parameter represents the value in seconds when the Agent thread is activated in test.
     */
    private static String agentRampUp;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        number of Client user threads.
     * @details This parameter represents the number of Client threads.
     */
    private static String concurrentClientNumber;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        Client user ramp up..
     * @details This parameter represents the value in seconds when the client thread is activated in test.
     */
    private static String clientRampUp;

    /**
     * @brief Initializer for parameters passed to script and script variables.
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/ OR /opt/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param kubeConfigPath The path to Kubernetes configuration file.
     * @param kubeConfigYaml The name of Kubernetes yaml configuration file.
     * @param kubeDomainName The Kubernetes (rancher k3s) domain name.
     * @param kubeServerIP The Kubernetes (rancher k3s) ip address.
     * @param kubeApiAuthToken The Kubernetes (rancher k3s) user token.
     * @param namespace The namespace name on cluster.
     * @param promDomainName The Prometheus domain name.
     * @param promServerIP The Prometheus ip address.
     * @param promStep The step (interval) between values used for Prometheus queries.
     * @param influxdbUrl The URL address of Influx DB.
     * @param concurrentAgentNumber The number of Agent threads.
     * @param agentRampUp The value in seconds when the Agent thread is activated in test.
     * @param concurrentClientNumber The number of Client threads.
     * @param clientRampUp The value in seconds when the client thread is activated in test.
     */
    public static void initialize(
        String cataloguePath,
        String repDataPath,
        String buildTag,
        String kubeConfigPath,
        String kubeConfigYaml,
        String kubeDomainName,
        String kubeServerIP,
        String kubeApiAuthToken,
        String namespace,
        String promDomainName,
        String promServerIP,
        String promStep,
        String influxdbUrl,
        String concurrentAgentNumber,
        String agentRampUp,
        String concurrentClientNumber,
        String clientRampUp
    ) {
        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.kubeConfigPath = kubeConfigPath;
        GlobalVariableManager.kubeConfigYaml = kubeConfigYaml;
        GlobalVariableManager.kubeDomainName = kubeDomainName;
        GlobalVariableManager.kubeServerIP = kubeServerIP;
        GlobalVariableManager.kubeApiAuthToken = kubeApiAuthToken;
        GlobalVariableManager.namespace = namespace;
        GlobalVariableManager.promDomainName = promDomainName;
        GlobalVariableManager.promServerIP = promServerIP;
        GlobalVariableManager.promStep = promStep;
        GlobalVariableManager.influxdbUrl = influxdbUrl;
        GlobalVariableManager.concurrentAgentNumber = concurrentAgentNumber;
        GlobalVariableManager.agentRampUp = agentRampUp;
        GlobalVariableManager.concurrentClientNumber = concurrentClientNumber;
        GlobalVariableManager.clientRampUp = clientRampUp;
    }

    /**
     * @brief Get the catalogue path.
     * @return The catalogue path.
     */
    public static String getCataloguePath() {
        return cataloguePath;
    }

    /**
     * @brief Get the repository data path.
     * @return The repository data path.
     */
    public static String getRepDataPath() {
        return repDataPath;
    }

    /**
     * @brief Get the build tag.
     * @return The build tag.
     */
    public static String getBuildTag() {
        return buildTag;
    }

    /**
     * @brief Get the Kubernetes configuration yaml file.
     * @return The combination of path and yaml comfiguration file.
     */
    public static String getKubeConfigYaml() {
        return kubeConfigPath + kubeConfigYaml;
    }

    /**
     * @brief Get the Kubernetes (rancher k3s) domain name.
     * @return The Kubernetes (rancher k3s) domain name.
     */
    public static String getKubeDomainName() {
        return kubeDomainName;
    }

    /**
     * @brief Get the Kubernetes (rancher k3s) user auth.
     * @return The Kubernetes (rancher k3s) ip address.
     */
    public static String getKubeServerIP() {
        return kubeServerIP;
    }

    /**
     * @brief Get the Kubernetes (rancher k3s) user auth token.
     * @return The Kubernetes (rancher k3s) user auth token.
     */
    public static String getKubeApiAuthToken() {
        return kubeApiAuthToken;
    }

    /**
     * @brief Get the Kubernetes namespace in cluster.
     * @return The Kubernetes namespace in cluster.
     */
    public static String getNamespace() {
        return namespace;
    }

    /**
     * @brief Get the Prometheus domain name.
     * @return The Prometheus domain name.
     */
    public static String getPromDomainName() {
        return promDomainName;
    }

    /**
     * @brief Get the Prometheus ip address.
     * @return The Prometheus ip address.
     */
    public static String getPromServerIP() {
        return promServerIP;
    }

    /**
     * @brief Get the Prometheus step.
     * @return The Prometheus step value.
     */
    public static String getPromStep() {
        return promStep;
    }

    /**
     * @brief Get the Influx DB URL.
     * @return The Influx DB URL with required constant parametrization to write data.
     */
    public static String getInfluxdbUrl() {
        return influxdbUrl + "/write?db=db0&precision=s";
    }

    /**
     * @brief Get the number of Agent threads.
     * @return The number of Agent threads.
     */
    public static String getConcurrentAgentNumber() {
        return concurrentAgentNumber;
    }

    /**
     * @brief Get the Agent Ramp Up time.
     * @return The value in seconds when the agent thread is activated in test.
     */
    public static String getAgentRampUp() {
        return agentRampUp;
    }

    /**
     * @brief Get the number of Client threads.
     * @return The number of Client threads.
     */
    public static String getConcurrentClientNumber() {
        return concurrentClientNumber;
    }

    /**
     * @brief Get the Client Ramp Up time.
     * @return The value in seconds when the Client thread is activated in test.
     */
    public static String getClientRampUp() {
        return clientRampUp;
    }

}

/**
 * @class LocalVariableManager
 * @brief Class to manage local script variables.
 */
public class LocalVariableManager {

    /**
     * @brief Static field to hold script variable:
     *        name of the script.
     * @details This variable represents the name of this script and
     *          is used as indentifier in logging.
     */
    private static final String SCRIPT_NAME = "Get reporting data - General Info and Resource Data Collection";
    /**
     * @brief Static field to hold script variable:
     *        log file path.
     * @details This variable represents the output file full path where
     *          custom logs will be written.
     */
    private static final String LOG_FILE_PATH = GlobalVariableManager.getCataloguePath() +
                                                GlobalVariableManager.getRepDataPath() + "results/" +
                                                GlobalVariableManager.getBuildTag() + "/";
    /**
     * @brief Static field to hold script variable:
     *        kubernetes alias.
     * @details The kubernetes alias in the truststore.
     *          Used as a file name and a key.
     */
    private static final String KUBERNETES_ALIAS = "kubernetes-ca";
    /**
     * @brief Static field to hold script variable:
     *        kubernetes truststore file path.
     * @details Path to the kubernetes truststore .jks file.
     */
    private static final String KUBERNETES_TRUSTSTORE_PATH = TruststoreManager.getTruststorePath(KUBERNETES_ALIAS);
    /**
     * @brief Static field to hold script variable:
     *        kubernetes truststore password.
     * @details The kubernetes password for the truststore.
     */
    private static final String KUBERNETES_TRUSTSTORE_PASSWORD = "cakubepass";
    /**
     * @brief Static field to hold script variable:
     *        prometheus alias.
     * @details The prometheus alias in the truststore.
     *          Used as a file name and a key.
     */
    private static final String PROMETHEUS_ALIAS = "prometheus-ca";
    /**
     * @brief Static field to hold script variable:
     *        prometheus truststore file path.
     * @details Path to the prometheus truststore .jks file.
     */
    private static final String PROMETHEUS_TRUSTSTORE_PATH = TruststoreManager.getTruststorePath(PROMETHEUS_ALIAS);
    /**
     * @brief Static field to hold script variable:
     *        password truststore password.
     * @details The prometheus password for the truststore.
     */
    private static final String PROMETHEUS_RUSTSTORE_PASSWORD = "caprompass";
    /**
     * @brief: Static field to hold script variable:
     *         measurement name for Influx DB.
     * @details The measurement name is used for Influx line protocol to send data to DB.
     */
    private static final String INFLUXDB_MEASUREMENT_NAME = "resources";

    /**
     * @brief Get the current script name.
     * @return The current script name.
     */
    public static String getScriptName() {
        return SCRIPT_NAME;
    }

    /**
     * @brief Get the logging file path.
     * @return The logging file path to create file.
     */
    public static String getLogFilePath() {
        return LOG_FILE_PATH;
    }

    /**
     * @brief Get the kubernetes alias.
     * @return The value of the key in the kubernetes truststore file.
     */
    public static String getKubeAlias() {
        return KUBERNETES_ALIAS;
    }

    /**
     * @brief Get the kubernetes truststore file path.
     * @return The file path with kubernetes truststore jks file.
     */
    public static String getKubeTruststorePath() {
        return KUBERNETES_TRUSTSTORE_PATH;
    }

    /**
     * @brief Get the kubernetes truststore password.
     * @return The kubernetes truststore password.
     */
    public static String getKubeTruststorePassword() {
        return KUBERNETES_TRUSTSTORE_PASSWORD;
    }

    /**
     * @brief Get the prometheus truststore file path.
     * @return The value of the key in the prometheus truststore file.
     */
    public static String getPromAlias() {
        return PROMETHEUS_ALIAS;
    }

    /**
     * @brief Get the prometheus truststore file path.
     * @return The file path with prometheus truststore jks file.
     */
    public static String getPromTruststorePath() {
        return PROMETHEUS_TRUSTSTORE_PATH;
    }

    /**
     * @brief Get the prometheus truststore password.
     * @return The prometheus truststore password.
     */
    public static String getPromTruststorePassword() {
        return PROMETHEUS_RUSTSTORE_PASSWORD;
    }

    /**
     * @brief Get the Influx DB measurement name for payload.
     * @return The Influx DB measurement name.
     */
    public static String getInfluxDBMeasurement() {
        return INFLUXDB_MEASUREMENT_NAME;
    }

}

/**
 * @class GlobalTimeManager
 * @brief Class to manage time variables and time operations for the script.
 */
public class GlobalTimeManager {

    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        test Start timing.
     * @details This parameter represents the test Start timing as a Epoch timestamp
     *          in seconds converted to an ISO-8601 string.
     */
    private static String dateTimeStart;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        test Max Load timing.
     * @details This parameter represents the test Max Load timing as a Epoch timestamp
     *          in seconds converted to an ISO-8601 string.
     *          If the max load was not reached, it holds the same value as test End timing.
     */
    private static String dateTimeMaxload;
    /**
     * @brief Static field to hold parameter from JMeter's environment:
     *        test End timing.
     * @details This parameter represents the test End timing as a Epoch timestamp
     *          in seconds converted to an ISO-8601 string.
     */
    private static String dateTimeEnd;
    /**
     * @brief Static field to store the calculated timezone offset in hours.
     * @details This is the difference in whole hours between the test start time and GMT.
     */
    private static int hourOffset = Integer.MIN_VALUE;

    /**
     * @brief Adjusts provided time String value to GMT time zone Instant.
     * @details Used to adjust time values from Prometheus to the GMT time zone.
     * @param time The timestamp value.
     * @return Raw Instant adjusted to GMT timezone.
     */
    public static Instant adjustToGMT(String time) {
        Instant instant = Instant.parse(time);

        if (GlobalTimeManager.hourOffset == Integer.MIN_VALUE) {
            // Determine timezone offset
            Instant testStartInstant = Instant.parse(GlobalTimeManager.dateTimeStart);
            long secondsOffset = instant.getEpochSecond() - testStartInstant.getEpochSecond();
            GlobalTimeManager.hourOffset = Math.toIntExact(Math.round(secondsOffset / 3600.0));
        }

        Instant gmtInstant = instant.minusSeconds(GlobalTimeManager.hourOffset * 3600L);

        return gmtInstant;
    }

    /**
     * @brief Format raw time Instant to ISO-8601 format.
     * @param instant The Instant of a time value.
     * @return Formatted to ISO-8601 GMT timezone Instant as String.
     */
    public static String formatInstantToISO8601(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
                                                       .withZone(ZoneOffset.UTC);

        return formatter.format(instant);
    }

    /**
     * @brief Initializer for timings passed to the script from JMeter's environment (props)
     *        and calculated inside the script.
     * @param dateTimeStart The test Start timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     * @param dateTimeMaxload The test Max Load timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     * @param dateTimeEnd The test End timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     */
    public static void setTimings(
        String dateTimeStart,
        String dateTimeMaxload,
        String dateTimeEnd
    ) {
        GlobalTimeManager.dateTimeStart = dateTimeStart;
        GlobalTimeManager.dateTimeMaxload = dateTimeMaxload;
        GlobalTimeManager.dateTimeEnd = dateTimeEnd;
    }

    /**
     * @brief Get the test Start timing.
     * @return The test Start timing as a Epoch timestamp in seconds converted to String.
     */
    public static String getDateTimeStart() {
        return dateTimeStart;
    }

    /**
     * @brief Get the test Nax Load timing.
     * @return The test Max Load timing as a Epoch timestamp in seconds converted to String.
     *         If Max Load was not reached, the eest End timing is returned.
     */
    public static String getDateTimeMaxload() {
        return dateTimeMaxload;
    }

    /**
     * @brief Get the test End timing.
     * @return The test End timing as a Epoch timestamp in seconds converted to String.
     */
    public static String getDateTimeEnd() {
        return dateTimeEnd;
    }

}

/**
 * @class Logger
 * @brief This class extends LocalVariableManager and used as a custom logger.
 * @details A custom logger which solves the problem of standard logger not working out of scope.
 */
public class Logger extends LocalVariableManager {

    /**
     * @brief Log writer.
     * @details Writes logs to CustomLogging.log located inside the tested build results folder.
     *          The format is: time LEVEL script_name text.
     *          NOTE: JMeter's in-build logger doesn't work when out of scope!
     * @param level The logging level (Info, Warn, Error, etc.).
     * @param text The text of the logged entry.
     */
    public static void logWriter(String level, String text) {
        long time_ms = new Date().getTime() / 1000;
        int time_s = (int) time_ms;
        String time = Instant.ofEpochSecond(time_s).toString();

        File logFile = new File(getLogFilePath() + "CustomLogging.log");
        logFile.getParentFile().mkdirs();
        logFile.createNewFile();

        logFile.withWriterAppend("UTF-8") { writer ->
            writer.write(time + " " +
                level.toUpperCase() + " " +
                getScriptName() + ": " +
                text + System.lineSeparator());
        }
    }

}

/**
 * @class Utils
 * @brief This class extends GlobalVariableManager and contains utility methods for handling data and file operations.
 *        These methods can be used by other classes or directly.
 */
public class Utils extends GlobalVariableManager {

    /**
     * @brief Converts a given path to Unix-style by replacing backslashes with forward slashes.
     * @param path The original file path.
     * @return A Path object with Unix-style separators.
     */
    public static Path createUnixStylePath(String path) {
        String unixStylePath = path.replace("\\\\", "/");
        Path pathObj = Paths.get(unixStylePath); // Path.of() is from JDK 11

        return pathObj;
    }

    /**
     * @brief Creates a File object and ensures that all necessary directories are created.
     * @details The method constructs a file path using global variables and creates any missing
     *          directories in the path. If directory creation fails, an error is logged and null is returned.
     * @param file The name of the file to be created.
     * @return A File object representing the created file, or null if an error occurred.
     */
    public static File createFile(String file) throws IOException {
        Logger.logWriter("Info", "Creating " + file + ".");

        String dfilePath = getCataloguePath() +
                           getRepDataPath() + "results/" +
                           getBuildTag() + "/";
        File dfile = new File(dfilePath + file);

        try {
            if (!dfile.getParentFile().exists() && !dfile.getParentFile().mkdirs()) {
                throw new IOException("Failed to create directories for path: " +
                    dfile.getParentFile().getAbsolutePath());
            }
        } catch (IOException e) {
            Logger.logWriter("Error", "Error creating directories: " + dfilePath);
            e.printStackTrace();
            return null;
        }

        return dfile;
    }

    /**
     * @brief Encodes a string into application/x-www-form-urlencoded format using UTF-8 encoding.
     * @param str The string to be encoded.
     * @return The encoded string.
     * @throws UnsupportedEncodingException If UTF-8 encoding is not supported (unlikely scenario).
     */
    public static String encode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.toString());
    }

    /**
     * @brief Reads a YAML file from the specified path and returns the data as a Map.
     * @details This method reads a YAML file from the provided path (path is converted it to Unix-style format
     *          if necessary) and then loads the content into a Map.
     * @param path The path to the YAML file.
     * @return A Map containing the YAML data.
     * @throws IOException If an I/O error occurs.
     */
    public static Map<String, Object> yamlReader(String path) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(Utils.createUnixStylePath(path))) {
            return yaml.load(inputStream);
        }
    }

    /**
     * @brief Appends a string to a file, ensuring the file uses UTF-8 encoding.
     * @details This method opens the provided file in append mode and writes the given text to it,
     *          adding a new line at the end.
     * @param file The file to write to.
     * @param text The text to append to the file.
     */
    public static void writeToFile(File file, String text) {
        file.withWriterAppend("UTF-8") { writer ->
            writer.write(text + System.lineSeparator());
        }
    }

}

/**
 * @class TruststoreManager
 * @brief Manages the creation, validation, and retrieval of Java KeyStores (JKS) for storing certificates.
 * @details The TruststoreManager class provides methods to create a truststore,
 *          check for the existence of a certificate, and retrieve the truststore path.
 *          It is used primarily for handling certificates from Kubernetes configurations.
 */
public class TruststoreManager {

    /**
     * @brief Retrieves the Certificate Authority (CA) certificate from a Kubernetes configuration file.
     * @param kubeConfigYaml The path to the Kubernetes configuration YAML file.
     * @return A byte array containing the decoded CA certificate.
     */
    private static byte[] getCertificateAuthority(String kubeConfigYaml) {
        // Load kubeconfig file
        Map<String, Object> kubeConfigData = Utils.yamlReader(kubeConfigYaml);

        // Extract the CA certificate data
        String certificateAuthorityData = (String) ((Map<String, Object>) ((List<Map<String, Object>>) kubeConfigData.get("clusters")).get(0).get("cluster")).get("certificate-authority-data");

        // Decode the base64-encoded CA certificate data
        byte[] caCertificateBytes = Base64.getDecoder().decode(certificateAuthorityData);

        return caCertificateBytes;
    }

    /**
     * @brief Creates a Java KeyStore (JKS) and adds the CA certificate retrieved from
     *        the Kubernetes configuration file.
     * @param kubeConfigYaml The path to the Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file will be saved.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
     */
    private static void createTruststore(
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) {
        // Initialize the KeyStore
        KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(null, truststorePassword.toCharArray());

        // Generate the certificate from the retrieved CA certificate bytes
        Certificate caCertificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(getCertificateAuthority(kubeConfigYaml)));

        // Add the CA certificate to the truststore
        truststore.setCertificateEntry(alias, caCertificate);

        File file = new File(truststorePath);
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(truststorePath)) {
            truststore.store(fos, truststorePassword.toCharArray());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Checks the existence of a truststore and its alias.
     *        If not found, creates the truststore and adds the CA certificate.
     * @param kubeConfigYaml The Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file is located or will be created.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
    public static void validateTruststore(
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) {
        Logger.logWriter("Info", "Started checking Thruststore for " + alias + ".");
        try {
            // Create truststore and add CA certificate to if needed
            File truststoreFile = new File(truststorePath);

            if (truststoreFile.exists()) {
                Logger.logWriter("Info", "Truststore file found.");
                KeyStore truststore = KeyStore.getInstance("JKS");

                try (InputStream truststoreInputStream = new FileInputStream(truststorePath)) {
                    truststore.load(truststoreInputStream, truststorePassword.toCharArray());
                } catch (Exception e) {
                    Logger.logWriter("Error", "Error with using custom truststore: " + truststorePath);
                    e.printStackTrace();
                    return;
                }

                // Check if the alias exists in the truststore
                if (truststore.containsAlias(alias)) {
                    Logger.logWriter("Info", "Alias '" + alias + "' found in the truststore.");
                    Certificate certificate = truststore.getCertificate(alias);

                    if (certificate != null) {
                        Logger.logWriter("Info", "Certificate for alias '" + alias + "' found in the truststore.");
                    } else {
                        Logger.logWriter("Warn", "No certificate found for alias '" + alias +
                            "' in the truststore.\nCreating truststore file...")
                        createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
                    }
                } else {
                    Logger.logWriter("Warn", "Alias '" + alias +
                        "' does not exist in the truststore.\nCreating truststore file...");
                    createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
                }
            } else {
                Logger.logWriter("Warn", "Truststore file not found.\nCreating truststore file...");
                createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
            }
        } catch (FileNotFoundException e) {
            Logger.logWriter("Error", "Check if the file does exist and add permission to modify it.\n" + e);
            e.printStackTrace();
            return;
        } catch (Exception e) {
            Logger.logWriter("Error", "Error with creating custom truststore: \n" + e);
            e.printStackTrace();
            return;
        }

        Logger.logWriter("Info", "Finished checking Thruststore for " + alias + ".");
    } */

    /**
     * @brief Checks the existence of a truststore file, alias, and certificate.
     *        If something does not exist, it creates the truststore and
     *        adds the specified alias and CA certificate.
     * @details This method orchestrates the process of verifying the existence of the truststore file,
     *          loading it, checking whether the alias exists, and ensuring the associated certificate
     *          is present. If any of these steps fail, it triggers the creation of a new truststore.
     * @param kubeConfigYaml The Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file is located or will be created.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
     */
    public static void validateTruststore(
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) {
        Logger.logWriter("Info", "Started checking Thruststore for " + alias + ".");

        try {
            File truststoreFile = new File(truststorePath);
            checkTruststoreFileExist(truststoreFile, kubeConfigYaml, truststorePath , truststorePassword, alias);
            KeyStore truststore = loadTruststore(truststorePath, truststorePassword);
            checkTruststoreContainsAlias(truststore, kubeConfigYaml, truststorePath, truststorePassword, alias);
            checkTruststoreContainsCertificate(truststore, kubeConfigYaml, truststorePath, truststorePassword, alias);
        } catch (FileNotFoundException e) {
            Logger.logWriter("Error", "Check if the file does exist and add permission to modify it.\n" + e);
            e.printStackTrace();
            return;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            Logger.logWriter("Error", "Error with using custom truststore: " + truststorePath + "\n" + e);
            e.printStackTrace();
            return;
        } catch (Exception e) {
            Logger.logWriter("Error", "Error with creating custom truststore: \n" + e);
            e.printStackTrace();
            return;
        }

        Logger.logWriter("Info", "Finished checking Thruststore for " + alias + ".");
    }

    /**
     * @brief Checks if the truststore file exists.
     * @details If the truststore file does not exist, a new truststore is created using the provided parameters.
     * @param truststoreFile The truststore file to check for existence.
     * @param kubeConfigYaml The Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file is located or will be created.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
     */
    private static void checkTruststoreFileExist(
        File truststoreFile,
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) {
        if (!truststoreFile.exists()) {
            Logger.logWriter("Warn", "Truststore file not found.\nCreating truststore file...");
            createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
        } else {
            Logger.logWriter("Info", "Truststore file found.");
        }
    }

    /**
     * @brief Loads the truststore from the specified path.
     * @details This method loads the truststore using the provided file path and password.
     * @param truststorePath The path to the truststore file.
     * @param truststorePassword The password to access the truststore.
     * @return A loaded KeyStore instance containing the truststore data.
     * @throws IOException If an I/O error occurs while loading the truststore.
     * @throws KeyStoreException If the KeyStore instance cannot be initialized.
     * @throws NoSuchAlgorithmException If the algorithm used for loading the truststore is not available.
     * @throws CertificateException If there is an issue with the certificate in the truststore.
     */
    private static KeyStore loadTruststore(
        String truststorePath,
        String truststorePassword
    ) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        KeyStore truststore = KeyStore.getInstance("JKS");

        try (InputStream truststoreInputStream = new FileInputStream(truststorePath)) {
            truststore.load(truststoreInputStream, truststorePassword.toCharArray());
        }

        return truststore;
    }

    /**
     * @brief Checks if the truststore contains the specified alias.
     * @details This method checks if the truststore contains the alias.
     *          If the alias does not exist, a new truststore is created.
     * @param truststore The KeyStore instance containing the truststore data.
     * @param kubeConfigYaml The Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file is located or will be created.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
     */
    private static void checkTruststoreContainsAlias(
        KeyStore truststore,
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) throws KeyStoreException {
        boolean containsAlias = truststore.containsAlias(alias);

        if (!containsAlias) {
            Logger.logWriter("Warn", "Alias '" + alias +
                "' does not exist in the truststore.\nCreating new truststore file...");
            createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
        } else {
            Logger.logWriter("Info", "Alias '" + alias + "' found in the truststore.");
        }
    }

    /**
     * @brief Checks if the truststore contains a certificate for the specified alias.
     * @details This method checks if the truststore contains a certificate for the alias.
     *          If no certificate is found, a new truststore is created.
     * @param truststore The KeyStore instance containing the truststore data.
     * @param kubeConfigYaml The Kubernetes configuration YAML file.
     * @param truststorePath The path where the truststore file is located or will be created.
     * @param truststorePassword The password to protect the truststore.
     * @param alias The alias name for the certificate entry in the truststore.
     */
    private static void checkTruststoreContainsCertificate(
        KeyStore truststore,
        String kubeConfigYaml,
        String truststorePath,
        String truststorePassword,
        String alias
    ) {
        Certificate certificate = truststore.getCertificate(alias);

        if (certificate == null) {
            Logger.logWriter("Warn", "No certificate found for alias '" + alias +
                "' in the truststore.\nCreating new truststore file...");
            createTruststore(kubeConfigYaml, truststorePath , truststorePassword, alias);
        } else {
            Logger.logWriter("Info", "Certificate for alias '" + alias + "' found in the truststore.");
        }
    }

    /**
     * @brief Retrieves the path to the truststore file for the specified alias.
     * @param alias The alias name for the certificate entry in the truststore.
     * @return The path to the truststore file.
     */
    public static String getTruststorePath(String alias) {
        return GlobalVariableManager.getCataloguePath() +
            GlobalVariableManager.getRepDataPath() +
            "resources/truststore/" + alias + ".jks";
    }

}

/**
 * @class HttpHandler
 * @brief This class extends GlobalVariableManager and manages HTTP requests and
 *        configurations for SSL context, DNS routing, and API URLs.
 * @details This class provides methods to set up DNS routing, create HTTP clients with custom SSL contexts,
 *          and build URLs for Kubernetes and Prometheus APIs.
 */
public class HttpHandler extends GlobalVariableManager {

    /**
     * @brief Configures DNS routing for a specific domain name to a given server IP.
     * @param domainName The domain name to route.
     * @param serverIP The IP address of the server.
     */
    public static void dnsRouting(String domainName, String serverIP) {
        System.setProperty("sun.net.spi.nameservice.provider.1", "dns,static");
        System.setProperty("sun.net.spi.nameservice.provider.1.dns", "dns,sun");
        System.setProperty("sun.net.spi.nameservice.provider.1.static", domainName + "=" + serverIP);
    }

    /**
     * @brief Creates an HTTP client with a custom SSL context using the provided truststore.
     * @param truststorePath The file path to the truststore.
     * @param truststorePassword The password for the truststore.
     * @return A CloseableHttpClient configured with the custom SSL context.
     */
    public static CloseableHttpClient customSSLContextHttpClient(
        String truststorePath,
        String truststorePassword
    ) {
        KeyStore truststore = KeyStore.getInstance("JKS");

        try (InputStream truststoreInputStream = new FileInputStream(truststorePath)) {
            truststore.load(truststoreInputStream, truststorePassword.toCharArray());
        } catch (Exception e) {
            Logger.logWriter("Error", "Error with using custom truststore: \n" + e);
            e.printStackTrace();
            return;
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                                                  TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(truststore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

        /* Configure HTTP Client
           Even with bad internet and large amount of infomation, the 5 sec must be enough
           to get data from Prometheus and, moreover, from Kubernetes. */
        RequestConfig config = RequestConfig.custom()
                                            .setSocketTimeout(5000)
                                            .setConnectTimeout(5000)
                                            .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                                                    .setSSLContext(sslContext)
                                                    // self-signed certs so disabling Hostname verification
                                                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                                    .setDefaultRequestConfig(config)
                                                    .build();

        return httpClient;
    }

    /**
     * @brief Retrieves the Kubernetes API URL using the kubeconfig data.
     * @return The Kubernetes API URL as a string.
     */
    public static String getKubeApiURL() {
        Map<String, Object> kubeConfigData = Utils.yamlReader(getKubeConfigYaml());
        String kubeHost = (String) ((Map<String, Object>) ((List<Map<String, Object>>) kubeConfigData.get("clusters"))
            .get(0).get("cluster")).get("server");

        return kubeHost + "/api/v1/namespaces/" + getNamespace() + "/pods";
    }

    /**
     * @brief Constructs the Prometheus API URL for querying metrics.
     * @param query The Prometheus query string.
     * @return The Prometheus API URL as a string.
     */
    public static String getPromApiURL(String query) {
        return "https://" + getPromDomainName() + "/api/v1/query_range?query=" + query +
            "&start=" + Utils.encode(GlobalTimeManager.getDateTimeStart()) +
            "&end=" + Utils.encode(GlobalTimeManager.getDateTimeEnd()) +
            "&step=" + Utils.encode(getPromStep()) + "s";
    }

    /**
     * @brief Builds a List of Strings of URL encoded Prometheus resource queries for
     *        metrics and limits (CPU and Memory) for a pod in namespace.
     * @param namespace The Kubernetes namespace.
     * @param podName The name of the pod.
     * @return The List of resource metric names.
     * @throws UnsupportedEncodingException If the encoding operation fails.
     * @see #buildMetricsList().
     */
    public static List<String> buildResourceQueries(
        String namespace,
        String podName
    ) throws UnsupportedEncodingException {
        List<String> resourceQueriesList = new ArrayList<>();
        String query;

        // CPU metric (in milicores)
        query = "sum(node_namespace_pod_container:container_cpu_usage_seconds_total:sum_irate{" +
            "namespace=\"" + namespace +"\", " + 
            "pod=\"" + podName + "\", " + 
            "container!=\"\"}) by (container) * 1000";
        resourceQueriesList.add(Utils.encode(query));

        // CPU limit (in milicores)
        query = "sum(kube_pod_container_resource_limits{" +
            "namespace=\"" + namespace + "\", " +
            "pod=\"" + podName + "\", " +
            "resource=\"cpu\"}) * 1000";
        resourceQueriesList.add(Utils.encode(query));

        // Memory metric (in megabytes)
        query = "sum(container_memory_working_set_bytes{" +
            "namespace=\"" + namespace +"\", " +
            "pod=\"" + podName + "\", " +
            "container!=\"\"}) by (container) / (1024 * 1024)";
        resourceQueriesList.add(Utils.encode(query));

        // Memory limit (in megabytes)
        query = "sum(kube_pod_container_resource_limits{" +
            "namespace=\"" + namespace + "\", " +
            "pod=\"" + podName + "\", " +
            "resource=\"memory\"}) / (1024 * 1024)";
        resourceQueriesList.add(Utils.encode(query));

        return resourceQueriesList;
    }

    /**
     * @brief Builds Prometheus resource queries' List of metrics names.
     * @return The List of Strings of resource metric names.
     * @see #buildResourceQueries(namespace, podName).
     */
    public static List<String> buildMetricsList() {
        return Arrays.asList("CPU", "CPU Limit", "Memory", "Memory Limit");
    }

}

/**
 * @class KubeWorker
 * @brief Provides helper methods for Kubernetes response validation, parsing and
 *        working with pod data.
 * @details This class contains methods to extract and format specific information from
 *          Kubernetes response on pod data.
 */
public class KubeWorker {

    /**
     * @brief LinkedHashMap of values for each pod in Kubernetes cluster's namespace.
     * @details Keys: pod's App Name
     *          Values: Image Name, CPU, Memory, Restarts number
     */
    private static LinkedHashMap<String, LinkedHashMap<String, String>> podDataMap = new LinkedHashMap<>();
    /**
     * @brief List of pod names from namespace on Kubernetes cluster.
     */
    private static List<String> podNamesList = new ArrayList<>();

    /**
     * @brief Handles the HTTP response from the Kubernetes API.
     * @details This method processes the response and throws appropriate exceptions if the response 
     *          status is invalid, the content is empty, or any required values are missing.
     *          As the end result does populate "podDataMap" with pod's information data and
     *          "podNamesList" with pod's App Names.
     * @param response The HTTP response received from the Kubernetes API.
     * @throws InvalidResponseStatusException if the HTTP response status is invalid.
     * @throws EmptyContentException if the HTTP response body is empty.
     * @throws MissingValueException if any expected values are missing from the HTTP response body.
     */
    public static void responseHandling(
        HttpResponse response
    ) throws InvalidResponseStatusException, EmptyContentException, MissingValueException {
        Object responseJSON = responseValidation(responseStatus(response), responseBody(response));
        itemsIterator(responseJSON);
    }

    /**
     * @brief Get status code from the Kubernetes API response.
     * @param response The HTTP response received from the Kubernetes API.
     * @return Integer representation of response's status code.
     */
    private static Integer responseStatus(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    /**
     * @brief Get body from the Kubernetes API response.
     * @param response The HTTP response received from the Kubernetes API.
     * @return String representation of response's body.
     */
    private static String responseBody(HttpResponse response) {
        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    }

    /**
     * @brief Validates JSON against certain parameter requirements.
     * @param result HTTP response JSON object.
     * @return Response JSON Object on validation success.
     */
    private static Object responseValidation(
        Integer responseStatus,
        String responseBody
    ) throws InvalidResponseStatusException, EmptyContentException, MissingValueException {
        if (responseStatus < 200 || responseStatus >= 300) {
            throw new InvalidResponseStatusException("HTTP Request to Kubernetes API failed\n" +
                "   for URL: " + (HttpHandler.getKubeApiURL() ?: "N/A") + "\n" +
                "   with status code: " + (responseStatus ?: "N/A") + "\n" +
                "   and response body: " + (responseBody ?: "N/A"));
        }

        if (responseStatus.equals("204") || responseBody.isEmpty()) {
            throw new EmptyContentException("HTTP Request to Kubernetes API returned empty body\n" +
                "   for URL: " + (HttpHandler.getKubeApiURL() ?: "N/A"));
        }

        Object responseJSON = responseToJSON(responseBody);

        if (responseJSON.items[0] == null) {
            throw new MissingValueException("HTTP Request to Kubernetes API returned no pod information\n" +
                "   for URL: " + (HttpHandler.getKubeApiURL() ?: "N/A") + "\n" +
                "   Check if cluster and namespace deployments are working properly!");
        }

        return responseJSON;
    }

    /**
     * @brief Parses Kubernetes API response body into a JSON format.
     * @param responseBody String representation of Kubernetes API response body.
     * @return Response Response body parsed to JSON Object format.
     */
    private static Object responseToJSON(String responseBody) {
        return new groovy.json.JsonSlurper().parseText(responseBody);
    }

    /**
     * @brief Does retrieve values from pods to parse and store them for further usage.
     * @details Iterates over every pod data object and parses them to populate
     *          "podDataMap" Map of pods' values and "podNamesList" List of pods' App Names for further use.
     * @param responseJSON Kubernetes API response body parsed to JSON Object format.
     */
    private static void itemsIterator(Object responseJSON) {
        responseJSON.items.each { item ->
            List<String> podValues = new ArrayList<>(kubePodData(item));
            kubePodValues(podValues);
        }
    }

    /**
     * @brief Parses pod data from a Kubernetes API response.
     * @details Extracts and processes pod information such as the pod name, application name,
     *          image name, CPU and memory limits, and restart count.
     * @param item The Kubernetes pod data object.
     * @return A list of strings containing the parsed pod data.
     */
    private static List<String> kubePodData(Object item) {
        String podNameFull = item.metadata.name as String;

        String podAppNameFull = item.metadata.labels.app as String;
        String[] nameParts = podAppNameFull.minus(GlobalVariableManager.getNamespace()).split("--");
        String podAppName = (nameParts.length > 1) ? nameParts[1] : nameParts[0];

        String podImageNameFull = item.spec.containers[0].image as String;
        String[] imageParts = podImageNameFull.split(":");
        String podImageName = (imageParts.length > 1) ? imageParts[1] : imageParts[0];

        String podCPU = item.spec.containers[0].resources.limits.cpu as String;
        String podMemory = item.spec.containers[0].resources.limits.memory as String;

        String podRestarts = item.status.containerStatuses[0].restartCount;

        return Arrays.asList(
            podNameFull,
            podAppName,
            podImageNameFull,
            podImageName,
            podCPU,
            podMemory,
            podRestarts
        );
    }

    /**
     * @brief Parses values from pod object to store for further use.
     * @details Parses values obtained from pod object and populates "podDataMap" Map of pods' values
     *          and "podNamesList" List of pods' App Names for further use.
     * @param podValues A List of values obtained from the pod object.
     */
    private static void kubePodValues(List<String> podValues) {
        LinkedHashMap<String, String> podData = new LinkedHashMap<>();

        podData.put(podValues.get(1),
                    podValues.get(3) + ';' +
                    podValues.get(4) + ';' +
                    podValues.get(5) + ';' +
                    podValues.get(6));
        KubeWorker.podDataMap.put(podValues.get(1), podData);
        KubeWorker.podNamesList.add(podValues.get(0));

        Logger.logWriter("Info", "Namespace pod information:" + "\n" +
                                 "  pod full name: " + podValues.get(0) + "\n" +
                                 "  pod app name: " + podValues.get(1) + "\n" +
                                 "  pod image: " + podValues.get(2) + "\n" +
                                 "  pod cpu: " + podValues.get(4) + "\n" +
                                 "  pod memory: " + podValues.get(5) + "\n" +
                                 "  pod restarts: " + podValues.get(6));
    }

    /**
     * @brief Retrieves the LinkedHashMap of pods information data.
     * @return A LinkedHashMap of pods information data.
     */
    public static LinkedHashMap<String, LinkedHashMap<String, String>> getPodDataMap() {
        return podDataMap;
    }

    /**
     * @brief Retrieves the list of pod names.
     * @return A List of pod names as Strings.
     */
    public static List<String> getPodNamesList() {
        return podNamesList;
    }

}

/**
 * @class PromWorker
 * @brief Provides helper methods for Prometheus response validation, parsing and
 *        working with resources data.
 * @details This class contains methods to extract and format specific information from
 *          Prometheus response data.
 */
public class PromWorker {

    /**
     * @brief Handles the HTTP response from the Prometheus API.
     * @details This method processes the response and stores
     *          Throws appropriate exceptions if the response status is invalid,
     *          the content is empty, or any required values are missing.
     *          On success, it parses the response and returns a list
     *          of query results in the format: time;value.
     *          - time: ISO 8601 formatted timestamp.
     *          - value: Floating-point number as a string.
     * @param response The HTTP response received from the Prometheus API.
     * @return A list of query results with each entry formatted as time;value.
     * @throws InvalidResponseStatusException if the HTTP response status is invalid.
     * @throws EmptyContentException if the HTTP response body is empty.
     * @throws MissingValueException if any expected values are missing from the HTTP response body.
     */
    public static List<String> responseHandling(
        HttpResponse response,
        String query,
        int componentIndex
    ) throws InvalidResponseStatusException, EmptyContentException, MissingValueException {
        Object responseJSON = responseValidation(
                                  responseStatus(response),
                                  responseBody(response),
                                  query,
                                  componentIndex
                              );

        return addValuesToList(responseJSON);
    }

    /**
     * @brief Get status code from the Prometheus API response.
     * @param response The HTTP response received from the Prometheus API.
     * @return Integer representation of response's status code.
     */
    private static Integer responseStatus(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    /**
     * @brief Get body from the Prometheus API response.
     * @param response The HTTP response received from the Prometheus API.
     * @return String representation of response's body.
     */
    private static String responseBody(HttpResponse response) {
        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    }

    /**
     * @brief Validates JSON against certain parameter requirements.
     * @param result HTTP response JSON object.
     * @return Response JSON Object on validation success.
     */
    private static Object responseValidation(
        Integer responseStatus,
        String responseBody,
        String query,
        int componentIndex
    ) throws InvalidResponseStatusException, EmptyContentException, MissingValueException {
        if (responseStatus < 200 || responseStatus >= 300) {
            throw new InvalidResponseStatusException("HTTP Request to Prometheus API failed\n" +
                "   for URL: " + (HttpHandler.getPromApiURL(query) ?: "N/A") + "\n" +
                "   with status code: " + (responseStatus ?: "N/A") + "\n" +
                "   and response body: " + (responseBody ?: "N/A"));
        }

        if (responseStatus.equals("204") || responseBody == null || responseBody.isEmpty()) {
            throw new EmptyContentException("HTTP Request to Prometheus API returned empty body\n" +
                "    for URL: " + (HttpHandler.getPromApiURL(query) ?: "N/A"));
        }

        Object responseJSON = responseToJSON(responseBody);

        // On pod boot/restart error or any other case when result matrix is being empty
        if (responseJSON?.data?.result[0]?.values[0] == null) {
            throw new MissingValueException("\"" + DataStore.getComponentsList().get(componentIndex) +
                "\" component has empty results for URL: " + (HttpHandler.getPromApiURL(query) ?: "N/A") + "\n" +
                "Check if pod is working properly!");
        }

        return responseJSON;
    }

    /**
     * @brief Parses Prometheus API response body into a JSON format.
     * @param responseBody String representation of Kubernetes API response body.
     * @return Response Response body parsed to JSON Object format.
     */
    private static Object responseToJSON(String responseBody) {
        return new groovy.json.JsonSlurper().parseText(responseBody);
    }

    /**
     * @brief Iterates through values in response JSON Object to get time and resource value
     *        to parse them and store into a List of Strings.
     *        - time is ISO 8601 String.
     *        - value is floating-point number String.
     * @param responseJSON Prometheus API response body parsed to JSON Object format.
     * @return A List of Strings with concatenated time and resource value with ";" as a delimiter.
     */
    private static List<String> addValuesToList(
        Object responseJSON
    ) {
        List<String> valuesList = new ArrayList<>();

        responseJSON.data.result[0].values.each { item ->
            valuesList.add(PromWorker.promPodResourceDataProcess(item));
        }

        return valuesList;
    }

    /**
     * @brief Formats a Prometheus pod resource data.
     * @details Converts the raw time and value data from a Prometheus query result into a
     *          formatted string: time;value.
     *          - time is ISO 8601 String.
     *          - value is floating-point number String.
     * @param item The Prometheus query result containing time and value data.
     * @return A formatted string containing the timestamp and corresponding value.
     */
    private static String promPodResourceDataProcess(Object item) {
        String rawTime = item[0].toString() as String;
        String time = new Date(rawTime.toLong() * 1000).format("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String value = item[1].toString() as String;

        return time + ";" + value;
    }

}

/**
 * @class InfluxWorker
 * @brief Provides helper methods for Influx DB response validation and parsing.
 * @details This class contains methods to extract and format specific information from
 *          Influx DB response.
 */
public class InfluxWorker {

    /**
     * @brief Handles the HTTP response from the Influx API.
     * @details This method validates the response and logs successful data sending.
     *          Throws exception if the response status is invalid.
     * @param response The HTTP response received from the Prometheus API.
     * @param payload A POST request's body sent to Influx API.
     * @throws InvalidResponseStatusException if the HTTP response status is invalid.
     */
    public static void responseHandling(
        HttpResponse response,
        String payload
    ) throws InvalidResponseStatusException {
        responseValidation(responseStatus(response), responseBody(response), payload);

        Logger.logWriter("Info", "Sent data to InfluxDB:\n" +
            "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
            "    Payload: " + (payload ?: "N/A") + "\n" +
            "    Status: " + (responseStatus(response) ?: "N/A") + "\n" +
            "    Body: " + (responseBody(response) ?: "N/A"));
    }

    /**
     * @brief Validates JSON against certain parameter requirements.
     * @param result HTTP response JSON object.
     */
    private static void responseValidation(
        Integer responseStatus,
        String responseBody,
        String payload
    ) throws InvalidResponseStatusException {
        if (responseStatus != 204 && responseStatus != 200) {
            throw new InvalidResponseStatusException("Problem with sending data to InfluxDB:\n" +
                "   for URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
                "   for payload: " + (payload ?: "N/A") + "\n" +
                "   with status code: " + (responseStatus ?: "N/A") + "\n" +
                "   and response body: " + (responseBody ?: "N/A"));
        }
    }

    /**
     * @brief Get status code from the Influx API response.
     * @param response The HTTP response received from the Influx API.
     * @return Integer representation of response's status code.
     */
    private static Integer responseStatus(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    /**
     * @brief Get body from the Influx API response.
     * @param response The HTTP response received from the Influx API.
     * @return String representation of response's body or empty String if response entity is null.
     */
    private static String responseBody(HttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }

        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.logWriter("Error", "Error reading response body: " + e.getMessage());
            return "";
        }
    }

    /**
     * 
     */
    public static String payloadBuilder(
        Map.Entry<String, List<String>> entry,
        String componentKey
    ) {
        String header = entry.getKey();
        List<String> dataValues = entry.getValue();
        StringBuilder payloadBuilder = new StringBuilder();

        for (String value : dataValues) {
            String[] parts = value.split(";");

            if (parts.length < 2) {
                Logger.logWriter("Error", "Skipping entry due to missing fields: " + value);
                continue;
            }


            String time_s = String.valueOf(GlobalTimeManager.adjustToGMT(parts[0]).getEpochSecond());
            String fields = constructFields(header, value);

            if (fields.isEmpty()) {
                Logger.logWriter("Error", "Skipping entry with empty fields: " + value);
                continue;
            }

            if (payloadBuilder.length() > 0) {
                payloadBuilder.append("\n");
            }

            payloadBuilder.append(LocalVariableManager.getInfluxDBMeasurement())
                          .append(",Component=").append(componentKey)
                          .append(" ").append(fields)
                          .append(" ").append(time_s);
        }

        return payloadBuilder.toString();
    }

    /**
     * 
     */
    private static String constructFields(
        String header,
        String value
    ) {
        String fields = "";
        String[] headers = header.split(";");

        for (int i = 1; i < headers.length; i++) {
            fields += (i == 1 ? "" : ",") + headers[i] + "=" + value.split(";")[i];
        }

        return fields;
    }

}

/**
 * @class DataTransformer
 * @brief The main goal is to manage and organize metrics with their corresponding 
 *        limit values into a new format, making it easier for downstream processing or 
 *        analysis.
 *        All operations are done inside one map structure (Map<String, List<String>>).
 * @detail Transforms values and their corresponding limits data into a CSV format.
 *         Via exclusion does gather keys without limit data after processing
 *         to transform their data into CSV format but without limits.
 *         Deletes all original keys that were used for combining data for clean up.
 *         As an end result, the merged data map will be populated with new
 *         combined data.
 * - Main Operations:
 *     1. Limit Association: 
 *         - For each key-value pair in the map, if the key ends with "Limit", 
 *           the method attempts to find a corresponding metric entry (without 
 *           the "Limit" suffix). 
 *         - The metric and its limit are then combined into a formatted string 
 *           (CSV-like format), which includes both the metric and its limit 
 *           value.
 *     2. Logging:
 *         - During processing, the method logs the progress and any issues 
 *           encountered, such as missing corresponding dataset entries.
 *     3. Key Management:
 *         - Once the metric and its limit have been combined, the original 
 *           entries (both the metric and the limit) are marked for removal.
 *     4. Handling Keys Without Limits:
 *         - Any metrics that do not have corresponding limit values are 
 *           processed separately, ensuring they are included in the final map 
 *           with an appropriate key format.
 *     5. Cleanup:
 *         - After processing, all original keys that were used for combining 
 *           data (either metrics or limits) are removed from the map to avoid 
 *           duplication or confusion.
 */
public class DataTransformer {

    /**
     * 
     */
    private static LinkedHashMap<String, List<String>> mergedDataMap = new LinkedHashMap<>();

    /**
     * @brief Transforms the merged data by processing limits and corresponding metrics.
     * @detail This method orchestrates the transformation of the provided merged data map.
     *         It processes entries with limit values, handles keys without corresponding limits,
     *         and removes processed entries from the map.
     * @param mergedData A map containing keys and lists of strings representing metric values.
     */
    public static void transformData(Map<String, List<String>> mergedData) {
        List<String> keysToRemove = new ArrayList<>();
        Set<String> keysWithoutLimit = new HashSet<>(mergedData.keySet());

        for (Map.Entry<String, List<String>> entry : new LinkedHashMap<>(mergedData).entrySet()) {
            String dataKey = entry.getKey();
            List<String> dataValues = entry.getValue();

            if (dataKey.endsWith(" Limit")) {
                processLimitData(mergedData, keysToRemove, dataKey, dataValues);
            }
        }

        processKeysWithoutLimit(mergedData, keysWithoutLimit, keysToRemove);
        removeUsedEntries(mergedData, keysToRemove);
    }

    /**
     * @brief Processes limit data and their corresponding metric values.
     * @detail This method processes entries in the merged data map that have corresponding limit values.
     *         It prepares new entries with both metric and limit values and marks the processed entries for removal.
     * @param mergedData The map containing the merged data.
     * @param keysToRemove A list of keys that should be removed after processing.
     * @param dataKey The current key being processed.
     * @param dataValues The list of data values associated with the current key.
     */
    private static void processLimitData(
        Map<String, List<String>> mergedData,
        List<String> keysToRemove,
        String dataKey,
        List<String> dataValues
    ) {

        String mainKey = dataKey.replace(" Limit", "");
        if (mergedData.containsKey(mainKey)) {
            List<String> mainMetricValues = mergedData.get(mainKey);
            List<String> formattedMetricValues = new ArrayList<>();
            int idx = 0;

            // !!! number of values can be less than number of limits => index out of bounds exception
            // need to log it
            if (dataValues.size() != mainMetricValues.size()) {
                Logger.logWriter("Warn", "Number of Limit values and Metric values are not equal:" +
                    "\n    (check if the pod was working for the whole duration of the test)" +
                    "\n    with number of limit values: " + dataValues.size() +
                    "\n    with number of metric values: " + mainMetricValues.size());
            }
            try {
                for (String value : dataValues) {
                    /*
                    Logger.logWriter("Info", "Limit value '" +
                        (value != null ? value : "N/A") +
                        "' (" + (idx + 1) + " out of " + dataValues.size() + ")");
                    Logger.logWriter("Info", "Metric value '" +
                        (mainMetricValues.get(idx) != null ? mainMetricValues.get(idx) : "N/A") +
                        "' (" + (idx + 1) + " out of " + mainMetricValues.size() + ")");
                    */

                    // Extract limit value without timestamp
                    String limitValue = value.substring(value.indexOf(";") + 1);

                    // Prepare the data for CSV format
                    formattedMetricValues.add((idx > mainMetricValues.size()
                        ? ""
                        : mainMetricValues.get(idx)) + ";" + limitValue);

                    idx++;
                }
            } catch (IndexOutOfBoundsException e) {
                Logger.logWriter("Error", "Error with Metric or Limit value parsing in: " + (dataKey != null ? dataKey : "N/A") +
                    "\n    with limit values list: " + dataValues +
                    "\n    with metric values list: " + mainMetricValues +
                    "\n" + e);
                e.printStackTrace();
            }
            // Create a new entry
            mergedData.put("Time;" + mainKey + ";" + mainKey + "_Limit", formattedMetricValues);
        } else {
            Logger.logWriter("Warn", "\"" + dataKey +
                "\" limit data doesn't have a corresponding dataset entry. Check if pod is working properly!");
        }
        // Used entries to remove
        keysToRemove.add(mainKey);
        keysToRemove.add(dataKey);
    }

    /**
     * @brief Handles keys without corresponding limit data.
     * @detail This method identifies and processes keys in the mergedData map that
     *         do not have corresponding limit data.
     *         It creates new entries for these keys and marks them for removal after processing.
     * @param mergedData The map containing the merged data.
     * @param keysWithoutLimit A set of keys that do not have corresponding limit data.
     * @param keysToRemove A list of keys that should be removed after processing.
     */
    private static void processKeysWithoutLimit(Map<String, List<String>> mergedData,
        Set<String> keysWithoutLimit, List<String> keysToRemove) {

        /* Remove all keys which where already transformed
           (values were concated with their corresponding limits) */
        keysWithoutLimit.removeAll(keysToRemove);
        for (String key : keysWithoutLimit) {
            List<String> mainMetricValues = mergedData.get(key);
            // Create a new entry
            mergedData.put("Time;" + key, mainMetricValues);
            // Entries to remove
            keysToRemove.add(key);
        }
    }

    /**
     * @brief Removes used entries from the merged data map.
     * @detail This method removes the entries from the mergedData map that have been marked for removal 
     *         after processing limit and metric values (clean up to have only processed/combined data).
     * @param mergedData The map containing the merged data.
     * @param keysToRemove A list of keys that should be removed from the map.
     */
    private static void removeUsedEntries(Map<String, List<String>> mergedData, List<String> keysToRemove) {
        // Remove the used entries
        for (String key : keysToRemove) {
            mergedData.remove(key);
        }
    }

    /**
     * @brief .
     * @param x .
     * @return .
     */
    public static LinkedHashMap<String, List<String>> getMergedDataMap() {
        return mergedDataMap;
    }

}

/**
 *
 */
public class DataStore {

    /**
     * 
     */
    private static List<String> componentsList;

    /**
     * 
     */
    public static void storeGeneralInfoToCSV() {
        // Create CSV store file for General Test Information and write a header
        File file = Utils.createFile("GeneralInfo.csv");
        Utils.writeToFile(file, "Type;Name;Value;CPU;Memory;Restarts");

        // Write pod's data to General Test Information CSV file
        DataStore.componentsList = writeComponentDataToFile(file, KubeWorker.getPodDataMap());
        // Write timings and configuration data to CSV store
        writeTestConfigDataToFile(file);
    }

    /**
     * 
     */
    public static void storeComponentDataToCSV(
        LinkedHashMap<String, List<String>> mergedDataMap,
        int index
    ) {
        for (Map.Entry<String, List<String>> entry : new LinkedHashMap<>(mergedDataMap).entrySet()) {
            String dataKey = entry.getKey();
            List<String> dataValues = entry.getValue();

            File csvFile = Utils.createFile("Resources/" + DataStore.componentsList.get(index) +
                                            "_" + dataKey.split(";")[1] + ".csv");

            // Write header
            Utils.writeToFile(csvFile, dataKey);
            // Write resource metrics data to CSV
            for (String value : dataValues) {
                try {
                    // Split the CSV line
                    String[] parts = value.split(";");

                    // Adjust the metrics timestamp to GMT
                    parts[0] = GlobalTimeManager.formatInstantToISO8601(GlobalTimeManager.adjustToGMT(parts[0]));
                    String updatedLine = String.join(";", parts);

                    Utils.writeToFile(csvFile, updatedLine);
                } catch (Exception e) {
                    Logger.logWriter("Error", "Failed to process line: " + value + " due to: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 
     */
    public static List<String> getComponentsList() {
        return componentsList;
    }

    /**
     * @brief Writes component data to a file from a dataset and returns a list of component names.
     * @details This method processes a dataset containing component information, writes it to a file,
     *          and returns a list of component names found in the dataset.
     * @param file The file object to which component data will be written.
     * @param podDataSet A LinkedHashMap containing component data with keys and their associated values.
     * @return A list of component names extracted from the dataset.
     */
    private static List<String> writeComponentDataToFile(
        File file,
        LinkedHashMap<String, LinkedHashMap<String, String>> podDataSet
    ) {
        Logger.logWriter("Info", "Writing component data to " + file + ".");

        List<String> components = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> set : podDataSet.entrySet()) {
            String podDataSetKey = set.getKey();
            components.add(podDataSetKey);
            LinkedHashMap<String, String> podDataSetValue = set.getValue();

            for (Map.Entry<String, String> entry : podDataSetValue.entrySet()) {
                String podDataKey = entry.getKey();
                String podDataValue = entry.getValue();

                Utils.writeToFile(file, "Component;" + podDataKey + ";" + podDataValue);
            }
        }
        return components;
    }

    /**
     * 
     */
    private static void writeTestConfigDataToFile(File file) {
        Utils.writeToFile(file, "TestInfo;Test start;" + GlobalTimeManager.getDateTimeStart());
        Utils.writeToFile(file, "TestInfo;Test maximum load;" + GlobalTimeManager.getDateTimeMaxload());
        Utils.writeToFile(file, "TestInfo;Test finish;" + GlobalTimeManager.getDateTimeEnd());
        Utils.writeToFile(file, "Configuration;Agents;" + GlobalVariableManager.getConcurrentAgentNumber());
        Utils.writeToFile(file, "Configuration;Agent RampUp;" +
            (Integer.valueOf(GlobalVariableManager.getAgentRampUp()) /
            Integer.valueOf(GlobalVariableManager.getConcurrentAgentNumber())).toString());
        Utils.writeToFile(file, "Configuration;Clients;" + GlobalVariableManager.getConcurrentClientNumber());
        Utils.writeToFile(file, "Configuration;Client RampUp;" +
            (Integer.valueOf(GlobalVariableManager.getClientRampUp()) /
            Integer.valueOf(GlobalVariableManager.getConcurrentClientNumber())).toString());

    }

}

/**
 * Initializes global variables in @GlobalVariableManager with values passed from JMeter script parameters.
 * 
 * @param args the parameters passed from JMeter script, which should contain
 *             mandatory information like catalogue paths, Kubernetes server details,
 *             and Prometheus configurations.
 */
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "",   // cataloguePath
    args.size() > 1 ? args[1] : "",   // repDataPath
    args.size() > 2 ? args[2] : "",   // buildTag
    args.size() > 3 ? args[3] : "",   // kubeConfigPath
    args.size() > 4 ? args[4] : "",   // kubeConfigYaml
    args.size() > 5 ? args[5] : "",   // kubeDomainName
    args.size() > 6 ? args[6] : "",   // kubeServerIP
    args.size() > 7 ? args[7] : "",   // kubeApiAuthToken
    args.size() > 8 ? args[8] : "",   // namespace
    args.size() > 9 ? args[9] : "",   // promDomainName
    args.size() > 10 ? args[10] : "", // promServerIP
    args.size() > 11 ? args[11] : "", // promStep
    args.size() > 12 ? args[12] : "", // influxdbUrl
    args.size() > 13 ? args[13] : "", // concurrentAgentNumber
    args.size() > 14 ? args[14] : "", // agentRampUp
    args.size() > 15 ? args[15] : "", // concurrentClientNumber
    args.size() > 16 ? args[16] : "", // clientRampUp
);

/**
 * Checks if all required arguments are provided. If not, stops the script execution and logs an error.
 * Note: by design, all the parameters are mandatory.
 *
 * @param args the arguments passed to the script.
 */
final int EXPERCTED_ARGS_LEN = 17;
if (args.size() < EXPERCTED_ARGS_LEN) {
    Logger.logWriter("Error", "Skipping provisioning: " +
        "one or more parameters were not passed from User Variables:\n" +
        args);

    return;
}

/**
 * Sets the start, end, and load timings for the test.
 * 
 * The timings are calculated based on the JMeter properties and system time.
 * This data is then passed to the GlobalTimeManager for later reference.
 * Note: "props" only works inside the main script body scope.
 *
 * @param props the JMeter properties containing start time and other relevant timings.
 */
Integer tStartInt = (Integer) ((props.get("START.MS") as long) / 1000L);
String tStart = Instant.ofEpochSecond(tStartInt).toString();

Integer tEndInt = (Integer) (new Date().getTime() / 1000);
props.put("Test End", (Integer) tEndInt);
String tEnd = Instant.ofEpochSecond(tEndInt).toString();

String tLoad;
if (props.get("Test Max load") instanceof Integer) {
    tLoad = Instant.ofEpochSecond((Integer) props.get("Test Max load")).toString();
} else {
    // Set test end timing if max load wasn't reached.
    tLoad = tEnd;
    Logger.logWriter("Warn", "Test hasn't reached maximum load.");
}

GlobalTimeManager.setTimings(
    tStart, // dateTimeStart
    tLoad,  // dateTimeMaxload
    tEnd    // dateTimeEnd
);

    // MAIN FLOW START
Logger.logWriter("Info", "Started General Information and Resources Data provisioning.\n" +
    "Timings data:\n" +
    "   Test Start: " + GlobalTimeManager.getDateTimeStart() + "\n" +
    "   Client RampUp: " + GlobalVariableManager.getClientRampUp() + "\n" +
    "   Agent RampUp: " + GlobalVariableManager.getAgentRampUp() + "\n" +
    "   Test Max load: " + GlobalTimeManager.getDateTimeMaxload() + "\n" +
    "   Test End: " + GlobalTimeManager.getDateTimeEnd()
);

/**
 * Sends a request to the Kubernetes API to retrieve pods data.
 * 
 * Checks the Kubernetes truststore for the necessary certificates and aliases.
 * If not, creates a new truststore with needed data for custom SSL connection
 * using Kubernetes config yaml file.
 * Maps DNS routing between Kubernetes domain name and IP address.
 * Then, it performs an HTTP GET request to retrieve the data,
 * handling response validation, processing, and various exceptions that may arise.
 * Note: for safety reasons the authToken must be used from the Service Account with all
 *       nessesary permissions
 *       authToken MUST NOT be taken from the real user!
 * 
 * @throws InvalidResponseStatusException if the response status is invalid.
 * @throws EmptyContentException if the response content is empty.
 * @throws MissingValueException if any expected values are missing from the response.
 * @throws IOException if an error occurs during HTTP request execution.
 */
Logger.logWriter("Info", "kubernetes API block.");
TruststoreManager.validateTruststore(
    GlobalVariableManager.getKubeConfigYaml(),
    LocalVariableManager.getKubeTruststorePath(),
    LocalVariableManager.getKubeTruststorePassword(),
    LocalVariableManager.getKubeAlias()
);

HttpHandler.dnsRouting(
    GlobalVariableManager.getKubeDomainName(),
    GlobalVariableManager.getKubeServerIP()
);

Logger.logWriter("Info", "Started receiving data from the kubernetes API.");
try (CloseableHttpClient httpClient = HttpHandler.customSSLContextHttpClient(
        LocalVariableManager.getKubeTruststorePath(),
        LocalVariableManager.getKubeTruststorePassword()
    )
) {
    HttpGet httpGet = new HttpGet(HttpHandler.getKubeApiURL());
    httpGet.addHeader("Authorization", "Bearer " + GlobalVariableManager.getKubeApiAuthToken());
    HttpResponse response = httpClient.execute(httpGet);
    KubeWorker.responseHandling(response);
} catch (InvalidResponseStatusException | EmptyContentException | MissingValueException e) {
    Logger.logWriter("Error", String.valueOf(e));
    e.printStackTrace();
    return;
} catch (Exception e) {
    Logger.logWriter("Error", "Error with http request sending with URL: " + HttpHandler.getKubeApiURL() + "\n" + e);
    e.printStackTrace();
    return;
}

Logger.logWriter("Info", "Finished receiving data from the kubernetes API.");
Logger.logWriter("Info", "Started storing General Test Information.");

DataStore.storeGeneralInfoToCSV();

Logger.logWriter("Info", "Finished storing General Test Information.");

/**
 * Sends requests to Prometheus API to retrieve metrics data.
 * 
 * Checks the Prometheus truststore for the necessary certificates and aliases.
 * If not, creates a new truststore with needed data for custom SSL connection
 * using Kubernetes config yaml file.
 * Maps DNS routing between Prometheus domain name and IP address.
 * Then iterates over all available pods builds resource queries, and sends
 * an HTTP GET request to Prometheus for each one.
 * Handles response validation, processing, parsed data storage in CSV files 
 * and various exceptions that may arise.
 *
 * @throws UnsupportedEncodingException if the URL encoding fails.
 * @throws InvalidResponseStatusException if the Prometheus API returns an invalid status.
 * @throws EmptyContentException if the Prometheus response is empty.
 * @throws MissingValueException if expected values are missing in the response.
 */
Logger.logWriter("Info", "Prometheus API block.");
TruststoreManager.validateTruststore(
    GlobalVariableManager.getKubeConfigYaml(),
    LocalVariableManager.getPromTruststorePath(),
    LocalVariableManager.getPromTruststorePassword(),
    LocalVariableManager.getPromAlias()
);

HttpHandler.dnsRouting(
    GlobalVariableManager.getPromDomainName(),
    GlobalVariableManager.getPromServerIP()
);

Logger.logWriter("Info", "Started receiving data from the Prometheus API.");
CloseableHttpClient httpClient = HttpHandler.customSSLContextHttpClient(
    LocalVariableManager.getPromTruststorePath(),
    LocalVariableManager.getPromTruststorePassword()
);

// Components' formatted data storage
Map<String, Map<String, List<String>>> componentMergedData = new HashMap<>();

Logger.logWriter("Info", "In total, got \'" + KubeWorker.getPodNamesList().size() +
    "\' Pods, with names: " + KubeWorker.getPodNamesList());

for (int i = 0; i < KubeWorker.getPodNamesList().size(); i++) {
    Logger.logWriter("Info", "Pod \'" + KubeWorker.getPodNamesList().get(i) +
        "\' (" + (i + 1) + " out of " + KubeWorker.getPodNamesList().size() + ")");

    List<String> resourceQueriesList = new ArrayList<>();
    List<String> resourceMetricsList = new ArrayList<>();

    try {
        resourceQueriesList = HttpHandler.buildResourceQueries(GlobalVariableManager.getNamespace(),
                                                               KubeWorker.getPodNamesList().get(i));
        resourceMetricsList = HttpHandler.buildMetricsList();

        Logger.logWriter("Info", "Resource queries for \'" + KubeWorker.getPodNamesList().get(i) + "\' pod:");
        for (String entry : resourceQueriesList) { Logger.logWriter("Info", entry); }
    } catch (UnsupportedEncodingException e) {
        Logger.logWriter("Error", "Error with URL encoding: " + e);
        e.printStackTrace();
    } catch (IndexOutOfBoundsException e) {
        Logger.logWriter("Error", "Error with accessing List element: " + e);
        e.printStackTrace();
    }

    for (int j = 0; j < resourceQueriesList.size(); j++) {
        Logger.logWriter("Info", "Query " + (j + 1) + " out of " + resourceQueriesList.size());
        String query = resourceQueriesList.get(j);

        try {
            httpGet = new HttpGet(HttpHandler.getPromApiURL(query));
            HttpResponse response = httpClient.execute(httpGet);

            DataTransformer.getMergedDataMap().put(
                resourceMetricsList.get(j),
                PromWorker.responseHandling(response, query, i)
            );
        } catch (UnsupportedEncodingException e) {
            Logger.logWriter("Error", "Error with URL encoding: " +
                "dateTimeStart: " + (GlobalTimeManager.getDateTimeStart() ?: "N/A") + "\n" +
                "dateTimeStartEncoded: " + (Utils.encode(GlobalTimeManager.getDateTimeStart()) ?: "N/A") + "\n" +
                "dateTimeEnd: " + (GlobalTimeManager.getDateTimeEnd() ?: "N/A") + "\n" +
                "dateTimeEndEncoded: " + (Utils.encode(GlobalTimeManager.getDateTimeEnd()) ?: "N/A") + "\n" +
                "step: " + (GlobalVariableManager.getPromStep() ?: "N/A") + "\n" +
                "stepEncoded: " + (Utils.encode(GlobalVariableManager.getPromStep()) ?: "N/A") + "\n" + e);
            e.printStackTrace();
            return;
        } catch (InvalidResponseStatusException | EmptyContentException | MissingValueException e) {
            Logger.logWriter("Error", String.valueOf(e));
            return;
        } catch (Exception e) {
            Logger.logWriter("Error", "Error with http request with URL: " +
                (HttpHandler.getPromApiURL(query) ?: "N/A") + "\n" + e);
            e.printStackTrace();
            return;
        }
    }

    DataTransformer.transformData(DataTransformer.getMergedDataMap());
    // Create CSV store file(s) and fill them with data
    DataStore.storeComponentDataToCSV(DataTransformer.getMergedDataMap(), i);
    // Put component's formatted data into the storage
    componentMergedData.put(DataStore.getComponentsList().get(i),
                            new LinkedHashMap<>(DataTransformer.getMergedDataMap()));
    // Clean up after use
    DataTransformer.getMergedDataMap().clear();
}

try {
    Logger.logWriter("Info", "Finished receiving data from the Prometheus API.");
    httpClient.close();
} catch (IOException e) {
    Logger.logWriter("Error", "Error with closing httpClient after sending to Prometheus API\n" + e);
    e.printStackTrace();
}

/**
 * Sends requests to Inflix DB API to send resource metrics data.
 * 
 * Iterates over all available components and their resources data and sends
 * an HTTP POST request to Influx for each one.
 * Handles Handles payload building and exceptions that may arise.
 *
 * @throws InvalidResponseStatusException if the Influx DB API returns an invalid status.
 */
Logger.logWriter("Info", "Started sending data to the Influx DB.");
for (Map.Entry<String, Map<String, List<String>>> componentEntry : componentMergedData.entrySet()) {
    String componentKey = componentEntry.getKey();
    Logger.logWriter("Info", "Sending data for component: " + componentKey + ".");
    Map<String, List<String>> componentData = componentEntry.getValue();

    for (Map.Entry<String, List<String>> entry : componentData.entrySet()) {
        String payload = InfluxWorker.payloadBuilder(entry, componentKey);

        try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(GlobalVariableManager.getInfluxdbUrl());
            httpPost.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "text/plain");
            HttpResponse response = httpClientInflux.execute(httpPost);
            InfluxWorker.responseHandling(response, payload);
        } catch (InvalidResponseStatusException e) {
            Logger.logWriter("Error", String.valueOf(e));
            continue;
        } catch (Exception e) {
            Logger.logWriter("Error", "Error with sending data to InfluxDB:\n" +
                "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
                "    Payload: " + (payload ?: "N/A") + "\n" + e);
            e.printStackTrace();
            return;
        }
    }
}

Logger.logWriter("Info", "Finished sending data to the Influx DB.");
//SampleResult.setIgnore();
