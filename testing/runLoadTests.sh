#!/bin/bash
# runLoadTests.sh
# Revised version: separates create requests (Phase 1) and transaction, query, cancel requests (Phase 2)
# This ensures that dependent data (accounts, positions) exists before processing transactions.

HOST="localhost"
PORT="12345"

# Define the XML files (relative to the RequestClient/src/main/resources directory)
# Files starting with "create" are run in Phase 1; the rest will be run in Phase 2.
CREATE_EXAMPLES=(
    "create1.xml"
    "create2.xml"
    "create3.xml"
)

TRANSACTION_EXAMPLES=(
    "transactions1.xml"
    "transactions2.xml"
    "transactions3.xml"
    "query1.xml"
    "cancel1.xml"
    "transactions4.xml"
)

# Base path for XML files.
EXAMPLES_DIR="RequestClient/src/main/resources"
# Path to the executable jar file.
JAR_PATH="RequestClient/target/RequestClient-1.0-SNAPSHOT.jar"

echo "Phase 1: Running create requests sequentially..."

for file in "${CREATE_EXAMPLES[@]}"; do
    echo "Processing create request for file: ${file}"
    java -jar "$JAR_PATH" "${EXAMPLES_DIR}/${file}" "$HOST" "$PORT"
done

# Wait for a sufficient time to ensure that all create requests are fully processed.
echo "Waiting 10 seconds to ensure accounts and positions are created..."
sleep 10

echo "Phase 2: Running transaction, query, and cancel requests concurrently..."

for file in "${TRANSACTION_EXAMPLES[@]}"; do
    echo "Launching client for file: ${file}"
    java -jar "$JAR_PATH" "${EXAMPLES_DIR}/${file}" "$HOST" "$PORT" &
done

# Wait for all background processes to finish.
wait
echo "Load test completed."