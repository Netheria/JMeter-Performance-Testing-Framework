# Observability

> Why does the framework collect more than HTTP response times?

The first JMeter framework was built around a simple belief: performance testing should explain system behavior, not only measure latency.

## Three Questions

A useful load test should answer three different questions:

| Question | Example signals |
|---|---|
| How fast is the system? | response time, throughput, error rate |
| Is the infrastructure healthy? | CPU, memory, restarts, database connections |
| Is the business workflow still working? | active agents, assignment behavior, conversation lifecycle, CSAT flow |

Looking at only one category can produce the wrong conclusion.

## Performance Metrics

JMeter captures the standard technical signals:

- HTTP response time;
- throughput;
- request rate;
- status codes;
- assertion failures;
- sampler-level errors;
- WebSocket connection activity;
- JDBC sampler timings where used.

These metrics show whether the system became slower or less stable.

They rarely explain the cause on their own.

## Infrastructure Metrics

The reporting pipeline collects infrastructure context from Kubernetes and Prometheus:

- pod names;
- images;
- CPU limits;
- memory limits;
- restart counts;
- CPU usage over time;
- memory usage over time.

This allows a test result to be interpreted against the resources that were available during the run.

For example, a latency increase with CPU saturation suggests a different investigation path than a latency increase while CPU and memory remain stable.

## Database Metrics

The `Get Database Connections` Thread Group polls PostgreSQL while the workload runs.

Database connection count is not enough to explain every database issue, but it is a valuable pressure signal.

It helps identify whether requests are slowing down while the application is waiting on database resources.

## Business Metrics

Business metrics check whether useful work is still happening.

Examples include:

- active Client and Agent threads;
- Agent utilization;
- conversation assignment progress;
- message exchange activity;
- resolved conversation flow;
- CSAT submission path.

These metrics are important because a system can stay technically "fast" while the workflow breaks.

Example:

```text
HTTP requests return 200
    -> response times look acceptable
    -> infrastructure looks healthy
    -> but conversations stop being assigned
```

Without business metrics, that failure can be missed.

## Metric Correlation

The framework is designed to compare signals collected during the same test window.

| Observation | Possible interpretation |
|---|---|
| Response time rises and CPU rises | Infrastructure saturation |
| Response time rises and CPU is stable | Application logic or downstream bottleneck |
| Response time rises and DB connections rise | Database connection pressure |
| Active users rise but throughput does not | Workflow blockage or functional regression |
| HTTP success remains high but business metrics fall | APIs respond, but business flow is broken |
| Infrastructure is healthy but business metrics are abnormal | Application behavior failure |
| Infrastructure and business metrics are healthy but latency rises | Performance regression inside application logic |

The point is not that one row proves one diagnosis. The point is that correlated data narrows the investigation.

## Proof in This Repository

The `results/test2` and `results/test3` folders are included as proof artifacts.

They show that the framework produced:

- `GeneralInfo.csv` execution metadata;
- `Resources/*.csv` CPU and memory datasets;
- `Aggregation/RampUp`, `Aggregation/MaxLoad`, and `Aggregation/WholeRun` datasets;
- `Metrics/*.csv` time-series metric groups;
- `Meta/*.csv` business/supporting metrics;
- raw JMeter sample output.

Those files demonstrate that observability was not only a design idea. It was part of the executed pipeline.

## Relationship to Version 2

The k6 version kept the same observability philosophy but changed the implementation.

In Version 1, JMeter listeners and teardown scripts collected and transformed the data.

In Version 2, k6 emits metrics while a separate reporting service performs post-processing.

The architecture changed, but the principle remained the same:

> performance results should be interpreted with workload, business, database, and infrastructure context together.
