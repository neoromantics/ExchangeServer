#!/bin/bash
# This script launches multiple concurrent instances of RequestClient,
# each sending a different XML request to the server.

# Define the host and port for the server.
HOST="localhost"
PORT="12345"

# Define an array of XML file names relative to the RequestClient/src/main/resources directory.
EXAMPLES=(
    "create1.xml"
    "create2.xml"
    "transactions1.xml"
    "transactions2.xml"
    "transactions3.xml"
    "query1.xml"
    "cancel1.xml"
    "transactions4.xml"
    "create3.xml"
)

# Determine the number of client instances to launch.
NUM_CLIENTS=${#EXAMPLES[@]}
echo "Starting load test with ${NUM_CLIENTS} concurrent clients..."

# Base path for example XML files.
EXAMPLES_DIR="RequestClient/src/main/resources"

# Path to the executable jar file.
JAR_PATH="RequestClient/target/RequestClient-1.0-SNAPSHOT.jar"

# Loop over the examples and launch an instance for each.
for file in "${EXAMPLES[@]}"; do
    echo "Launching client for file: ${file}"
    # Launch the client in the background.
    java -jar "$JAR_PATH" "${EXAMPLES_DIR}/${file}" "$HOST" "$PORT" &
done

# Wait for all clients to finish.
wait
echo "Load test completed."
