import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import groovy.json.JsonSlurper;
import groovy.xml.XmlSlurper;

import java.time.Instant;
import java.net.URLEncoder;

/**
 * @file Get reporting data - run metrics to CSV.groovy
 * @brief Sends queries to the database with metrics and stores results in the CSV format.
 * @detail Uses queries from the xml file to get metrics from the database and save results in the CSV file.
 */

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
    *        query file path.
    * @details This parameter represents the relative path to the file which contains
    *          queries to the database with metrics.
    */
    private static String queryListFile;
    /**
    * @brief Static field to hold parameter from JMeter's environment:
    *        Influx DB URL.
    * @details This parameter represents the URL address of Influx DB.
    */
    private static String influxdbUrl;
    /**
    * @brief Static field to hold parameter from JMeter's environment:
    *        data interval.
    * @details This parameter represents the timespan in seconds between data points.
    */
    private static String dataInterval;
    /**
    * @brief Static field to hold parameter from JMeter's environment:
    *        spread interval.
    * @details This parameter represents the timespan in seconds between which the spread will be calculated.
    */
    private static String spreadInterval;

    /**
     * @brief Initializer for parameters passed to the script from JMeter's environment (props).
     * @param cataloguePath The absolute path in the system where the repository is located
     *                      (e.g., C:/directory/repositories/ OR /opt/repositories/).
     * @param repDataPath The continuation of repository path to JMeter inside repository
     *                    (e.g., repository_name/jmeter/).
     * @param buildTag The name of the build which results we compare (usually, current build).
     * @param queryListFile The name of the file with queries to the database with metrics.
     * @param influxdbUrl The URL address of InfluxDB.
     * @param dataInterval Timespan in seconds between data points.
     * @param spreadInterval Timespan in seconds between which the spread will be calculated.
     */
    public static void initialize(
        String cataloguePath,
        String repDataPath,
        String buildTag,
        String queryListFile,
        String influxdbUrl,
        String dataInterval,
        String spreadInterval
    ) {
        GlobalVariableManager.cataloguePath = cataloguePath;
        GlobalVariableManager.repDataPath = repDataPath;
        GlobalVariableManager.buildTag = buildTag;
        GlobalVariableManager.queryListFile = queryListFile;
        GlobalVariableManager.influxdbUrl = influxdbUrl;
        GlobalVariableManager.dataInterval = dataInterval;
        GlobalVariableManager.spreadInterval = spreadInterval;
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

    /**
     * @brief Get the data Interval.
     * @return The data Interval in the String format for query.
     */
    public static String getDataInterval() {
        return dataInterval + "s";
    }

    /**
     * @brief Get the spread Interval.
     * @return The spread Interval in the String format for query.
     */
    public static String getSpreadInterval() {
        return spreadInterval + "s";
    }

}

/**
 * @class LocalVariableManager
 * @brief Class to manage local script variables.
 */
public class LocalVariableManager {

    /**
     * @brief Static field to hold script variable: name of the script.
     * @details This variable represents the name of this script and
     *          is used as indentifier in logging.
     */
    private static final String SCRIPT_NAME = "Get reporting data - run metrics to CSV";
    /**
     * @brief Static field to hold script variable: log file path.
     * @details This variable represents the output file full path where
     *          custom logs will be written.
     */
    private static final String LOG_FILE_PATH = GlobalVariableManager.getCataloguePath() +
                                                GlobalVariableManager.getRepDataPath() + "results/" +
                                                GlobalVariableManager.getBuildTag() + "/";
    /**
     * @brief Static field to hold script variable: timespan periods.
     * @details This variable represents the the List of Strings with timespan periods.
     */
    private static final List<String> PERIODS = Arrays.asList("RampUp", "MaxLoad", "WholeRun");

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
     * @brief Get the periods list.
     * @return The List of period timespan Strings.
     */
    public static List<String> getPeriods() {
        return PERIODS;
    }

}

