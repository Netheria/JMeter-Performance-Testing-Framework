# Continuous Integration Execution

The JMeter test suite includes automated validation and execution via GitHub Actions CI/CD pipeline.

The CI pipeline runs on every push and pull request to verify test plan integrity, validate configurations, build Docker images, and execute smoke tests against the full stack.

**CI Workflow:** [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)

## What the workflow validates

- **Test Plan Validation** – JMeter test plan syntax and sampler configuration checks
- **Configuration Validation** – CSV data files, YAML configs, properties files integrity
- **Docker Builds** – JMeter load generator image builds successfully
- **Smoke Test** – Full stack (InfluxDB, JMeter) starts correctly
- **Service Health** – InfluxDB availability, test data integrity
- **Documentation** – Markdown links validation

## GitHub Actions Workflow Structure

### Triggers

The CI pipeline is triggered on:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:  # Manual trigger via GitHub UI
```

### Jobs

The workflow runs the following jobs in parallel where possible:

1. **Validation** – Syntax and configuration checks
2. **Build Docker Images** – Image compilation without pushing to registry
3. **Smoke Test** – Integration test of full stack
4. **Script Code Documentation** - Generate Doxygen documentation
5. **Documentation** – Markdown link validation

## Running CI Manually

### Trigger via GitHub CLI

```bash
# Trigger workflow from command line
gh workflow run ci.yml

# Or with specific parameters
gh workflow run ci.yml -f build_tag=manual-test
```

### Trigger via GitHub Web UI

1. Go to repository → **Actions** tab
2. Select **CI** workflow
3. Click **Run workflow** → **Run workflow**

### Run CI Steps Locally

For local validation before pushing, run individual validation steps:

#### Validate JMeter Test Plans

```bash
# Check syntax of test plan files
./bin/jmeter.sh -t flows/performance/testapp.performance.conversationload.jmx -n -Jtesthosts=example.com

# This will attempt to run but may fail on external system connection (expected)

# Or verify JMeter is working:
./bin/jmeter.sh --version
```

#### Validate Configuration Files

```bash
# Validate YAML files (Kubernetes config)
python -m yaml resources/dev-01-test.yaml

# Or use yamllint if available
yamllint resources/dev-01-test.yaml

# Validate JSON files
jq empty flows/performance/query_list.xml  # Note: this is XML, not JSON
```

#### Validate CSV Data Files

```bash
# Check CSV format integrity
head -5 resources/testapp.agent_data.csv
head -5 resources/testapp.telegram_contact_data.csv

# Count rows to ensure data is present
wc -l resources/testapp.agent_data.csv
wc -l resources/testapp.telegram_contact_data.csv
```

#### Check for Debug Code

```bash
# Scan Groovy scripts for accidental debug statements
grep -rn "System.out.println\|println" flows/performance/conversationload_scripts/ \
  --exclude-dir=documentation || echo "No debug statements found"

