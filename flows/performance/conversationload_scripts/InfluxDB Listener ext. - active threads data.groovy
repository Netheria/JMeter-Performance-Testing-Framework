import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.time.Instant;

/**
 * @file InfluxDB Listener ext. - active threads data.groovy
 * @brief The Listener to write active threads metrics in InfluxDB.
 * @details Receives the values of currently running Agent and Client threads from the test and sends it to InfluxDB.
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
    * @details This parameter represents the name of the Client thread group in test.
    */
    private static String clientThreadGroupName;
    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the number of active Client threads in test.
    */
    private static Integer clientActiveThreads;
    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the name of the Agent thread group in test.
    */
    private static String agentThreadGroupName;
    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the number of active Agent threads in test.
    */
    private static Integer agentActiveThreads;

    // Static method to initialize the fields
    /**
     * @brief Initializes the global variables.
     * @param influxdbUrl The URL address of InfluxDB.
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/ OR /opt/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param clientThreadGroupName The name of the Client thread group in test.
     * @param clientActiveThreads The number of active Client threads in test.
     * @param agentThreadGroupName The name of the Agent thread group in test.
     * @param agentActiveThreads The number of active Agent threads in test.
     */
    public static void initialize(
        String influxdbUrl,
        String cataloguePath,
        String repDataPath,
        String buildTag,
        String clientThreadGroupName,
        Integer clientActiveThreads,
        String agentThreadGroupName,
        Integer agentActiveThreads
    ) {
        GlobalVariableManager.influxdbUrl = influxdbUrl;
        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.clientThreadGroupName = clientThreadGroupName;
        GlobalVariableManager.clientActiveThreads = clientActiveThreads;
        GlobalVariableManager.agentThreadGroupName = agentThreadGroupName;
        GlobalVariableManager.agentActiveThreads = agentActiveThreads;
    }

    /**
     * @brief Get the InfluxDB URL.
     * @return The InfluxDB URL with required constant parameters for writing data.
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
     * @brief Get the number of active Client threads.
     * @return The name of the Client thread group.
     */
    public static String getClientThreadGroupName() {
        return clientThreadGroupName;
    }

    /**
     * @brief Get the number of active Client threads.
     * @return The number of active Client threads.
     */
    public static Integer getClientActiveThreads() {
        return clientActiveThreads;
    }

    /**
     * @brief Get the number of active Agent threads.
     * @return The name of the Agent thread group.
     */
    public static String getAgentThreadGroupName() {
        return agentThreadGroupName;
    }

    /**
     * @brief Get the number of active Agent threads.
     * @return The number of active Agent threads.
     */
    public static Integer getAgentActiveThreads() {
        return agentActiveThreads;
    }

}

/**
 * @class LocalVariableManager
 * @brief Class to manage local script variables.
 */
public class LocalVariableManager {

    // Script local variables
    /**
    * @brief Static field to hold name of this script.
    * @details This variable represents the name of this script and
    *          is used as indentifier in logging.
    */
    private static String SCRIPT_NAME = "InfluxDB Listener ext. - active threads data";
    /**
    * @brief Static field to hold script variable.
    * @details This variable represents the output file path where
    *          custom logs will be written.
    */
    private static String LOG_FILE_PATH = GlobalVariableManager.getCataloguePath() +
                                          GlobalVariableManager.getRepDataPath() + "results/" +
                                          GlobalVariableManager.getBuildTag() + "/";
    /**
    * @brief Static field to hold name of this script.
    * @details This variable represents the name of the category (i.e. measurement)
    *          in which values will be written to InfluxDB.
    */
    private static String MEASUREMENT = "jmeter_threads";

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
     * @brief Get the InfluxDB measurement.
     * @return The name of InfluxDB measurement.
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
    * @brief Static field to hold thread groups' names and number of active threads in a map.
    */
    private static Map<String, Integer> threadData = new HashMap<>();

    /**
     * @brief Populates activity map with names (as keys) and activity (as values).
     * @details Populates activity map with thread group names and their active thread values.
     *          Also, puts a total thread group with sum of all threads.
     */
    private static void setThreadGroupActiveThreads() {
        // Client threads
        threadData.put(
            //Escape whitespaces in thread groups's names
            //Groovy (and Java) uses POSIX standard for regular expressions
            getClientThreadGroupName().replaceAll("\\s", "\\\\ "),
            getClientActiveThreads()
        );
        // Agent threads
        threadData.put(
            //Escape whitespaces in thread groups's names
            //Groovy (and Java) uses POSIX standard for regular expressions
            getAgentThreadGroupName().replaceAll("\\s", "\\\\ "),
            getAgentActiveThreads()
        );
        // Total threads sum
        threadData.put(
            "TotalThreads",
            Integer.sum(getClientActiveThreads(), getAgentActiveThreads())
        );
    }