/**
 * @class GlobalTimeManager
 * @brief Class to manage time variables for the script.
 */
public class GlobalTimeManager {

    /**
    * @brief Static field to hold parameter from JMeter's environment: test start timing.
    * @details This parameter represents the test Start timing as a Epoch timestamp
    *          in seconds converted to an ISO-8601 string.
    */
    private static String dateTimeStart;
    /**
    * @brief Static field to hold parameter from JMeter's environment: test max load timing.
    * @details This parameter represents the test Max Load timing as a Epoch timestamp
    *          in seconds converted to an ISO-8601 string.
    *          If the max load was not reached, it holds the same value as test End timing.
    */
    private static String dateTimeMaxload;
    /**
    * @brief Static field to hold parameter from JMeter's environment: test end timing.
    * @details This parameter represents the test End timing as a Epoch timestamp
    *          in seconds converted to an ISO-8601 string.
    */
    private static String dateTimeEnd;

    /**
     * @brief Initializer for timings passed to the script from JMeter's environment (props).
     * @param dateTimeStart The test Start timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     * @param dateTimeMaxload The test Max Load timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     * @param dateTimeEnd The test End timing as a Epoch timestamp in seconds converted to an ISO-8601 string.
     */
    public static void setTimings(
        Integer timeStart,
        Integer timeMaxload,
        Integer timeEnd
    ) {
        GlobalTimeManager.dateTimeStart = Instant.ofEpochSecond(timeStart).toString();
        GlobalTimeManager.dateTimeMaxload = Instant.ofEpochSecond(timeMaxload).toString();
        GlobalTimeManager.dateTimeEnd = Instant.ofEpochSecond(timeEnd).toString();
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
     *         If Max Load was not reached, the test End timing is returned.
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
 * @brief This class extends LocalVariableManager and is used as a custom logger.
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
 * @class FileWorker
 * @brief Factory class to create a data storage file.
 * @details Manages a file creation for diffetent data types.
 */
public class FileBuilder {

    /**
     * @brief Creates a file for aggregation data storage.
     * @param dFilePath Absolute path to the specific data type storage.
     * @param graphName Transaction group name for which data will be taken.
     * @param period A timespan name for which data was taken.
     * @return A data storage file for aggregation.
     */
    public static File createAggregationDataFile(String dFilePath, String graphName, String period) {
        File dfile = new File(dFilePath + "/" + period + "/" + graphName + ".csv");
        if (!dfile.exists()) {
            dfile.getParentFile().mkdirs();
            dfile.createNewFile();
        }
        return dfile;
    }

    /**
     * @brief Creates a file for metrics data storage.
     * @param dFilePath Absolute path to the specific data type storage.
     * @param graphName Transaction group name for which data will be taken.
     * @return A data storage file for metrics.
     */
    public static File createMetricDataFile(String dFilePath, String graphName) {
        File dfile = new File(dFilePath + "/" + graphName + ".csv");
        if (!dfile.exists()) {
            dfile.createNewFile();
        }
        return dfile;
    }

}

/**
 * @class ResponseWorker
 * @brief Сlass to parse and validate HTTP response, and write data to storage CSV file.
 * @details Validates and parses a HTTP response and, depending on tag type (which is dynamic entiry),
 *          writes result in specific format to the CSV file storage.
 */
public class ResponseWorker {

    /**
     * @brief Extracts all unique transaction names from a series list.
     * @details Collects transaction tag values across all series in the list
     *          to build the set of "known-active" transactions for a given period.
     * @param seriesList List of series Maps from InfluxDB response.
     * @return Set of unique transaction names found in seriesList tags.
     */
    private static Set<String> extractAllTransactionNames(List<Map> seriesList) {
        Set<String> transactionNames = new LinkedHashSet<>();
        seriesList.each { series ->
            Object tags = series.tags;
            if (tags instanceof Map) {
                transactionNames.addAll(((Map) tags).keySet());
            } else if (tags instanceof List) {
                ((List) tags).each { tag ->
                    transactionNames.add(tag instanceof List ? tag.join(";") : tag.toString());
                }
            }
        }
        return transactionNames;
    }

    /**
     * @brief Extracts transaction names from a specific series (by its metric name).
     * @param series A series Map from InfluxDB response.
     * @return Set of unique transaction names found in this series' tags.
     */
    private static Set<String> extractTransactionNamesFromSeries(Map series) {
        Set<String> names = new LinkedHashSet<>();
        Object tags = series.tags;
        if (tags instanceof Map) {
            names.addAll(((Map) tags).keySet());
        } else if (tags instanceof List) {
            ((List) tags).each { tag -> names.add(tag instanceof List ? tag.join(";") : tag.toString()); }
        }
        return names;
    }

    /**
     * @brief Parses response to JSON object and writes data from it to the CSV storage file.
     * @details Null checks the HTTP response and validates the query data to not be empty.
     *          Handles forming data in the specific formats and writing it into a CSV storage file.
     *          Logs response parsed to JSON object and errors if response is null or if data is empty.
     * @param responseBody Query result (response) from the HTTP request to the database.
     * @param dfile CSV storage file.
     */
    public static void writeResultToFile(
        String responseBody,
        File dfile
    ) {
        if (responseBody == null) {
            Logger.logWriter("Error", "No data received in the response for the query.");
            return;
        }

        Object result = new JsonSlurper().parseText(responseBody);

        if (isValidResult(result)) {
            Logger.logWriter("Info", "Result:\n" +
                "   columns: " + result.results.get(0).series.columns + "\n" +
                "   number of values: " + result.results.get(0).series.values.size());

            List<Map> seriesList = new ArrayList<>((List<Map>) result.results.get(0).series);
            appendSeriesToFile(seriesList, dfile, seriesList);
        } else {
            Logger.logWriter("Error", "Invalid or empty result data in the response for the graph.");
        }
    }

    /**
     * @brief Validates JSON against certain parameter requirements.
     * @param result HTTP response JSON object.
     * @return Boolean value: 'true' for valid or 'false' for invalid.
     */
    private static boolean isValidResult(
        Object result
    ) {
        return result != null &&
               result.results != null &&
               result.results.size() > 0 &&
               result.results.get(0).series != null &&
               result.results.get(0).series.size() > 0;
    }

    /**
     * @brief Appends each series data to the CSV storage file.
     * @param seriesList List of the 'series' Maps from the JSON object.
     * @param dfile CSV storage file.
     * @param allSeriesList Full series list for determining active transactions (used for backfill).
     *                      Pass null for non-aggregation or non-by-transaction queries.
     */
    private static void appendSeriesToFile(
        List<Map> seriesList,
        File dfile,
        List<Map> allSeriesList
    ) {
        Set<String> activeTransactions = null;
        if (allSeriesList != null && !allSeriesList.isEmpty()) {
            activeTransactions = extractAllTransactionNames(allSeriesList);
        }

        dfile.withWriterAppend("utf-8") { writer ->
            seriesList.each { series ->
                writeSeries(writer, series, activeTransactions);
            }
        }
    }

    /**
     * @brief Handles "tags" dynamic nature to choose a specific writing format.
     * @details As a "tags" object has a dynamic nature, depending on it's type navigates to the
     *          specific handling logic and logs an error on unexpected type.
     * @param writer BufferedWriter for CSV storage file in append mode.
     * @param series A Map from the JSON object.
     * @param activeTransactions Set of transaction names known to have activity this period (for backfill).
     */
    private static void writeSeries(
        Writer writer,
        Map series,
        Set<String> activeTransactions
    ) {
        Object tags = series.tags; // Dynamic entity
        List<String> columns = series.columns;
        List<List<Object>> values = series.values;
        Set<String> presentTransactions = extractTransactionNamesFromSeries(series);

        if (tags instanceof Map) {
            ((Map) tags).each { key, item -> writeTagData(writer, item, columns, values, "map"); }
            if (activeTransactions != null && !activeTransactions.isEmpty()) {
                backfillMissingTransactions(writer, activeTransactions, presentTransactions, columns);
            }
        } else if (tags instanceof List) {
            ((List) tags).each { tag -> writeTagData(writer, tag, columns, values, "list"); }
            if (activeTransactions != null && !activeTransactions.isEmpty()) {
                backfillMissingTransactions(writer, activeTransactions, presentTransactions, columns);
            }
        } else if (tags == null) {
            writeValues(writer, columns, values);
        } else {
            Logger.logWriter("Error", "Unhandled tag type: " + tags.getClass().getName());
        }
    }

    /**
     * @brief Backfills missing but active transactions with zero values.
     * @details For status-filtered "by transaction" queries (e.g., Total Errors), writes
     *          tag;transaction / time;metricName / 1970-01-01T00:00:00Z;0 triplets for
     *          transactions that had activity but are absent from the query result.
     * @param writer BufferedWriter for CSV storage file in append mode.
     * @param activeTransactions Set of all transactions with activity this period.
     * @param presentTransactions Set of transactions present in this series' result.
     * @param columns CSV header (metric name derived from columns[0] or similar).
     */
    private static void backfillMissingTransactions(
        Writer writer,
        Set<String> activeTransactions,
        Set<String> presentTransactions,
        List<String> columns
    ) {
        Set<String> missingTransactions = new LinkedHashSet<>(activeTransactions);
        missingTransactions.removeAll(presentTransactions);

        if (!missingTransactions.isEmpty()) {
            String metricName = columns.get(0);
            Logger.logWriter("Debug", "Backfilling " + missingTransactions.size() +
                " missing transactions for metric: " + metricName);

            missingTransactions.each { transaction ->
                writer.write("tag;" + transaction + System.lineSeparator());
                writer.write("time;" + metricName + System.lineSeparator());
                writer.write("1970-01-01T00:00:00Z;0" + System.lineSeparator());
            }
        }
    }

    /**
     * @brief Writes a additional custom header line to the CSV storage file.
     * @param writer BufferedWriter for CSV storage file in append mode.
     * @param tag Value which represents name of the transaction.
     * @param columns Values which represent CSV header (column names).
     * @param values Values which represent CSV data.
     * @param mode Type of the 'tag' object in JSON.
     */
    private static void writeTagData(
        Writer writer,
        Object tag,
        List<String> columns,
        List<List<Object>> values,
        String mode
    ) {
        writer.write(mode.equals("map")
                        ? "tag;" + tag + System.lineSeparator()
                        : "tag;" + tag.join(";") + System.lineSeparator()
                    );
        writeValues(writer, columns, values);
    }

    /**
     * @brief Writes a header and data lines to the CSV storage file.
     * @param writer BufferedWriter for CSV storage file in append mode.
     * @param columns A List of values which represent CSV header (column names).
     * @param dataRowsList A List of Lists with values which represent CSV data (data rows).
     */
    private static void writeValues(
        Writer writer,
        List<String> columns,
        List<List<Object>> dataRowsList
    ) {
        writer.write(String.join(";", columns) + System.lineSeparator());
        dataRowsList.each { dataRow ->
            writer.write(String.join(";", dataRow.stream()
                                                 .map(String::valueOf)
                                                 .collect(Collectors.toList())
                                    ) + System.lineSeparator());
        }
    }

}

/**
 * @class HttpHandler
 * @brief This class extends GlobalVariableManager and manages HTTP requests and
 *        query building for data retrieval from InfluxDB.
 * @details This class provides static methods for executing HTTP GET requests,
 *          building queries for data retrieval, and managing connections to the InfluxDB.
 */
public class HttpHandler extends GlobalVariableManager {

    /**
     * @brief Executes an HTTP GET request to retrieve data from InfluxDB.
     * @details Constructs the InfluxDB URL with the provided query, executes the HTTP GET request,
     *          and returns the response as a string. In case of an error, logs the issue and throws
     *          a RuntimeException.
     * @param query A raw query part of the URL for request.
     * @param period The time period for the query (e.g., "RampUp", "MaxLoad", "WholeRun").
     * @param mode The mode of the query (e.g., "data" for regular data, "spread" for spread calculation).
     * @return Response body from the HTTP request to the database as a String.
     * @throws RuntimeException If the request fails due to an error.
     */
    public static String executeHttpGetRequest(
        String query,
        String period,
        String mode
    ) {
        try {
            String influxdbUrl = GlobalVariableManager.getInfluxdbUrl() +
                                 URLEncoder.encode(buildQuery(query, period, mode), "UTF-8");
            return httpClientGetRequest(influxdbUrl);
        } catch (Exception e) {
            throw new RuntimeException("Error with sending data to InfluxDB:\n" +
                "URL: " + influxdbUrl + "\n" + e);
        }
    }

    /**
     * @brief Sends an HTTP GET request to a given URL and retrieves the response.
     * @details Creates a closable HTTP client, executes the GET HTTP request, and handles the response.
     *          Logs errors if the response is not successful or if an exception occurs during the request.
     * @param influxdbUrl The fully constructed URL for the InfluxDB query.
     * @return The response body as a string if the request is successful; null otherwise.
     * @throws RuntimeException If there is an error during the HTTP request.
     */
    public static String httpClientGetRequest(
        String influxdbUrl
    ) throws RuntimeException {
        try (CloseableHttpClient httpClientInflux = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(influxdbUrl);
            HttpResponse httpResponse = httpClientInflux.execute(httpGet);

            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                return httpResponse.getEntity().getContent().getText(); // responseBody
            } else {
                Logger.logWriter("Error", "Failed to get data from InfluxDB: \n" +
                    httpResponse.getEntity().getContent().getText());
                return null; // responseBody
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during HTTP request: " + e.getMessage(), e);
        }
    }

    /**
     * @brief Builds a query string for retrieving data from InfluxDB.
     * @details Constructs a query string by replacing placeholders in the base query template with
     *          actual values such as the start and end timings, and the data interval.
     *          Logs errors if the period is unknown.
     * @param query A raw query part of the URL for request.
     * @param period A timespan name for which data was taken.
     * @param mode The mode of the query (e.g., "data" or "spread").
     * @return A fully constructed query string with the appropriate parameters replaced
     *         (e.g., start timing, end timing, interval).
     */
    public static String buildQuery(
        String query,
        String period,
        String mode
    ) {
        String start;
        String end;
        String interval = mode.equals("data") ? getDataInterval() : getSpreadInterval();

        if (period.equals("RampUp")) {
            start = GlobalTimeManager.getDateTimeStart();
            end = GlobalTimeManager.getDateTimeMaxload();
        } else if (period.equals("MaxLoad")) {
            start = GlobalTimeManager.getDateTimeMaxload();
            end = GlobalTimeManager.getDateTimeEnd();
        } else if (period.equals("WholeRun") || period == null) {
            start = GlobalTimeManager.getDateTimeStart();
            end = GlobalTimeManager.getDateTimeEnd();
        } else {
            Logger.logWriter("Error", "Unknown timespan logic for period: " + period);
        }

        String modifiedQuery = query.replaceAll('[\\t\\n\\r]', '')
            .replaceAll("dateTimeStart", start)
            .replaceAll("dateTimeEnd", end)
            .replaceAll("interval", interval);
        Logger.logWriter("Info", "Query: " + modifiedQuery);
        return modifiedQuery;
    }

}

// Initialize global variables in GlobalVariableManager using parameters passed from JMeter.
GlobalVariableManager.initialize(
    args.size() > 0 ? args[0] : "", // cataloguePath
    args.size() > 1 ? args[1] : "", // repDataPath
    args.size() > 2 ? args[2] : "", // buildTag
    args.size() > 3 ? args[3] : "", // queryListFile
    args.size() > 4 ? args[4] : "", // influxdbUrl
    args.size() > 5 ? args[5] : "", // dataInterval
    args.size() > 6 ? args[6] : ""  // spreadInterval
);

// By design, all the parameters are mandatory.
final int EXPERCTED_ARGS_LEN = 7;
if (args.size() < EXPERCTED_ARGS_LEN) {
    // Stop script and create a log if missing a parameter (for example: no build to compare provided).
    Logger.logWriter("Error", "Skipping Whole Run metrics gathering: " +
        "one or more parameters were not passed from User Variables:\n" +
        args);

    return;
}

    // MAIN FLOW START
Logger.logWriter("Info", "Started Whole Run metrics gathering.");

Integer timeStart = (Integer) ((props.get("START.MS") as long) / 1000L);
Integer timeLoad = props.get("Test Max load") != null ? props.get("Test Max load") : props.get("Test End");
Integer timeEnd = props.get("Test End");

GlobalTimeManager.setTimings(
    timeStart, // dateTimeStart
    timeLoad,  // dateTimeMaxload
    timeEnd    // dateTimeEnd
);

File qfile = new File(GlobalVariableManager.getCataloguePath() +
                      GlobalVariableManager.getRepDataPath() +
                      GlobalVariableManager.getQueryListFile());

if (!qfile.exists()) {
    Logger.logWriter("Error", "Query file does not exist!");
    return;
}

Object xml = new XmlSlurper().parse(qfile);
String filePath = GlobalVariableManager.getCataloguePath() +
                  GlobalVariableManager.getRepDataPath() + "results/" +
                  GlobalVariableManager.getBuildTag() + "/";

xml.type.each { type ->
    String typeName = type.category;
    Logger.logWriter("Info", "Type name: " + typeName);
    String dFilePath = filePath + typeName;
    File dir = new File(dFilePath);
    dir.mkdirs();

    type.graph.each { graph ->
        String graphName = graph.name;
        Logger.logWriter("Info", "Graph name: " + graphName);
        if (typeName.toLowerCase().contains("aggregation")) {
            for (String period : LocalVariableManager.getPeriods()) {
                File dfile = FileBuilder.createAggregationDataFile(dFilePath, graphName, period);

                graph.group.each { group ->
                    String queryName = group.name;
                    Logger.logWriter("Info", "Query name: " + queryName);
                    dfile.withWriterAppend("utf-8") {
                        writer -> writer.write(queryName + System.lineSeparator());
                    }

                    String responseBody;
                    try {
                        String mode = graphName.toLowerCase().contains("spread") ? "spread" : "data";
                        responseBody = HttpHandler.executeHttpGetRequest(group.query.text(), period, mode);
                    } catch (Exception e) {
                        Logger.logWriter("Error", "Critical error encountered: " + e);
                        e.printStackTrace();
                        return;
                    }

                    ResponseWorker.writeResultToFile(responseBody, dfile);
                }
            }
        } else {
            File dfile = FileBuilder.createMetricDataFile(dFilePath, graphName);

            graph.group.each { group ->
                String queryName = group.name;
                Logger.logWriter("Info", "Query name: " + queryName);
                dfile.withWriterAppend("utf-8") {
                    writer -> writer.write(queryName + System.lineSeparator());
                }

                String responseBody;
                try {
                    String mode = graphName.toLowerCase().contains("spread") ? "spread" : "data";
                    responseBody = HttpHandler.executeHttpGetRequest(group.query.text(), null, mode);
                } catch (Exception e) {
                    Logger.logWriter("Error", "Critical error encountered: " + e);
                    e.printStackTrace();
                    return;
                }

                ResponseWorker.writeResultToFile(responseBody, dfile);
            }
        }
    }
}

Logger.logWriter("Info", "Finished Whole Run metrics gathering.");
//SampleResult.setIgnore();
