import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.time.Instant;

/**
 * @file InfluxDB Listener ext. - agent utilization.groovy
 * @brief The Listener to write agent conversation activity (i.e. utilization) metrics in InfluxDB.
 * @details Receives the activity boolean value of each agent from the test and sends it to InfluxDB.
 */

/**
 * @class GlobalVariableManager
 * @brief Class to manage global variables for the script.
 */
public class GlobalVariableManager {

    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the URL address of InfluxDB.
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
    * @details This parameter represents the number of working Agent threads in test.
    */
    private static Integer agentActiveThreads;
    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the List with activity marker for all currently working Agent threads in test.
    */
    private static List<Integer> agentActivityList;

    /**
     * @brief Initializes the global variables.
     * @param influxdbUrl The URL address of InfluxDB.
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/ OR /opt/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param agentActiveThreads The number of working Agent threads in test.
     * @param agentActivityList The List with activity marker for all currently working Agent threads in test.
     */
    public static void initialize(
        String influxdbUrl,
        String cataloguePath,
        String repDataPath,
        String buildTag,
        Integer agentActiveThreads,
        List<Integer> agentActivityList
    ) {
        GlobalVariableManager.influxdbUrl = influxdbUrl;
        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.agentActiveThreads = agentActiveThreads;
        GlobalVariableManager.agentActivityList = new ArrayList<>(agentActivityList);
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
     * @brief Get the number of working Agent threads.
     * @return The number of working Agent threads.
     */
    public static Integer getAgentActiveThreads() {
        return agentActiveThreads;
    }

    /**
     * @brief Get the List with activity markers of currently working Agent threads.
     * @return The List with activity markers of currently working Agent threads.
     */
    public static List<Integer> getAgentActivityList() {
        return agentActivityList;
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
    private static final String SCRIPT_NAME = "InfluxDB Listener ext. - agent utilization";
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
    *          in which values will be written to InfluxDB.
    */
    private static final String MEASUREMENT = "agent_activity";

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
    * @brief Static field to hold total activity percentage.
    */
    private static int activityPercentage;
    /**
    * @brief Static field to hold agents' activity data and total activity percentage in a map.
    */
    private static Map<String, Integer> agentActivityMap = new HashMap<>();

    /**
     * @brief Calculate the total number of activite Agent threads.
     * @return The count of active Agent threads.
     */
    private static int calcActivity() {
        int countActivity = 0;
        for (int activity : agentActivityMap.values()) {
            if (activity == 1) {
                countActivity++;
            }
        }
        return countActivity;
    }

    /**
     * @brief Calculate and store the total activity percentage in the map.
     */
    private static void calcActivityPct() {
        int countActivity = calcActivity();
        if (countActivity >= 1) {
            activityPercentage = (int) Math.floor(((double) countActivity / getAgentActiveThreads()) * 100);
            agentActivityMap.put("ActivityPercentage", activityPercentage);
        }
    }

    /**
     * @brief Gather activity data for each Agent thread and store in the map.
     */
    public static void setActivityData() {
        for (int i = 0; i < getAgentActiveThreads(); i++) {
            Integer activity = getAgentActivityList().get(i);
            if (activity != null) {
                agentActivityMap.put("Agent" + i, activity);
            } else {
                agentActivityMap.put("Agent" + i, 0);
            }
        }
        calcActivityPct();
    }

    /**
     * @brief Get the current time in seconds.
     * @return The current time in seconds.
     */
    public static int getCurrentTimeInSeconds() {
        return (int) (new Date().getTime() / 1000);
    }

    /**
     * @brief Build a payload string with measurement, Agent thread number, activity value, and time.
     * @param agentNum The agent number.
     * @param activity The activity value (0 or 1).
     * @return A formatted payload string for InfluxDB.
     */
    public static String payloadBuilder(String agentNum, Integer activity) {
        return LocalVariableManager.getMeasurement() +
            ",agentNum=" + agentNum +
            " isActive=" + activity + " " +
            getCurrentTimeInSeconds();
    }

    /**
     * @brief Getter for agent activity map.
     * @details Calls a chain of private methods to get, calculate, populate all related data before return.
     * @return A map containing active agents' activity and total activity percentage.
     */
    public static Map<String, Integer> getAgentActivityMap() {
        setActivityData();
        return agentActivityMap;
    }

}

activeThreads = (Integer) props.get("AgentActiveThreads") ?: 0;
if (activeThreads == 0) { // Skip if no active Agent threads data are available.
    return;
}

/* "props" only works inside the main script body scope.
   Therefore, workaround with adding everything in a List */
List<Integer> activityList = new ArrayList<>();
for (int i = 1; i <= activeThreads; i++) {
    Integer activity = (Integer) props.get("activityAgent" + i);
    if (activity != null) {
        activityList.add(activity);
    } else {
        activityList.add(0);
    }
}

// Initialize global variables in GlobalVariableManager using parameters passed from JMeter.
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "", // influxdbUrl
    args.size() > 1 ? args[1] : "", // cataloguePath
    args.size() > 2 ? args[2] : "", // repDataPath
    args.size() > 3 ? args[3] : "", // buildTag
    activeThreads,                  // AgentActiveThreads
    activityList                    // List with agents' activity markers
);

// Skip if parameter wasn not passed.
final int EXPERCTED_ARGS_LEN = 4;
if (args.size() < EXPERCTED_ARGS_LEN) {
    Logger.logWriter("Error", "Skipping agent utilization data sending: " +
        "one or more parameters were not passed from User Variables:\n" +
        args);

    return;
}

/* Receive activity data from each Agent thread and calculate total activity percentage.
   Put all data in a map.
   Then, send activity metrics for each thread to InfluxDB. */
for (Map.Entry<String, Integer> entry : Utils.getAgentActivityMap()) {
    try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
        HttpPost httpPost = new HttpPost(GlobalVariableManager.getInfluxdbUrl());
        httpPost.setEntity(new StringEntity(Utils.payloadBuilder(entry.getKey(),  entry.getValue())));
        HttpResponse response = httpClientInflux.execute(httpPost);
        String responseStatus = response.getStatusLine().getStatusCode().toString();

        if (!responseStatus.equals("204") && 
            !responseStatus.equals("200")) {

            Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with payload or response:\n" +
                "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
                "    Status Code: " + (responseStatus ?: "N/A")  + "\n" +
                "    Payload: " + (Utils.payloadBuilder(entry.getKey(),  entry.getValue()) ?: "N/A"));
            return;
        }
    } catch (Exception e) {
        Logger.logWriter("Error", LocalVariableManager.getScriptName() + ": Error with sending data to InfluxDB:\n" +
            "    URL: " + (GlobalVariableManager.getInfluxdbUrl() ?: "N/A") + "\n" +
            "    Payload: " + (Utils.payloadBuilder() ?: "N/A") + "\n" + e);
        e.printStackTrace();
        return;
    }
}

//SampleResult.setIgnore();
