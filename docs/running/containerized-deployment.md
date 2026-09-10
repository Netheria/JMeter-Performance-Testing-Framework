# Containerized Deployment

> How can the JMeter test plan be executed from a container or in Cloud deployment?

The JMeter test suite can be containerized and orchestrated using Docker Compose for development, testing, and CI/CD environments.

Instead of running locally, tests can be executed inside Docker containers with full service orchestration for database connectivity, metrics collection, and results data generation.

## Docker Compose – Local Full Stack

Run the entire JMeter test framework locally using Docker Compose.

All required services are automatically configured and started.

### Quick start

```bash
# Copy environment template (optional: to customize values)
cp .env.example .env

# Start all services
docker compose up -d --wait

# Verify services are healthy
docker compose ps

# Run JMeter performance test
docker compose run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/testapp.performance.conversationload.stdout.csv" \
  -p "./bin/test.properties"

# Run JMeter with environment variable override
docker compose run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/testapp.performance.conversationload.stdout.csv" \
  -J ConcurrentAgentNumber=50 \
  -J AgentRampUp=600 \
  -p "./bin/test.properties"

# View logs
docker compose logs -f jmeter

# Stop and clean up
docker compose down -v
```

### What starts

| Service | Port | Purpose |
|---------|------|---------|
| JMeter | N/A | Load generator (runs on-demand via `docker compose run`) |
| InfluxDB | 8087 | Time-series metrics storage for performance data |
| Chronograf | 8888 | Metrics visualization dashboard |

### Accessing Results

The JMeter container has a volume mount: `./results:/opt/jmeter/results` (container path : host path).

When JMeter tests complete, results are written to `/opt/jmeter/results/{BuildTag}/` inside the container, which automatically appears on your host at `./results/{BuildTag}/`.

Verify results exist:

```bash
# List all test runs
ls -la ./results/

# View results from a specific test run (e.g., BUILD_TAG=local-test)
ls -la ./results/local-test/
cat ./results/local-test/GeneralInfo.csv
cat ./results/local-test/Metrics/*.csv
```

## Container Images

The framework includes one custom Docker image and two external services:

**[JMeter Load Generator](.docker/jmeter/Dockerfile)** – Custom image based on Eclipse Temurin Java 17
- Apache JMeter 5.5 with all custom plugins pre-installed
- Custom performance monitoring plugins (InfluxDB listener)
- WebSocket and Selenium support
- Can run multiple test plans in sequence or parallel
- Mounts flows/, resources/, and results directories

**InfluxDB 1.8** – Official image from Docker Hub
- Time-series metrics storage for test execution data
- Pre-configured for JMeter metrics ingestion via HTTP listener
- Data persisted in named volume `influxdb_data`

**Chronograf** – Official image from Docker Hub
- Web UI for real-time metrics visualization and exploration
- Requires initial setup to connect to InfluxDB (one-time via web UI)
- Access via http://localhost:8888 during local development
- Connection details: `http://influxdb:8086` (InfluxDB hostname on jmeter_stack network)

## Docker Compose Files

The framework includes compose files for different use cases:

**`docker-compose.yml` – Full Development Stack**

- Includes all services: JMeter + InfluxDB + Chronograf
- Chronograf dashboard for real-time metrics visualization
- Best for: local testing, debugging, exploring metrics

```bash
docker compose up -d --wait
```

**`docker-compose.ci.yml` – CI Validation Stack**

- Includes only essential services: JMeter + InfluxDB (no Chronograf)
- **Purpose:** Used by GitHub Actions `.github/workflows/ci.yml` for automated validation
- Optimized for speed and minimal resource usage
- **Not** intended for local development

```bash
docker compose -f docker-compose.ci.yml up -d --wait
```

### Environment Configuration

Create `.env` file (or use `.env.example`):

```bash
# JMeter Configuration
JMETER_HEAP=2g
CONCURRENT_AGENTS=5
AGENT_RAMPUP=120
CONCURRENT_CLIENTS=3
CLIENT_RAMPUP=120

# Database Configuration (external system - for test connectivity)
DB_HOST=192.0.2.10
DB_PORT=5432
DB_USER=testapp_user
DB_PASSWORD=demo_password
DB_NAME=testapp_db

# InfluxDB Configuration
INFLUXDB_URL=http://influxdb:8086
INFLUXDB_DB=jmeter_metrics
INFLUXDB_USER=jmeter
INFLUXDB_PASSWORD=jmeter_password

# Test Identifiers
BUILD_TAG=local-test
COMPARE_BUILD_TAG=baseline
```

## Running Tests in Containers

### Standard Performance Test

```bash
# Run main performance test with default parameters
docker compose run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/testapp.performance.conversationload.stdout.csv" \
  -p "./bin/test.properties"
```

### Functional Tests

```bash
# Run functional tests
docker compose run --rm jmeter -n \
  -t "flows/functional/WebsocketPermissions.jmx" \
  -l "results/websocket-functional.csv" \
  -p "./bin/test.properties"
```

