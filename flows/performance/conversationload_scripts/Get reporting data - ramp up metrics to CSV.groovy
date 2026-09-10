import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import groovy.json.JsonSlurper;
import groovy.xml.XmlSlurper;

import java.time.Instant;
import java.net.URLEncoder;

/**
 * @file Get reporting data - ramp up metrics to CSV.groovy
 * @brief Sends queries to the database with metrics and stores results in the CSV format.
 * @detail Uses queries from the xml file to get metrics from the database and save results in the CSV file.
 */

/**
 * @class GlobalVariableManager
 * @brief Class to manage global variables for the script.
 */
public class GlobalVariableManager {

    // JMeter parameters
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
    * @details This parameter represents the name of the file with queries to the database with metrics.
    */
    private static String queryListFile;
    /**
    * @brief Static field to hold parameter from JMeter's environment.
    * @details This parameter represents the URL address of Influx DB.
    */
    private static String influxdbUrl;

    // Script local variables
    /**
    * @brief Static field to hold name of this script.
    * @details This variable represents the name of this script and
    *          is used as indentifier in logging.
    */
    private static  String scriptName;
    /**
    * @brief Static field to hold script variable.
    * @details This variable represents the output file path where
    *          custom logs will be written.
    */
    private static String logFilePath;

    /**
     * @brief Initializer for parameters passed to script and script variables.
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param queryListFile The name of the file with queries to the database with metrics.
     * @param influxdbUrl The URL address of InfluxDB.
     */
    // Static method to initialize the fields
    public static void initialize(String cataloguePath,
                                  String repDataPath,
                                  String buildTag,
                                  String queryListFile,
                                  String influxdbUrl) {

        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.queryListFile = queryListFile;
        GlobalVariableManager.influxdbUrl = influxdbUrl;

        // Initialize local script variables
        scriptName = "Get reporting data - ramp up metrics to CSV";
        logFilePath = getCataloguePath() + getRepDataPath() + "results/" + getBuildTag() + "/";
    }

    // Getter methods for JMeter parameters
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
     * @brief Get the query file name.
     * @return The query file name.
     */
    public static String getQueryListFile() {
        return queryListFile;
    }

    /**
     * @brief Get the InfluxDB URL.
     * @return The InfluxDB URL with required constant parameters for writing data.
     */
    public static String getInfluxdbUrl() {
        return influxdbUrl + "/query?db=db0&q=";
    }

    // Getter methods for script variables
    /**
     * @brief Get the current script name.
     * @return The current script name.
     */
    public static String getScriptName() {
        return scriptName;
    }

    /**
     * @brief Get the logging file path.
     * @return The logging file path to create file.
     */
    public static String getLogFilePath() {
        return logFilePath;
    }

}

/**
 * @class Logger
 * @brief This class extends GlobalVariableManager and used as a custom logger.
 * @details A custom logger which solves the problem of standard logger not working out of scope.
 */
public class Logger extends GlobalVariableManager {

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

// Initialize global variables in GlobalVariableManager using parameters passed from JMeter.
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "", // cataloguePath
    args.size() > 1 ? args[1] : "", // repDataPath
    args.size() > 2 ? args[2] : "", // buildTag
    args.size() > 3 ? args[3] : "", // queryListFile
    args.size() > 4 ? args[4] : ""  // influxdbUrl
);

// By design, all the parameters are mandatory.
final int EXPERCTED_ARGS_LEN = 5;
if (args.size() < EXPERCTED_ARGS_LEN) {
    // Stop script and create a log if missing a parameter (for example: no build to compare provided).
    Logger.logWriter("Error", "Skipping Ramp Up metrics gathering: " +
        "one or more parameters were not passed from User Variables:\n" +
        args);

    return;
}

    // MAIN FLOW START
Logger.logWriter("Info", "Started Ramp Up metrics gathering.");

Integer time_start_s = (Integer) ((props.get("START.MS") as long) / 1000L);
String dateTimeStart = Instant.ofEpochSecond(time_start_s).toString();

String dateTimeMaxload = null;
if (props.get("Test Max load") != null) {
    int time_maxload_s = props.get("Test Max load");
    dateTimeMaxload = Instant.ofEpochSecond(time_maxload_s).toString();
} else {
    int time_end_s = props.get("Test End");
    dateTimeMaxload = Instant.ofEpochSecond(time_end_s).toString();
}

String interval = "5s"; // hardcoded because it does provide great visual data

