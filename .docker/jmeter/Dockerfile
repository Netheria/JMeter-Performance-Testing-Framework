FROM eclipse-temurin:17-jdk

LABEL maintainer="JMeter Test Automation"
LABEL description="Apache JMeter 5.5 with custom plugins for performance testing"

# Build arguments with defaults
ARG JMETER_HEAP=2g
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        unzip \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Download and install JMeter 5.5
ENV JMETER_VERSION=5.5
ENV JMETER_HOME=/opt/jmeter
RUN curl -fSL https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.zip -o /tmp/jmeter.zip && \
    unzip -q /tmp/jmeter.zip -d /opt && \
    mv /opt/apache-jmeter-${JMETER_VERSION} $JMETER_HOME && \
    rm /tmp/jmeter.zip && \
    chmod +x $JMETER_HOME/bin/*.sh

# Copy custom plugins and libraries (overlay on top of JMeter installation)
COPY lib/ $JMETER_HOME/lib/
COPY lib/ext/ $JMETER_HOME/lib/ext/

# Copy test plans and data
COPY flows/ $JMETER_HOME/flows/
COPY resources/ $JMETER_HOME/resources/

# Copy test configuration
COPY bin/test.properties $JMETER_HOME/bin/test.properties

# Create results directory with write permissions
RUN mkdir -p $JMETER_HOME/results && \
    chmod 777 $JMETER_HOME/results

# Set working directory
WORKDIR $JMETER_HOME

# Set JMeter environment variables
ENV PATH=$JMETER_HOME/bin:$PATH
ENV JMETER_HEAP=${JMETER_HEAP:-2g}

# Health check: verify JMeter is functional
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD jmeter --version || exit 1

# Default entry point: JMeter shell script
ENTRYPOINT ["bin/jmeter.sh"]