    /**
     * @brief Getter for agent activity map.
     * @details Calls a chain of private methods to get, calculate, and populate all related data before return.
     * @return A map containing active agents' activity and total activity percentage.
     */
    public static Map<String, Integer> getThreadGroupActiveThreads() {
        setThreadGroupActiveThreads();
        return threadData;
    }

    /**
     * @brief Get the current time in seconds.
     * @return The current time in seconds.
     */
    public static int getCurrentTimeInSeconds() {
        return (int) (new Date().getTime() / 1000);
    }

    /**
     * @brief Build a payload string with measurement, thread group name, number of active threads, and time.
     * @param groupName The name of the thread group.
     * @param activeThreads The number of active threads in a thread group.
     * @return A formatted payload string for InfluxDB.
     */
    public static String payloadBuilder(String groupName, Integer activeThreads) {
        return LocalVariableManager.getMeasurement() +
            ",groupName=" + groupName +
            " activeThreads=" + activeThreads + " " +
            getCurrentTimeInSeconds();
    }

}

/* Initialize global variables in GlobalVariableManager using parameters passed from JMeter.
   NOTE: at the start of the test JMeter does initialize thread groups and their scenarious,
         therefore, active threads' values will peak then drop to minimal values. */
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "",                       // influxdbUrl
    args.size() > 1 ? args[1] : "",                       // cataloguePath
    args.size() > 2 ? args[2] : "",                       // repDataPath
    args.size() > 3 ? args[3] : "",                       // buildTag
    (String) props.get("ClientThreadGroupName") ?: "N/A", // ClientThreadGroupName
    (Integer) props.get("ClientActiveThreads") ?: 0,      // ClientActiveThreads
    (String) props.get("AgentThreadGroupName") ?: "N/A",  // AgentThreadGroupName
    (Integer) props.get("AgentActiveThreads") ?: 0,       // AgentActiveThreads
);

/* Skip if parameter was not passed or both group names are not present.
   Only one group may be present is case of specific testing or debugging. */
final int EXPERCTED_ARGS_LEN = 4;
if (args.size() < EXPERCTED_ARGS_LEN) {
    if (((String) props.get("ClientThreadGroupName") ?: "N/A").equals("N/A") ||
       ((String) props.get("AgentThreadGroupName") ?: "N/A").equals("N/A")) {

        Logger.logWriter("Error", "Skipping active threads data sending: " +
            "one or more parameters were not passed from User Variables:\n" +
            args + "\n" +
            "ClientThreadGroupName: " +
            (props.get("ClientThreadGroupName") != null ? (String) props.get("ClientThreadGroupName") : "N/A") + "\n" +
            "AgentThreadGroupName: " +
            (props.get("AgentThreadGroupName") != null ? (String) props.get("AgentThreadGroupName") : "N/A"));
    }

    return;
}

/* Receive each thread group name and active threads.
   Calculate total active threads.
   Put everything in a map.
   Then, send data of every metric in the map to InfluxDB. */
for (threadData : Utils.getThreadGroupActiveThreads()) {
    try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
        HttpPost httpPost = new HttpPost(GlobalVariableManager.getInfluxdbUrl());
        httpPost.setEntity(new StringEntity(Utils.payloadBuilder(threadData.getKey(),  threadData.getValue())));
        HttpResponse response = httpClientInflux.execute(httpPost);
        String responseStatus = response.getStatusLine().getStatusCode().toString();

        if (!responseStatus.equals("204") && 
            !responseStatus.equals("200")) {

            Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with payload or response:\n" +
                "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
                "    Status Code: " + (responseStatus ?: "N/A")  + "\n" +
                "    Payload: " + (Utils.payloadBuilder(threadData.getKey(), threadData.getValue()) ?: "N/A"));
            return;
        }
    } catch (Exception e) {
        Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with sending data to InfluxDB:\n" +
            "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
            "    Payload: " + (Utils.payloadBuilder(threadData.getKey(), threadData.getValue()) ?: "N/A") + "\n" + e);
        e.printStackTrace();
        return;
    }
}

//SampleResult.setIgnore();