File qfile = new File(GlobalVariableManager.getCataloguePath() +
                      GlobalVariableManager.getRepDataPath() +
                      GlobalVariableManager.getQueryListFile());

if (!qfile.exists()) {
    Logger.logWriter("Error", "Query file does not exist!");
    return;
}

Object xml = new XmlSlurper().parse(qfile);
String dfilePath = GlobalVariableManager.getCataloguePath() +
                   GlobalVariableManager.getRepDataPath() + "results/" + 
                   GlobalVariableManager.getBuildTag() + "/RampUp/";
File dfile = null;
String typeName = null;
String graphName = null;
String queryName = null;

// Get queries from the file
xml.type.each { type ->
    typeName = type.category;
    Logger.logWriter("Info", "Type name: " + typeName);
    File dir = new File(dfilePath + typeName);
    dir.mkdirs();

    // Iterating through graphs in metric type
    type.graph.each { graph ->
        graphName = graph.name;
        Logger.logWriter("Info", "Graph name: " + graphName);
        dfile = new File(dfilePath + typeName + '/' + graphName + '.csv');
        if (!dfile.exists()) {
            dfile.createNewFile();
        }

        // Iterating through groups' queries in graph
        graph.group.each { group ->
            queryName = group.name;
            Logger.logWriter("Info", "Query name: " + queryName);
            dfile.withWriterAppend('utf-8') {
                writer -> writer.write(queryName + System.lineSeparator());
            }
            // a bit of an overhead, but done for readability in xml
            String query = group.query.text().replaceAll('[\\t\\n\\r]', '')
                .replaceAll("dateTimeStart", dateTimeStart)
                .replaceAll("dateTimeEnd", dateTimeMaxload)
                .replaceAll("interval", interval);
            Logger.logWriter("Info", "Query: " + query);
            String influxdbUrl = GlobalVariableManager.getInfluxdbUrl() + URLEncoder.encode(query, "UTF-8");
            String responseBody = null;

            // Send request to InfluxDB
            try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
                HttpGet httpGet = new HttpGet(influxdbUrl);
                HttpResponse httpResponse = httpClientInflux.execute(httpGet);

                if (httpResponse.getStatusLine().getStatusCode() == 200) {
                    responseBody = httpResponse.getEntity().getContent().getText();
                } else {
                    responseBody = httpResponse.getEntity().getContent().getText();
                    Logger.logWriter("Error", "Failed to get data from InfluxDB: \n" + responseBody);
                    responseBody = null;
                }
            } catch (Exception e) {
                Logger.logWriter("Error", "Error with sending data to InfluxDB:\n" +
                    "URL: " + influxdbUrl + "\n" +
                    "Query: " + query + "\n" + e);
                e.printStackTrace();
                return;
            }

            // Write query result to file
            if (responseBody != null) {
                Object result = new groovy.json.JsonSlurper().parseText(responseBody);
                Logger.logWriter("Info", "Result: " + result);
                if (result.results.size() > 0 && result.results[0].series != null && result.results[0].series.size() > 0) {
                    List<Map> seriesList = result.results[0].series;
                    dfile.withWriterAppend('utf-8') { writer ->
                        seriesList.each { series ->
                            def tags = series.tags; // Dynamic entity
                            List<String> columns = series.columns;
                            List<List<Object>> values = series.values;
                            if (tags instanceof Map) {
                                tags.each { key, item ->
                                    writer.write("tag;" + item + System.lineSeparator());
                                    writer.write("" + columns.join(';') + System.lineSeparator());
                                    values.each { value ->
                                        writer.write("" + value.join(';') + System.lineSeparator());
                                    }
                                }
                            } else if (tags instanceof List) {
                                tags.each { tag ->
                                    writer.write("tag;" + tag.join(';') + System.lineSeparator());
                                    writer.write("" + columns.join(';') + System.lineSeparator());
                                    values.each { value ->
                                        writer.write("" + value.join(';') + System.lineSeparator());
                                    }
                                }
                            } else if (tags == null) {
                                writer.write("" + columns.join(';') + System.lineSeparator());
                                values.each { value ->
                                    writer.write("" + value.join(';') + System.lineSeparator());
                                }
                            } else {
                                Logger.logWriter("Error", "Error with data: can't handle instance \'" + tags.getClass().getName() + "\' tags");
                            }
                        }
                    }
                }
            } else {
                Logger.logWriter("Error", "No data found in the response for graph " + graphName + " and its query: " + queryName);
            }
        }
    }
}

Logger.logWriter("Info", "Finished Ramp Up metrics gathering.");
//SampleResult.setIgnore();