# JMeter 5.5 Customization

This document details all additions, modifications, and enhancements made to the base Apache JMeter 5.5 distribution to support performance testing for the TestApp product.

The customizations focus on distributed testing, observability, browser automation, and comprehensive metrics collection.

---

## Table of Contents

1. [Configuration Changes](#configuration-changes)
2. [Added Plugins](#added-plugins)
3. [Added/Upgraded Libraries](#addedupdated-libraries)
4. [Customization Summary](#customization-summary)

---

## Configuration Changes

### New Configuration Files

#### `test.properties`
**Location:** `bin/test.properties`

This new configuration file contains test environment-specific settings for the JMeter test suite. It includes:
- Database connection parameters (URLs, usernames, credentials)
- Infrastructure endpoints (Kubernetes API, Prometheus, InfluxDB)
- Performance test parameters (concurrent agent/client counts, ramp-up durations)
- Build tags for comparison reporting

Used for convenience to not provide test parameters all the time.

### Modified Configuration Files

#### `user.properties`
**Location:** `bin/user.properties`  

Extensions added to the default user properties include:
- **InfluxDB Backend Listener Configuration:** Settings to enable real-time metrics collection to InfluxDB during test execution, including connection URLs and metric prefixes
- **Thread Group Settings:** Increased default thread pool sizes for better concurrent load simulation
- **Plugin Manager Integration:** Auto-configuration of the JMeter plugins manager

These additions enable the test suite to automatically collect and export performance metrics to monitoring systems without manual listener setup in the JMX file.

### Unchanged Configuration Files

The following core configuration files remain identical to the original Apache JMeter 5.5:
- `jmeter.properties` — Main JMeter configuration
- `saveservice.properties` — Service serialization settings
- `system.properties` — JVM system properties
- `reportgenerator.properties` — Built-in report generator config
- `upgrade.properties` — Upgrade migration settings

---

## Added Binary Support Files in `bin/`

### SSL Certificate Files

#### `ApacheJMeterTemporaryRootCA.crt`
**Type:** X.509 Certificate  
**Purpose:** Root Certificate Authority for HTTPS testing

This certificate file is used for SSL/TLS testing scenarios where JMeter needs to validate HTTPS connections or when running tests against services with self-signed certificates. It establishes a trust anchor for certificate chain validation during test execution.

#### `ApacheJMeterTemporaryRootCA.usr`
**Type:** Certificate User File  
**Purpose:** Root CA User Certificate

This file contains the user-specific certificate associated with the root CA, used alongside the CRT file for mutual TLS authentication or advanced SSL/TLS testing scenarios where client certificates are required.

### WebDriver Support

#### `chromedriver.exe`
**Version:** Latest compatible with Selenium 4.8.1  
**Purpose:** Google Chrome WebDriver Binary

This executable enables the Selenium WebDriver plugin to control and automate Google Chrome browser instances during test execution. It's essential for UI/functional testing scenarios and browser-based performance testing. The driver communicates with the Chrome browser via the Chrome DevTools Protocol (CDP) to drive user interactions, capture page loads, and measure real browser performance metrics.

### Distributed Testing Support

#### `proxyserver.jks`
**Type:** Java KeyStore  
**Purpose:** Proxy Server Certificate Store

This keystore contains certificates used by JMeter's proxy server when recording or simulating HTTPS traffic. It allows JMeter to intercept and modify HTTPS requests during test recording and playback, essential for testing secure applications without SSL bypass.

#### `rmi_keystore.jks`
**Type:** Java KeyStore  
**Purpose:** RMI Distributed Testing Security

This keystore stores keys and certificates for Remote Method Invocation (RMI) communication between JMeter master and slave nodes in distributed testing scenarios. It provides secure authentication and encryption for distributing load across multiple machines.

### Plugin Management

#### `JMeterPluginsCMD.bat` / `JMeterPluginsCMD.sh`
**Purpose:** JMeter Plugins Manager Command-Line Interface

These shell scripts provide command-line access to the JMeter Plugins Manager, enabling:
- Installation and uninstallation of plugins via CLI
- Batch plugin management in CI/CD pipelines
- Automated plugin version control and updates
- Integration with deployment automation tools

#### `PluginsManagerCMD.bat` / `PluginsManagerCMD.sh`
**Purpose:** Additional Plugin Management Commands

Supplementary plugin manager scripts providing extended functionality for managing the plugin ecosystem, complementing the primary JMeterPluginsCMD scripts.

---

## Added Plugins

JMeter plugins extend the core functionality with additional samplers, listeners, functions, and UI components. The customized installation includes 28 additional plugins organized by functional category.

### Metrics & Observability Plugins

#### `jmeter-plugins-influxdb2-listener-2.6.jar`
**Category:** Metrics Collection  
**Purpose:** InfluxDB 2.x Integration Listener

This plugin provides real-time metrics export to InfluxDB 2.x, a time-series database optimized for performance metrics and monitoring data. During test execution, it captures:
- Response times (min, max, average, percentiles)
- Throughput metrics (requests per second)
- Error rates and types
- Custom metrics from test samplers
- Thread state metrics (active, started, finished)

The metrics are pushed to InfluxDB in real-time, enabling live monitoring dashboards (via Grafana) and post-test analysis without waiting for test completion.

### WebSocket & Real-time Communication Plugins

#### `jmeter-websocket-samplers-1.2.10.jar` / `1.2.8.jar`
**Category:** Protocol Support  
**Purpose:** WebSocket Protocol Testing

Standard.

Multiple versions of the WebSocket sampler plugin provide:
- Full WebSocket protocol support (RFC 6455)
- Bi-directional communication testing
- WebSocket frame-level control
- Message payload customization
- Connection lifecycle management (open, send, receive, close)
- Performance testing of real-time applications (chat, notifications, streaming)

Dual versions allow testing against different WebSocket server implementations and backward compatibility testing.

#### `Java-WebSocket-1.6.0.jar`
**Category:** Library Dependency  
**Purpose:** WebSocket Client Library

Personal recommendation.

This is the Java WebSocket client library used by the WebSocket samplers. Version 1.6.0 provides:
- Native Java WebSocket client implementation
- TLS/SSL support for secure WebSocket connections (WSS)
- Custom header support
- Connection pooling and reuse
- Low-level protocol handling and frame management

### Graph & Visualization Plugins

Used only for pre-visualization runs.

#### `jmeter-plugins-graphs-basic-2.0.jar`
**Category:** Results Visualization  
**Purpose:** Basic Real-time Graph Plotting

Provides foundational real-time charting during test execution:
- Response time distribution graphs
- Throughput over time (requests/second)
- Error rate trends
- Latency percentiles (p50, p90, p99)
- Live chart updates as test progresses

These basic graphs are essential for quick visual feedback during active testing.

#### `jmeter-plugins-graphs-additional-2.0.jar`
**Category:** Results Visualization  
**Purpose:** Extended Graph Types

Additional graph types beyond basics:
- Response time vs. threads (load curve)
- Latency vs. throughput scatter plots
- 3D response time surface plots
- Custom metric plotting
- Advanced statistical visualizations

#### `jmeter-plugins-graphs-dist-2.0.jar`
**Category:** Results Visualization  
**Purpose:** Distribution Analysis Graphs

Specialized graphs for analyzing data distributions:
- Histogram of response times (distribution shape analysis)
- Percentile curve charts
- Cumulative distribution functions
- Outlier detection visualization

#### `jmeter-plugins-graphs-vs-2.0.jar`
**Category:** Results Visualization  
**Purpose:** Comparison Graphs

Side-by-side comparison graphing:
- Two-run comparison charts
- Regression detection visualization
- Build-to-build performance trends
- Baseline vs. current run overlays

### Custom Thread Group Plugins

#### `jmeter-plugins-casutg-3.1.1.jar`
**Category:** Thread Group Extension  
**Purpose:** Custom Arrivals Thread Group

Was used in Stress testing type scenarions (Test Plans).

Flexible and perfect match for stateful workloads.

This plugin provides advanced thread scheduling patterns beyond standard JMeter thread groups:
- Constant arrival rate (fixed throughput target)
- Poisson distribution arrivals (realistic user patterns)
- Stepping load patterns
- Coordinated omni-bus scheduling

CASUTG (Custom Arrivals Shaped Unit Test Group) is essential for:
- Load testing with realistic user arrival patterns
- Constant throughput testing (independent of response time)
- Complex multi-stage load scenarios
- Modeling real user behavior distributions

### Utility & Function Plugins

#### `jmeter-plugins-functions-2.2.jar`
**Category:** Custom Functions  
**Purpose:** Extended Function Library

Adds custom JMeter functions beyond the built-in set:
- Advanced string manipulation functions
- Mathematical operations
- Date/time processing
- Random value generation with distributions
- Hashing and encoding functions
- System interaction functions

#### `jmeter-plugins-tst-2.6.jar`
**Category:** Test Management  
**Purpose:** Test Status & Timing Tools

Tools for managing test execution status:
- Real-time test status reporting
- Execution time tracking
- Pass/fail rate calculation
- Test result aggregation
- Timeline visualization

#### `jmeter-plugins-cmd-2.2.jar`
**Category:** Command Execution  
**Purpose:** Command-Line Sampler & Tools

Enables running system commands during tests:
- Execute shell/batch commands
- Capture command output
- Parse command results into JMeter variables
- Integrate external tools and systems
- Health check validation via CLI

#### `jmeter-plugins-ffw-2.0.jar`
**Category:** Results Processing  
**Purpose:** Flexible File Writer

Advanced result file writing options:
- Custom result format templates
- Conditional result writing
- Result filtering and sampling
- File rotation based on size/time
- Multiple simultaneous result files

#### `jmeter-plugins-dummy-0.4.jar`
**Category:** Testing Utility  
**Purpose:** Dummy Sampler

Lightweight placeholder sampler for:
- Test structure development without actual HTTP calls
- Load testing logic validation
- Thread behavior analysis
- Reducing test infrastructure load during development

#### `jmeter-plugins-pde-0.1.jar`
**Category:** Plugin Management  
**Purpose:** Plugin Dependency Extractor

Utility for analyzing and managing plugin dependencies:
- Plugin version detection
- Dependency chain analysis
- Conflict resolution
- Classpath troubleshooting
- Plugin compatibility verification

### Schema & Validation Plugins

#### `ApacheJmeter_Schema_Assertion-1.1.0.jar`
**Category:** Validation  
**Purpose:** JSON/XML Schema Assertion

Validates response payloads against defined schemas:
- JSON Schema validation (Draft 4, 6, 7)
- XML Schema (XSD) validation
- Automatic schema inference
- Schema-based test failure reporting
- Contract testing support

#### `validatetg-1.0.1.jar`
**Category:** Thread Group Validation  
**Purpose:** Validate Test Group Plugin

Validates thread group configuration and behavior:
- Thread ramp-up validation
- Arrival rate feasibility checks
- Resource requirement estimation
- Configuration conflict detection
- Best practice enforcement

#### `yongfa365-jmeter-plugin-3.0.1.jar`
**Category:** Custom Extensions  
**Purpose:** Extended JMeter Functionality

Community-contributed plugin providing:
- Additional samplers for specialized protocols
- Custom result processors
- Advanced test data generators
- Integration connectors for external systems

### Debugging & Development

#### `jmeter-debugger-0.6.jar`
**Category:** Development Tool  
**Purpose:** JMeter Debugger Plugin

Step-through debugging for JMeter test plans:
- Set breakpoints on samplers
- Step through test execution
- Inspect variable values
- Modify values during execution
- Call stack visualization
- Test flow tracing

Essential for troubleshooting complex test plans and validating test logic.

### Plugin Management

#### `jmeter-plugins-manager-1.11.jar` / `1.9.jar`
**Category:** Plugin Infrastructure  
**Purpose:** JMeter Plugins Manager

The plugins manager is the central hub for plugin ecosystem management:
- Browse available plugins from plugin marketplace
- One-click plugin installation/uninstallation
- Automatic dependency resolution
- Plugin version management
- Conflict detection and resolution
- Plugin updates and rollback
- Offline plugin repository support

Dual versions (1.11.jar and 1.9.jar) may indicate:
- Version transition period
- Backward compatibility support
- Testing against different manager versions
- Gradual upgrade strategy

---

## Added/Upgraded Libraries

### Selenium WebDriver Suite (v4.8.1+)

The complete Selenium 4.8.1 WebDriver stack enables browser automation:

#### Core Selenium Libraries
- **`selenium-api-4.8.1.jar`** — Core Selenium API interfaces and base classes
- **`selenium-java-4.8.1.jar`** — High-level Java API for WebDriver
- **`selenium-support-4.8.1.jar`** — Helper utilities and common patterns
- **`selenium-http-4.8.1.jar`** — HTTP communication for WebDriver protocol
- **`selenium-json-4.8.1.jar`** — JSON serialization for WebDriver commands
- **`selenium-remote-driver-4.8.1.jar`** — Remote WebDriver client for Selenium Grid

#### Browser-Specific Drivers
- **`selenium-chrome-driver-4.8.1.jar`** — Google Chrome browser automation
- **`selenium-chromium-driver-4.8.1.jar`** — Base Chromium-based browser support
- **`selenium-firefox-driver-4.8.1.jar`** — Mozilla Firefox browser automation
- **`selenium-edge-driver-4.8.1.jar`** — Microsoft Edge browser automation
- **`selenium-ie-driver-4.8.1.jar`** — Internet Explorer browser automation (legacy)

#### Browser Development Tools
- **`selenium-devtools-v108-4.8.1.jar`** — Chrome DevTools Protocol v108 support for Chrome inspector capabilities, network monitoring, and performance profiling

#### Cross-Platform Support
- **`selenium-os-4.13.0.jar`** — Operating system detection and browser path resolution for cross-platform testing (Windows, macOS, Linux)

Selenium 4.8.1 brings modern WebDriver capabilities including:
- W3C WebDriver Protocol compliance
- Shadow DOM element location
- Bidirectional Protocol support
- Enhanced mobile browser testing
- Better performance monitoring

---

### OpenTelemetry Observability Stack (v1.24.0+)

A complete observability solution for distributed tracing, metrics, and logging:

#### Core API
- **`opentelemetry-api-1.24.0.jar`** — Core API for trace, metric, and baggage handling
- **`opentelemetry-context-1.24.0.jar`** — Context propagation and scope management
- **`opentelemetry-api-events-1.24.0-alpha.jar`** — Event API for structured event logging
- **`opentelemetry-api-logs-1.24.0-alpha.jar`** — Logging API integration

#### SDK Implementation
- **`opentelemetry-sdk-1.24.0.jar`** — Trace SDK implementation
- **`opentelemetry-sdk-trace-1.24.0.jar`** — Distributed tracing SDK
- **`opentelemetry-sdk-metrics-1.24.0.jar`** — Metrics collection and aggregation SDK
- **`opentelemetry-sdk-logs-1.24.0-alpha.jar`** — Logging SDK (alpha)
- **`opentelemetry-sdk-common-1.24.0.jar`** — Common utilities and base classes
- **`opentelemetry-sdk-extension-autoconfigure-1.24.0-alpha.jar`** — Auto-configuration from environment
- **`opentelemetry-sdk-extension-autoconfigure-spi-1.24.0.jar`** — Service provider interface for auto-configuration

#### Exporters & Data Processors
- **`opentelemetry-exporter-common-1.24.0.jar`** — Common exporter utilities
- **`opentelemetry-exporter-logging-1.24.0.jar`** — Log-based exporter for debugging
- **`opentelemetry-exporter-otlp-1.31.0.jar`** — OpenTelemetry Protocol (OTLP) exporter (v1.31.0 for latest features)
- **`opentelemetry-exporter-otlp-common-1.31.0.jar`** — OTLP common utilities

#### Semantic Conventions
- **`opentelemetry-semconv-1.24.0-alpha.jar`** — Standard attribute names and values for consistency across instrumentation

OpenTelemetry integration enables:
- Distributed trace correlation across services
- Automatic instrumentation of HTTP requests
- Custom span creation for test phase tracking
- Metrics export to observability backends (Datadog, New Relic, Grafana Tempo)
- Unified observability platform for test and application metrics

---

### Browser Testing & HTML Processing Libraries

#### `htmlunit-2.70.0.jar` + Stack
- **`htmlunit-2.70.0.jar`** — Headless browser emulation with full HTML/CSS/JavaScript rendering
- **`htmlunit-core-js-2.70.0.jar`** — JavaScript engine (Rhino-based)
- **`htmlunit-cssparser-1.14.0.jar`** — CSS parsing and cascading
- **`htmlunit-xpath-2.70.0.jar`** — XPath query support
- **`neko-htmlunit-2.70.0.jar`** — HTML5 parser integration

HTMLUnit provides a lightweight alternative to Selenium for:
- Headless browser testing without a real browser process
- Fast page load simulation
- Form submission and navigation testing
- Cookie and session management
- JavaScript execution without browser overhead

#### `jsyntaxpane-1.0.0.jar`
**Purpose:** Syntax-Highlighted Code Editor Component

Provides a Swing-based code editor with syntax highlighting for:
- JMeter script editing in the GUI
- Test plan source code viewing
- JSR223 script editing with language detection
- Real-time syntax validation
- Code formatting and beautification

---

### Async HTTP & Networking Stack

#### Async HTTP Client
- **`async-http-client-2.12.3.jar`** — Non-blocking HTTP client for high-concurrency scenarios
- **`async-http-client-netty-utils-2.12.3.jar`** — Netty integration utilities

The async HTTP client enables:
- Non-blocking I/O for higher concurrent connections
- Connection pooling and multiplexing
- Automatic request/response compression
- Proxy support with authentication
- WebSocket upgrade support

#### Netty 4.1.89.Final Stack
Complete network I/O framework:
- **`netty-buffer-4.1.89.Final.jar`** — Efficient byte buffer management
- **`netty-codec-4.1.89.Final.jar`** — Protocol codec framework
- **`netty-codec-http-4.1.89.Final.jar`** — HTTP protocol codec
- **`netty-common-4.1.89.Final.jar`** — Common utilities
- **`netty-handler-4.1.89.Final.jar`** — Channel event handler framework
- **`netty-resolver-4.1.89.Final.jar`** — DNS resolution
- **`netty-transport-4.1.89.Final.jar`** — Transport abstraction (NIO, epoll, Kqueue)
- **`netty-reactive-streams-2.0.8.jar`** — Reactive Streams adaptation for Netty

Netty provides the foundation for:
- High-performance non-blocking I/O
- Thousands of concurrent connections
- Custom protocol implementation
- Advanced networking patterns (multiplexing, keepalive)

#### WebSocket Stack (Jetty)
- **`websocket-api-9.1.1.v20140108.jar`** — WebSocket API specification
- **`websocket-client-9.1.1.v20140108.jar`** — WebSocket client implementation
- **`websocket-common-9.1.1.v20140108.jar`** — Common WebSocket utilities

---

### HTTP Client Libraries

#### `okhttp-4.12.0.jar` + `okio-jvm-3.6.0.jar`
**Purpose:** Modern HTTP Client

OkHTTP is a popular alternative HTTP client providing:
- HTTP/2 and HTTP/1.1 support
- Automatic connection pooling
- Transparent gzip compression
- Interceptor framework for request/response modification
- Certificate pinning for security
- Reactive/coroutine support via extensions

This complements the native JMeter HTTP sampler for:
- Testing HTTP/2 specific features
- Advanced proxy scenarios
- Custom header injection
- Request interception and modification

---

### Java Bytecode & Runtime Utilities

#### `byte-buddy-1.14.1.jar`
**Purpose:** Dynamic Java Bytecode Generation

ByteBuddy enables runtime:
- Dynamic class generation
- Method interception and wrapping
- Runtime annotation processing
- Proxy generation without bytecode files
- Instrumentation for performance monitoring

Used by test frameworks for:
- Dynamic mock object creation
- AOP-style test instrumentation
- Runtime behavior modification

#### `failsafe-3.3.0.jar`
**Purpose:** Retry & Resilience Policies

Failsafe provides:
- Configurable retry strategies (exponential backoff, jitter)
- Circuit breaker pattern implementation
- Timeout management
- Bulkhead pattern (resource isolation)
- Event listeners for success/failure tracking

Essential for:
- Resilient API client testing
- Transient failure handling
- Chaos engineering patterns
- Testing retry logic

---

### Command Execution & Process Management

#### `cmdrunner-2.3.jar`
**Purpose:** JMeter Plugin Command Runner

JMeter plugin infrastructure for:
- Running external commands with environment setup
- Capturing command output
- Error stream handling
- Cross-platform command execution

#### `commons-exec-1.3.jar`
**Purpose:** Apache Commons Exec

Simplified process execution:
- Execute system commands safely
- Handle output/error streams
- Process timeout management
- Environment variable passing
- Platform-independent command execution

Used for:
- System resource monitoring
- Application startup/shutdown
- Health check execution

---

### Utility Libraries

#### `guava-31.1-jre.jar`
**Purpose:** Google Core Libraries

Essential utility collection:
- Collections utilities (multimap, multiset, immutable types)
- Caching framework
- String utilities
- Preconditions and contracts
- Event bus for decoupled communication
- Hash functions and streaming

#### `jmeter-plugins-cmn-jmeter-0.7.jar`
**Purpose:** JMeter Plugin Commons

Shared utilities for JMeter plugins:
- Common GUI components
- Plugin base classes
- Standard listener implementations
- Plugin configuration helpers
- Metric aggregation utilities

#### `json-lib-2.4-jdk15.jar`
**Purpose:** JSON Object Model Library

JSON processing:
- JSON to/from Java object conversion
- Flexible object mapping
- Complex nested structure handling
- Pretty-printing and formatting
- JSONArray and JSONObject manipulation

---

### Template Engine

#### `velocity-engine-core-2.3.jar` + `velocity-engine-scripting-2.3.jar`
**Purpose:** Apache Velocity Template Engine

Velocity is a lightweight templating language used for:
- Dynamic test plan generation
- Report template processing
- HTML report generation with dynamic content
- Configuration file templating
- Log and result file formatting

These libraries enable:
- Embedding Velocity templates in JMeter
- JSR223 Velocity scripting support
- Template-driven test report generation

---

### Database Drivers

#### `postgresql-42.5.1.jar` (UPGRADED)
**Original:** 42.2.20  
**Current:** 42.5.1

PostgreSQL JDBC driver upgrade provides:
- Support for newer PostgreSQL 13-15 server versions
- Improved JSONB and range type support
- Enhanced SSL/TLS configuration
- Performance improvements in connection pooling
- Bug fixes and security patches
- Better error messages and diagnostics

#### `ojdbc14.jar`
**Purpose:** Oracle Database JDBC Driver

Oracle database connectivity for:
- Testing against Oracle backend systems
- JDBC sampling in test plans
- Database performance monitoring
- Oracle-specific connection pooling

---

### HTTP Server Components (Jetty)

#### Jetty 9.1.1.v20140108 Stack
- **`jetty-http-9.1.1.v20140108.jar`** — HTTP protocol implementation
- **`jetty-io-9.1.1.v20140108.jar`** — Non-blocking I/O implementation
- **`jetty-util-9.1.1.v20140108.jar`** — Utility classes

Jetty provides:
- Embedded HTTP server for test reporting
- Mock server for local testing
- Proxy server capabilities
- WebSocket server support

---

### Plotting & Visualization Libraries

#### Lets-Plot Libraries (2.2.1)
- **`lets-plot-batik-2.2.1.jar`** — SVG rendering engine integration
- **`lets-plot-common-2.2.1.jar`** — Common visualization utilities

Lets-Plot is a Kotlin-based plotting library providing:
- Statistical graphics
- Interactive plot generation
- ggplot2-style grammar of graphics

#### Portable JVM Plot Libraries (2.2.1+)
- **`plot-api-jvm-3.1.1.jar`** — Core plotting API
- **`plot-base-portable-jvm-2.2.1.jar`** — Base plotting functions
- **`plot-builder-portable-jvm-2.2.1.jar`** — Plot construction DSL
- **`plot-common-portable-jvm-2.2.1.jar`** — Common plotting utilities
- **`plot-config-portable-jvm-2.2.1.jar`** — Plot configuration
- **`vis-svg-portable-jvm-2.2.1.jar`** — SVG visualization output

These libraries enable:
- Data visualization in test reports
- Performance metric charts
- Statistical analysis plots
- Interactive result exploration

---

### Test Reporting

#### `allure-context-uber.jar`
**Purpose:** Allure Test Reporting Integration

Allure is a comprehensive test reporting framework providing:
- Test execution history
- Trend analysis over builds
- Test case categorization and filtering
- Automatic screenshot/log attachment
- Test hierarchy and dependencies
- Failed test root cause analysis

The "uber" JAR includes all dependencies, making:
- Simple integration without dependency management
- Reliable deployment across environments

---

### Miscellaneous Utilities

#### `MonteMedia.jar`
**Purpose:** Media Library

Used for:
- Screen recording during test execution
- Video capture of UI automation
- Audio processing (if needed)
- Media file handling

---

## Customization Summary

### Categorization by Purpose

#### 1. **Distributed Testing & Observability** (Core Enhancement)
- OpenTelemetry stack (17 libraries)
- InfluxDB and Prometheus plugins
- Allure reporting
- System metrics collection (PerfMon)

**Impact:** Transforms JMeter from a standalone tool to an observable, distributed testing platform. Metrics flow to central monitoring systems, enabling live test dashboard visualization and post-test analysis.

#### 2. **Browser & UI Automation** (New Capability)
- Selenium 4.8.1 WebDriver (10+ libraries)
- HTMLUnit headless browser
- WebDriver plugin for JMeter
- Chrome WebDriver binary

**Impact:** Enables realistic user experience testing with actual browser rendering, JavaScript execution, and real page load metrics alongside synthetic API testing.

#### 3. **WebSocket & Real-time Testing** (Protocol Support)
- Multiple WebSocket sampler versions
- Java WebSocket library
- WebSocket plugin

**Impact:** Extends JMeter beyond HTTP/HTTPS to support modern real-time communication patterns (WebSocket, Server-Sent Events, streaming responses).

#### 4. **Advanced Plugin Ecosystem** (28 plugins total)
- Custom thread groups (CASUTG for constant arrival rate)
- Graph and visualization plugins
- Schema validation
- Extended functions and utilities

**Impact:** Removes limitations of core JMeter through community-contributed plugins for specialized scenarios (realistic load patterns, advanced result analysis, contract testing).

#### 5. **Enhanced Infrastructure Support**
- PostgreSQL 42.5.1 (upgraded)
- Oracle JDBC driver
- RMI keystores
- Distributed testing support

**Impact:** Broadens database and infrastructure compatibility for diverse testing environments.

#### 6. **Performance & Scalability**
- Async HTTP client
- Netty 4.1.89 (non-blocking I/O)
- Connection pooling and multiplexing
- High-concurrency networking

**Impact:** Enables thousands of concurrent connections with lower resource consumption through non-blocking I/O patterns.

### Technical Footprint

| Category | Count | Impact |
|----------|-------|--------|
| Plugins Added | 28 | Functional breadth |
| Libraries Added | 100+ | 300+ MB additional |
| Configuration Changes | 2 modified, 1 new | Environment specificity |
| Binary Support Files | 15+ | Testing capabilities |

### Deployment Considerations

1. **Storage:** The customized installation is approximately 400-500 MB vs. ~150 MB for base JMeter
2. **Memory:** OpenTelemetry and plugin manager increase base memory footprint by ~50-100 MB
3. **Performance:** Async libraries and Netty improve throughput for high-concurrency scenarios
4. **Complexity:** More plugins increase test plan design options but require skill to use effectively
5. **Maintenance:** Plugin updates and security patches require coordinated management
