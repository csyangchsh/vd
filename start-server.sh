#!/bin/bash
# VectorDB Server Startup Script

# Set default values
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${CONFIG_FILE:-$SCRIPT_DIR/config.yaml}"
DAEMON_MODE="${DAEMON_MODE:-false}"
PID_FILE="$SCRIPT_DIR/vectordb.pid"
LOG_FILE="$SCRIPT_DIR/vectordb.log"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --config|-c)
            CONFIG_FILE="$2"
            shift 2
            ;;
        --daemon|-d)
            DAEMON_MODE=true
            shift
            ;;
        --stop)
            # Stop running server
            if [ -f "$PID_FILE" ]; then
                PID=$(cat "$PID_FILE")
                kill "$PID" 2>/dev/null
                rm -f "$PID_FILE"
                echo "VectorDB server stopped (PID: $PID)"
            else
                echo "VectorDB server is not running"
                exit 1
            fi
            exit 0
            ;;
        --status)
            # Show server status
            if [ -f "$PID_FILE" ]; then
                PID=$(cat "$PID_FILE")
                if ps -p "$PID" > /dev/null 2>&1; then
                    echo "VectorDB server is running (PID: $PID)"
                    exit 0
                else
                    echo "VectorDB server PID file exists but process is not running"
                    rm -f "$PID_FILE"
                    exit 1
                fi
            else
                echo "VectorDB server is not running"
                exit 1
            fi
            ;;
        --restart)
            # Restart server
            $0 --stop
            sleep 2
            $0 "$@"
            exit $?
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--config FILE] [--daemon] [--stop] [--status] [--restart]"
            exit 1
            ;;
    esac
done

# Check if server is already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "VectorDB server is already running (PID: $PID)"
        echo "Use '$0 --stop' to stop it first"
        exit 1
    else
        # Stale PID file
        rm -f "$PID_FILE"
    fi
fi

# Build the project if needed
if [ ! -f "$SCRIPT_DIR/target/simple-vector-db-1.0.0.jar" ]; then
    echo "Building VectorDB..."
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "Build failed"
        exit 1
    fi
fi

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Config file not found: $CONFIG_FILE"
    exit 1
fi

# Create data directory
mkdir -p "$(dirname "$CONFIG_FILE")/data/vectordb"

# Start the server
echo "Starting VectorDB server..."
echo "  Config: $CONFIG_FILE"
echo "  Daemon: $DAEMON_MODE"

# Java options
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx4g}"
JAVA_OPTS="$JAVA_OPTS --enable-preview --add-modules=jdk.incubator.vector"

# Fat JAR path (created by maven-shade-plugin)
JAR_FILE="$SCRIPT_DIR/target/simple-vector-db-1.0.0.jar"

if [ "$DAEMON_MODE" = "true" ]; then
    # Start in daemon mode using -jar flag
    nohup java $JAVA_OPTS -jar "$JAR_FILE" \
        --config "$CONFIG_FILE" \
        --daemon \
        >> "$LOG_FILE" 2>&1 &

    PID=$!
    echo $PID > "$PID_FILE"

    echo "VectorDB server started in daemon mode"
    echo "  PID: $PID"
    echo "  Log: $LOG_FILE"
    echo ""
    echo "To view logs: tail -f $LOG_FILE"
    echo "To stop server: $0 --stop"
    echo "To check status: $0 --status"
else
    # Start in foreground mode using -jar flag
    java $JAVA_OPTS -jar "$JAR_FILE" \
        --config "$CONFIG_FILE"
fi
