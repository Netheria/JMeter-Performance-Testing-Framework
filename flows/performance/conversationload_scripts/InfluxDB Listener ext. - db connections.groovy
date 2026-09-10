import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.time.Instant;

/**
 * @file InfluxDB Listener ext. - db connections.groovy
 * @brief The Listener to write number of used database connections in Influx DB.
 * @detail Receives the number of database connections from test and sends in to Influx DB.
 */

/**
 * @class GlobalVariableManager
 * @brief Class to manage global variables for the script.
 */
public class GlobalVariableManager {

    /**
     * @brief Static field to hold parameter from JMeter's environment.
     * @details This parameter represents the URL address of Influx DB.
     */
    private static String influxdbUrl;
    /**
     * @brief Static field to hold parameter from JMeter's environment.
     * @details This parameter represents an absolute path in the system where the repository is located
     *          (e.g., C:/directory/repositories/).
     */
    private static String cataloguePath;
    /**
     * @brief Static field to hold parameter from JMeter's environment.
     * @details This parameter represents continuation of repository path to JMeter inside repository
     *          (e.g., repository_name/jmeter/).
     */
    private static String repDataPath;
    /**
     * @brief Static field to hold parameter from JMeter's environment.
     * @details This parameter represents the name of the build which results we compare.
     */
    private static String buildTag;
    /**
     * @brief Static field to hold parameter from JMeter's environment.
     * @details This parameter represents the number of used database connections
     *          at the current point of time.
     */
    private static String dbConnections;

    /**
     * @brief Initializes the global variables.
     * @param influxdbUrl The URL address of InfluxDB.
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/ OR /opt/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param dbConnections The number of used connections by database.
     */
    public static void initialize(
        String influxdbUrl,
        String cataloguePath,
        String repDataPath,
        String buildTag,
        String dbConnections
    ) {
        GlobalVariableManager.influxdbUrl = influxdbUrl;
        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.dbConnections = dbConnections;
    }

    /**
     * @brief Get the Influx DB URL.
     * @return The Influx DB URL with required constant parametrization to write data.
     */
    public static String getInfluxdbUrl() {
        return influxdbUrl + "/write?db=db0&precision=s";
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
     * @brief Get the used database connections.
     * @return The number of used database connections.
     */
    public static String getDBConnections() {
        return dbConnections;
    }

}

/**
 * @class LocalVariableManager
 * @brief Class to manage local script variables.
 */
public class LocalVariableManager {

    /**
     * @brief Static field to hold name of this script.
     * @details This variable represents the name of this script and
     *          is used as indentifier in logging.
     */
    private static final String SCRIPT_NAME = "InfluxDB Listener ext. - db connections";
    /**
     * @brief Static field to hold script variable.
     * @details This variable represents the output file path where
     *          custom logs will be written.
     */
    private static final String LOG_FILE_PATH = GlobalVariableManager.getCataloguePath() +
                                                GlobalVariableManager.getRepDataPath() + "results/" +
                                                GlobalVariableManager.getBuildTag() + "/";
    /**
     * @brief Static field to hold name of this script.
     * @details This variable represents the name of the category (i.e. measurement)
     *          in which values will be written to Influx DB.
     */
    private static final String MEASUREMENT = "db_connections";

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
     * @brief Get the Influx DB measurement.
     * @return The name of Influx DB measurement.
     */
    public static String getMeasurement() {
        return MEASUREMENT;
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
    public static void logWriter(String level, String text) { // JMeter's in-build logger doesn't work when out of scope
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
 * @brief This class extends GlobalVariableManager and contains utility methods for handling data.
 */
public class Utils extends GlobalVariableManager {

    /**
     * @brief Get the current time in seconds.
     * @return The current time in seconds.
     */
    public static int getCurrentTimeInSeconds() {
        return (int) (new Date().getTime() / 1000);
    }

    /**
     * @brief Builds a payload String with measurement, value of DB connections, and time in seconds
     *        to send to Influx DB.
     * @return A String with payload for Influx DB.
     */
    public static String payloadBuilder() {
        return LocalVariableManager.getMeasurement() + ",name=postgres connections=" +
            getDBConnections() + " " + getCurrentTimeInSeconds();
    }

}

// Initialize global variables in GlobalVariableManager using parameters passed from JMeter.
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "", // influxdbUrl
    args.size() > 1 ? args[1] : "", // cataloguePath
    args.size() > 2 ? args[2] : "", // repDataPath
    args.size() > 3 ? args[3] : "", // buildTag
    props.get("dbConnections")      // dbConnections
);

// Skip if parameter was not passed or no data available.
final int EXPERCTED_ARGS_LEN = 4;
if (args.size() < EXPERCTED_ARGS_LEN) {
    Logger.logWriter("Error", "Skipping database connections data sending: " +
        "one or more parameters were not passed from User Variables:\n" +
        args + "\n" +
        "database connections: " + props.get("dbConnections"));

    return;
}

/* Listeners in JMeter start working almost instantly, therefore,
   loggin will just spam messages when test haven't even started. */
if (GlobalVariableManager.getDBConnections() == null) {
    return;
}

// Send database connections to InfluxDB.
try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
    HttpPost httpPost = new HttpPost(GlobalVariableManager.getInfluxdbUrl());
    httpPost.setEntity(new StringEntity(Utils.payloadBuilder()));
    HttpResponse response = httpClientInflux.execute(httpPost);
    String responseStatus = response.getStatusLine().getStatusCode().toString();

    if (!responseStatus.equals("204") && 
        !responseStatus.equals("200")) {

        Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with payload or response:\n" +
            "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
            "    Status Code: " + (responseStatus ?: "N/A")  + "\n" +
            "    Payload: " + (Utils.payloadBuilder() ?: "N/A"));
        return;
    }
} catch (Exception e) {
    Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with sending data to InfluxDB:\n" +
        "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
        "    Payload: " + (Utils.payloadBuilder() ?: "N/A") + "\n" + e);
    e.printStackTrace();
    return;
}

//SampleResult.setIgnore();