# Check test plans for debug listeners left behind
grep -n "ResultCollector\|DebugSampler" flows/performance/*.jmx || echo "No debug samplers found"
```

#### Build Docker Images Locally

```bash
# Build JMeter image
docker build -f .docker/jmeter/Dockerfile -t jmeter-test:latest .

# Verify image works
docker run --rm jmeter-test:latest jmeter --version
```

#### Run Smoke Test Locally

**Important:** The test plan is designed for a proprietary system. Without valid credentials and network access (VPN), the smoke test will fail at connection attempts. This is expected behavior.

```bash
# Start services
docker compose -f docker-compose.ci.yml up -d --wait

# Run a quick smoke test (will fail on connection to external system)
docker compose -f docker-compose.ci.yml run --rm jmeter -n \
  -t "flows/performance/testapp.performance.conversationload.jmx" \
  -l "results/smoke-test.csv" \
  -J ConcurrentAgentNumber=1 \
  -J AgentRampUp=10 \
  -J ConcurrentClientNumber=1 \
  -J ClientRampUp=10 \
  -j jmeter-smoke.log

# Verify results CSV was created
ls -la results/smoke-test.csv

# Cleanup
docker compose -f docker-compose.ci.yml down -v
```

**Note:** To make the smoke test actually execute against the target system, you would need:
1. Valid credentials for the proprietary system
2. Network access/VPN connection to the test environment
3. Correct hostnames and endpoints in `bin/test.properties` or via `-J` flags

#### Verify Markdown Links

```bash
# Check for broken links in documentation
for file in $(find docs -name "*.md" 2>/dev/null); do
  echo "Checking $file"
  grep -o '\[.*\](.*\.md)' "$file" | while read -r link; do
    path=$(echo "$link" | sed 's/.*(\(.*\))/\1/')
    if [[ "$path" != http* ]] && [[ "$path" != "#"* ]]; then
      if [ ! -f "$path" ]; then
        echo "ERROR: Broken link in $file: $path"
      fi
    fi
  done
done
```

## CI Pipeline Details

### Job 1: Validation

**Purpose:** Syntax and configuration integrity checks

**Steps:**
1. Checkout code
2. Validate test plan files (`*.jmx`) can be parsed by JMeter
3. Validate Kubernetes config YAML (`resources/*.yaml`)
4. Validate CSV data files are properly formatted
5. Check Groovy scripts for syntax errors
6. Scan for accidental debug code left in scripts
7. Verify test.properties and other config files are present

**Failure Triggers:**
- Invalid JMeter test plan syntax
- Malformed YAML/CSV files
- Missing critical configuration files
- Debug code in committed scripts

### Job 2: Build Docker Images

**Purpose:** Verify Docker images compile successfully

**Steps:**
1. Checkout code
2. Set up Docker Buildx
3. Build JMeter image (`Dockerfile`)
4. Verify image metadata (labels, entry points)
5. Build any supporting service images
6. Scan for common Docker security issues (if enabled)

**Failure Triggers:**
- Dockerfile syntax errors
- Missing base image or dependencies
- Broken JMeter plugin installations
- Resource quota exceeded during build

### Job 3: Smoke Test

**Purpose:** Integration test ensuring services start and basic metrics collection works

**Steps:**
1. Checkout code
2. Start Docker Compose services (`docker-compose.ci.yml`)
3. Wait for all services to be healthy (InfluxDB ready)
4. Run minimal test plan (1 agent, 1 client, 10s duration)
5. Verify test completed (results CSV created)
6. Verify InfluxDB is responsive
7. Cleanup services and volumes

**Environment:**
- Uses `docker-compose.ci.yml` (minimal stack: JMeter + InfluxDB)
- Metrics: InfluxDB for result collection
- Timeout: 10 minutes

**Expected Outcome:**
- ✅ Services start successfully
- ✅ Results CSV file is created
- ⚠️ Test may fail on external system connection (expected without VPN/credentials)
- ✅ InfluxDB remains healthy and accepts metrics

**Failure Triggers:**
- Services fail to start
- InfluxDB not accepting connections
- Results files not created
- Docker container exits unexpectedly

### Job 4: Generate Doxygen docs

**Purpose:** Generate code documentation and publish it on github pages

**Steps:**
1. Checkout code
2. Install Doxygen
3. Generate documentation
4. Publish to GitHub Pages

### Job 5: Markdown Link Validation

**Purpose:** Catch broken documentation links (PR only)

**Steps:**
1. Checkout code
2. Find all markdown files in `docs/` and root
3. Extract all `[text](file.md)` links
4. Verify referenced files exist
5. Report any broken relative links

**Scope:**
- Only runs on pull requests
- Checks: docs/ directory and README.md
- Ignores: HTTP links, anchor-only links

**Failure Triggers:**
- Reference to non-existent `.md` files
- Incorrect relative paths

## Environment & Secrets

### CI Environment Variables

```yaml
# In .github/workflows/ci.yml

# Test Configuration
JMETER_HEAP: 1g
CONCURRENT_AGENTS: 1        # Low for smoke test
AGENT_RAMPUP: 10
CONCURRENT_CLIENTS: 1       # Low for smoke test
CLIENT_RAMPUP: 10

# InfluxDB (internal Docker network)
INFLUXDB_URL: http://influxdb:8086
INFLUXDB_DB: jmeter_metrics
INFLUXDB_USER: jmeter
INFLUXDB_PASSWORD: jmeter_password

# External system (example values - will fail without proper credentials)
DB_HOST: 192.0.2.10         # Test range IP (RFC 5737)
DB_PORT: 5432
DB_USER: testapp_user
DB_PASSWORD: demo_password
```

### Secrets (Not in Workflow YAML)

Store these as GitHub repository secrets:

- `DOCKER_REGISTRY_USERNAME` — If pushing to private registry
- `DOCKER_REGISTRY_PASSWORD` — If pushing to private registry
- `SLACK_WEBHOOK_URL` — For notifications (optional)

### Security Considerations

- CI runs in ephemeral containers; no secrets stored locally
- Database credentials are CI-specific (not production)
- Docker images built but not pushed (manual step required)
- No access to production metrics systems
- All volumes cleaned up after test completion

## Handling CI Failures

### Test Plan Validation Failure

**Error:** `ERROR: Test plan has syntax errors`

**Solution:**
1. Open JMeter GUI locally: `./bin/jmeter.sh`
2. File → Open Test Plan
3. Look for red X marks in tree
4. Fix configuration errors
5. Commit and push

### Docker Build Failure

**Error:** `failed to solve with frontend dockerfile.v0`

**Solutions:**
- Check Dockerfile for typos
- Verify base image `FROM jmeter:5.5` exists
- Ensure all dependencies are available
- Build locally to debug: `docker build .`

### Smoke Test Failure

**Expected Behavior:** Test may fail connecting to external proprietary system (expected without VPN/credentials)

**Error:** `connection timeout` or `authentication failed`

This is **expected** if:
- Running without VPN access to the test environment
- Credentials in `bin/test.properties` are example values (not real)
- Hostnames point to RFC 5737 test ranges (192.0.2.x) instead of real systems

**To make smoke test succeed:**
1. Provide valid credentials for the proprietary system
2. Ensure network access (VPN) to the test environment
3. Update connection strings in `bin/test.properties` or via `-J` flags
4. Example: `-J DB_HOST=actual-host.com -J DB_PASSWORD=real_password`

**Errors indicating actual infrastructure problems:**

If you see these errors, there's likely an issue with the Docker setup (not the external system):

```
# InfluxDB unreachable
ERROR: InfluxDB connection failed at http://influxdb:8086

# Services didn't start
ERROR: docker-compose.ci.yml failed to start

# Results not created
ERROR: /opt/jmeter/results/ is empty
```

**Troubleshooting actual infrastructure issues:**

```bash
# Check service health
docker compose -f docker-compose.ci.yml ps

# Check InfluxDB logs
docker compose -f docker-compose.ci.yml logs influxdb

# Test InfluxDB connectivity from JMeter container
docker compose -f docker-compose.ci.yml run --rm jmeter \
  bash -c 'curl -v http://influxdb:8086/ping'

# Check if results directory was created
docker compose -f docker-compose.ci.yml run --rm jmeter \
  ls -la /opt/jmeter/results/
```

### Broken Markdown Links

**Error:** `ERROR: Broken link: docs/nonexistent.md`

**Solution:**
- Check link in PR file
- Verify target file path is correct
- Use relative paths from the PR file's location
- Example: If in `docs/running/ci.md`, link to `../../PROJECT_SUMMARY.md`

## Customizing CI for Your Needs

### Add Custom Test Validation

```yaml
# In .github/workflows/ci.yml
- name: Custom Validation Step
  run: |
    echo "Running custom checks..."
    # Add your validation script here
```

### Run Additional Tests in Smoke Test

```yaml
# In smoke-test job
- name: Run Extended Smoke Test
  run: |
    docker compose -f docker-compose.ci.yml run --rm jmeter -n \
      -t "flows/functional/WebsocketPermissions.jmx" \
      -l "results/functional-smoke.csv"
```

### Push Docker Images to Registry

```yaml
# Add to docker build step
- name: Login to Docker Registry
  uses: docker/login-action@v2
  with:
    username: ${{ secrets.DOCKER_USERNAME }}
    password: ${{ secrets.DOCKER_PASSWORD }}

- name: Build and Push JMeter Image
  uses: docker/build-push-action@v5
  with:
    context: .
    file: .docker/jmeter/Dockerfile
    push: true
    tags: myregistry/jmeter:latest
```

### Add Notification on Failure

```yaml
# In any job
- name: Notify on Failure
  if: failure()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    text: 'CI Pipeline Failed'
    webhook_url: ${{ secrets.SLACK_WEBHOOK_URL }}
```

## Monitoring CI Status

### Check Workflow Status

```bash
# List recent workflow runs
gh run list --workflow=ci.yml --limit=10

# Watch a running workflow
gh run watch <RUN_ID>

# View workflow logs
gh run view <RUN_ID> --log
```

### Branches & Protection Rules

**Recommended branch protection:**

1. Go to **Settings** → **Branches** → **main**
2. Enable **Require status checks to pass before merging**
3. Select required checks:
   - `validate` — Configuration checks
   - `build-docker-images` — Docker compilation
   - `smoke-test` — Full stack integration test

This ensures all PRs pass validation before merge.

### Performance Metrics

To track CI performance:

```bash
# Get average workflow run time
gh run list --workflow=ci.yml --limit=20 | awk '{print $3}' | tail -10

# Check for slow jobs
gh run view <RUN_ID> --json=jobs --jq '.[] | .name, .duration'
```

---

**See Also:**
- [Local / Server Execution](./local-or-server-execution.md)
- [Containerized Deployment](./containerized-deployment.md)
- [Distributed Execution](./distributed-execution.md)
- `.github/workflows/ci.yml` — Full workflow definition
- `docker-compose.ci.yml` — CI services configuration
