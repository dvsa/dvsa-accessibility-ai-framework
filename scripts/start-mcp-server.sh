#!/bin/bash

# Accessibility MCP Server Startup Script
# This script starts the MCP server for real-time accessibility guidelines scraping

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

# Configuration
MCP_PORT=${MCP_PORT:-8080}
MCP_HOST=${MCP_HOST:-localhost}
JAVA_OPTS=${JAVA_OPTS:-"-Xmx1g -Xms512m"}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Accessibility MCP Server...${NC}"
echo "Port: $MCP_PORT"
echo "Host: $MCP_HOST"
echo "Java Options: $JAVA_OPTS"
echo ""

# Check if port is available
if lsof -Pi :$MCP_PORT -sTCP:LISTEN -t >/dev/null ; then
    echo -e "${RED}ERROR: Port $MCP_PORT is already in use${NC}"
    echo "Please stop the existing process or use a different port"
    exit 1
fi

# Set Java options
export JAVA_OPTS="$JAVA_OPTS"

# Navigate to project directory
cd "$PROJECT_ROOT"

# Build the project if needed
if [ ! -d "target/test-classes" ] || [ "src" -nt "target" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    export JAVA_HOME=/Users/Wonderson.Chideya2/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
    mvn test-compile -q
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed!${NC}"
        exit 1
    fi
fi

# Set proper Java for running
export JAVA_HOME=/Users/Wonderson.Chideya2/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

# Set classpath
CLASSPATH="target/test-classes:target/classes"
for jar in $(find target/dependency -name "*.jar" 2>/dev/null); do
    CLASSPATH="$CLASSPATH:$jar"
done

# Add Maven dependencies to classpath
if [ -f ".m2_classpath" ]; then
    CLASSPATH="$CLASSPATH:$(cat .m2_classpath)"
else
    # Generate classpath if not cached
    mvn dependency:build-classpath -Dmdep.outputFile=.m2_classpath -q
    if [ -f ".m2_classpath" ]; then
        CLASSPATH="$CLASSPATH:$(cat .m2_classpath)"
    fi
fi

# Create PID file directory
PID_DIR="$PROJECT_ROOT/run"
mkdir -p "$PID_DIR"
PID_FILE="$PID_DIR/mcp-server.pid"

# Function to handle shutdown
shutdown() {
    echo -e "\n${YELLOW}Shutting down MCP Server...${NC}"
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        kill $PID 2>/dev/null
        rm -f "$PID_FILE"
    fi
    echo -e "${GREEN}MCP Server stopped${NC}"
    exit 0
}

# Set up signal handlers
trap shutdown SIGINT SIGTERM

# Start the MCP server
echo -e "${GREEN}Starting MCP Server on $MCP_HOST:$MCP_PORT...${NC}"
echo "Press Ctrl+C to stop"
echo ""

# Set environment variables
export MCP_SERVER_HOST="$MCP_HOST"
export MCP_SERVER_PORT="$MCP_PORT"

# Start the server in background and capture PID
$JAVA_HOME/bin/java $JAVA_OPTS -cp "$CLASSPATH" org.dvsa.testing.framework.mcp.AccessibilityMcpServer &
MCP_PID=$!

# Save PID
echo $MCP_PID > "$PID_FILE"

# Wait for server to start
sleep 3

# Check if server is running
if kill -0 $MCP_PID 2>/dev/null; then
    echo -e "${GREEN}MCP Server started successfully!${NC}"
    echo "PID: $MCP_PID"
    echo "Endpoint: http://$MCP_HOST:$MCP_PORT"
    echo ""
    echo "The server is now scraping real-time accessibility guidelines from:"
    echo "  • GOV.UK Design System (design-system.service.gov.uk)"
    echo "  • W3C WCAG Guidelines (w3.org/WAI/WCAG22)"
    echo "  • W3C ARIA Specifications (w3.org/WAI/ARIA)"
    echo ""
    echo -e "${YELLOW}Logs will appear below:${NC}"
else
    echo -e "${RED}Failed to start MCP Server${NC}"
    rm -f "$PID_FILE"
    exit 1
fi

# Wait for the background process
wait $MCP_PID