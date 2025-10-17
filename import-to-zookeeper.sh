#!/bin/bash

# Script to import Zookeeper configuration
# Usage: ./import-to-zookeeper.sh [zookeeper_host:port]
# Example: ./import-to-zookeeper.sh localhost:2181

ZOOKEEPER_HOST=${1:-localhost:2181}

echo "Importing Zookeeper configuration to $ZOOKEEPER_HOST"

# Create base paths
zkCli.sh -server $ZOOKEEPER_HOST create /dev ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/apiPrefix "/api"

# Create endpoints base path
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints ""

# Example Endpoint 1: People (with sequence support)
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/path "/people"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/httpMethod "GET"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/databaseCollection "people"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/type "REST"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/sequenceEnabled "true"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/People/defaultBulkSize "100"

# Example Endpoint 2: Users (without sequence support)
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/path "/users"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/httpMethod "GET"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/databaseCollection "users"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/type "REST"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/sequenceEnabled "false"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Users/defaultBulkSize "50"

# Example Endpoint 3: Products (GraphQL with sequence)
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/path "/products"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/httpMethod "POST"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/databaseCollection "products"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/type "GRAPHQL"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/sequenceEnabled "true"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/sigma/endpoints/Products/defaultBulkSize "200"

# Create dataSource base path
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource ""

# MongoDB configuration
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.host "localhost"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.port "27017"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.database "dynamic-graphql"
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.username ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.password ""
zkCli.sh -server $ZOOKEEPER_HOST create /dev/dataSource/mongodb.authDatabase "admin"

echo "Zookeeper configuration imported successfully!"
echo ""
echo "To verify, run:"
echo "  zkCli.sh -server $ZOOKEEPER_HOST ls /dev/sigma/endpoints"
echo ""
echo "Environment variables required to run the application:"
echo "  export ENV=dev"
echo "  export SERVICE=sigma"
echo "  export ZOOKEEPER_URL=localhost:2181    # Optional, defaults to localhost:2181"
echo "  export ZOOKEEPER_TIMEOUT=3000          # Optional, defaults to 3000ms"