Check out my [functional testing in JMeter](https://github.com/Netheria/Functional-testing-Postman-vs-JMeter) case study article.

### Custom Parameter Override

```bash
# Override parameters via -J (JMeter properties)
docker compose run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/high-load-test.csv" \
  -J ConcurrentAgentNumber=100 \
  -J AgentRampUp=1200 \
  -J ConcurrentClientNumber=50 \
  -J ClientRampUp=1200 \
  -J InfluxdbUrl=http://influxdb:8086 \
  -p "./bin/test.properties"
```

### Running with Verbose Logging

```bash
# Enable debug logging for troubleshooting
docker compose run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/debug-test.csv" \
  -L DEBUG \
  -p "./bin/test.properties"
```

## Monitoring Tests in Real-Time

### Access Chronograf Dashboard

```bash
# Once containers are running:
# Open browser: http://localhost:8888

# First-time setup (one-time):
# 1. Click "Dashboards"
# 2. Create the dashboard
# 3. Enjoy
```

### View JMeter Logs Live

```bash
docker compose logs -f jmeter
```

### Check InfluxDB Metrics

```bash
# Connect to InfluxDB CLI
docker compose exec influxdb influx

# Inside InfluxDB:
> use jmeter_metrics
> show measurements
> select * from jmeter_sample limit 100
```

## Volume Mounts & Data Persistence

| Host Path | Container Path | Purpose |
|-----------|----------------|---------|
| `./flows` | `/opt/jmeter/flows` | Test plans (read-only) |
| `./resources` | `/opt/jmeter/resources` | Test data (CSV, YAML configs) |
| `./results` | `/opt/jmeter/results` | Test results & reports (read-write) |
| `./bin/test.properties` | `/opt/jmeter/bin/test.properties` | Configuration (read-only) |
| `./chronograf/data` | `/var/lib/chronograf` | Dashboards persistence |
| `./influxdb/data` | `/var/lib/influxdb` | Metrics persistence |

## Cloud & Kubernetes Deployment

For production deployments beyond Docker Compose:

### Kubernetes Deployment

```yaml
# Example: JMeter as Kubernetes Job
apiVersion: batch/v1
kind: Job
metadata:
  name: jmeter-performance-test
spec:
  template:
    spec:
      containers:
      - name: jmeter
        image: jmeter:5.5-custom
        args: ["bin/jmeter.sh", "-n", "-t", "flows/performance/testapp.performance.conversationload.jmx"]
        env:
        - name: DatabaseURL
          value: "jdbc:postgresql://postgres-service:5432/testapp_perf"
        - name: InfluxdbUrl
          value: "http://influxdb-service:8086"
      restartPolicy: Never
```

### Docker Swarm Multi-Node

```bash
# Deploy across multiple nodes using same compose file
docker stack deploy -c docker-compose.yml jmeter-stack
```

### Remote Execution

```bash
# SSH to remote machine and run JMeter in container
ssh user@remote-host "cd /path/to/jmeter && docker compose run --rm jmeter -n ..."
```

## Troubleshooting Container Issues

### Container fails to start

```bash
# Check logs for startup errors
docker compose logs jmeter

# Common issues:
# - Insufficient heap memory: increase JMETER_HEAP in .env
# - Database not ready: use --wait flag or add health checks
# - Port conflicts: change port mappings in docker-compose.yml
```

### Tests timeout or hang

```bash
# Increase timeout in docker compose
docker compose run --rm --timeout 600 jmeter -n ...

# Or add timeout to compose file:
# services:
#   jmeter:
#     timeout: 600
```

### Results not appearing on host

```bash
# Verify volume mount is correct
docker compose exec jmeter ls -la /opt/jmeter/results

# Check permissions on host results directory
ls -la ./results

# Ensure directory exists and is writable
mkdir -p ./results
chmod 777 ./results
```

### Database connection failures

```bash
# Test connectivity from JMeter container
docker compose exec jmeter \
  bash -c 'nc -zv postgres 5432'

# Verify database is running and ready
docker compose ps postgres

# Check PostgreSQL logs
docker compose logs postgres
```

## Performance Tuning for Containers

### Increase JMeter Memory

```env
# In .env file
JMETER_HEAP=4g
```

### Enable Non-Blocking I/O

```bash
# In test.properties or via -J flag
-J sun.nio.ch.bugLevel=11
```

### Increase File Descriptor Limits

```yaml
# In docker-compose.yml
services:
  jmeter:
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
```

### Network Configuration for High Concurrency

```yaml
# In docker-compose.yml
services:
  jmeter:
    networks:
      - test-network
    environment:
      - "net.core.somaxconn=4096"
      - "net.ipv4.tcp_max_syn_backlog=4096"
```

---

**See Also:**
- [Local / Server Execution](./local-or-server-execution.md)
- [Continuous Integration Execution](./continuous-integration-execution.md)
- [Distributed Execution](./distributed-execution.md)
