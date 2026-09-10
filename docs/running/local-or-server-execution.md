# Local or Server Execution

> How was the JMeter test plan executed on a workstation or dedicated load generator?

The original framework can be run from the JMeter command line. Public examples in this repository use placeholders because the real environment contained private hosts, credentials, Kubernetes access, and database configuration.

## Prerequisites

- Java 17
- Apache JMeter 5.5
- Required JMeter plugins and libraries from the historical runtime
- Access to the target application environment (which is proprietary)
- PostgreSQL access for setup and monitoring
- InfluxDB for JMeter metric output
- Optional Kubernetes and Prometheus access for resource reporting

The historical runtime additions are described in [../additions-changes.md](../additions-changes.md).

## Basic Command

Linux/macOS:

```bash
./bin/jmeter.sh -n -f \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/<BUILD_TAG>/testapp.performance.conversationload.stdout.csv" \
  -p "bin/test.properties"
```

Windows:

```bat
bin\jmeter.bat -n -f ^
  -t "flows\performance\testapp.performance.conversationload.jmx" ^
  -l "results\<BUILD_TAG>\testapp.performance.conversationload.stdout.csv" ^
  -p "bin\test.properties"
```

## Parameterized Command

The test plan can also be controlled through `-J` properties:

```bash
./bin/jmeter.sh -n -f \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/<BUILD_TAG>/testapp.performance.conversationload.stdout.csv" \
  -JHost=<application-host> \
  -JNodeRed=<webhook-host> \
  -JCataloguePath=<workspace-root> \
  -JRepDataPath=<relative-results-root> \
  -JApiAccessToken=<api-token> \
  -JaccountId=<account-id> \
  -JinboxId=<inbox-id> \
  -JteamId=<team-id> \
  -JTelegramID=<telegram-channel-id> \
  -JDatabaseURL=jdbc:postgresql://<database-host>:5432/<database-name> \
  -JDatabaseUser=<database-user> \
  -JDatabasePassword=<database-password> \
  -JConcurrentAgentNumber=20 \
  -JAgentRampUp=600 \
  -JConcurrentClientNumber=16 \
  -JClientRampUp=600 \
  -JInfluxdbUrl=http://<influxdb-host>:8086 \
  -JBuildTag=<BUILD_TAG> \
  -JCompareBuildTag=<COMPARE_BUILD_TAG>
```

## Optional Resource Reporting Parameters

Resource reporting requires Kubernetes and Prometheus parameters:

```bash
-JkubeConfigPath=<kube-config-directory> \
-JkubeConfigYaml=<kube-config-file> \
-JkubeDomainName=<kubernetes-api-domain> \
-JkubeServerIP=<kubernetes-api-ip> \
-JkubeApiAuthToken=<kubernetes-token> \
-Jnamespace=<namespace> \
-JpromDomainName=<prometheus-domain> \
-JpromServerIP=<prometheus-ip> \
-JpromStep=15
```

If these values are not configured, the core JMeter workload can still be used, but infrastructure resource datasets will not be complete.

## Output

A successful execution produces:

```text
results/<BUILD_TAG>/
    GeneralInfo.csv
    Resources/
    Aggregation/
    Metrics/
    Meta/
    testapp.performance.conversationload.stdout.csv
    custom.log
```

The sample `results/test2` and `results/test3` folders show this structure.

**Logs are not shown in samples**.

---

**See Also:**
- [Continuous Integration Execution](./continuous-integration-execution.md)
- [Containerized Deployment](containerized-deployment.md)
- [Distributed Execution](./distributed-execution.md)
