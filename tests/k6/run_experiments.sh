#!/bin/bash

# Configuration
API_URL="http://localhost:8080/api/v1"
GATEWAY_URL="https://localhost:4318/api/v1/logs"
K6_SCRIPT="gateway_test.js"

echo "=========================================="
echo " SOC License Platform - K6 Benchmark Tool"
echo "=========================================="

# Function to create a tenant and license, returns the tenant ID
setup_tenant() {
    local quota=$1
    echo "[Setup] Creating a new tenant..." >&2
    local tenant_res=$(curl -s -X POST "$API_URL/tenants" \
        -H "Content-Type: application/json" \
        -d '{"name": "K6-Test-Tenant-'"$(date +%s)"'"}')
    
    local tenant_id=$(echo $tenant_res | grep -o '"tenantId":"[^"]*' | cut -d'"' -f4)
    if [ -z "$tenant_id" ]; then
        echo "[Error] Failed to create tenant: $tenant_res" >&2
        exit 1
    fi
    
    echo "[Setup] Tenant Created: $tenant_id" >&2
    
    echo "[Setup] Creating license with $quota EPS..." >&2
    local start_date=$(date -d "yesterday" +"%Y-%m-%d")
    local end_date=$(date -d "+30 days" +"%Y-%m-%d")
    
    local license_res=$(curl -s -X POST "$API_URL/licenses" \
        -H "Content-Type: application/json" \
        -d '{
            "tenantId": "'$tenant_id'",
            "epsQuota": '$quota',
            "startDate": "'$start_date'",
            "endDate": "'$end_date'"
        }')
        
    echo "[Setup] License Created." >&2
    echo $tenant_id
}

echo "Select an experiment to run:"
echo "1) Scenario 1: Data Plane Multi-Tenant Performance & Rate Limiting Test (10 Tenants)"
echo "2) Scenario 2: Control Plane Management API Performance Test (CRUD Operations)"
read -p "Enter scenario number (1, 2): " SCENARIO

case $SCENARIO in
    1)
        echo "Running Scenario 1: Data Plane Multi-Tenant Performance Test (10 Tenants)..."
        TENANTS_CONFIG=""
        for i in {1..10}; do
            # Random quota: 90% chance for 100-5000, 10% chance for 5001-10000
            R=$(( RANDOM % 10 ))
            if [ $R -lt 9 ]; then
                QUOTA=$(( (RANDOM % 4901) + 100 ))
            else
                QUOTA=$(( (RANDOM % 5000) + 5001 ))
            fi
            
            echo "Creating Tenant $i (Quota: $QUOTA EPS)..."
            T_ID=$(setup_tenant $QUOTA)
            if [ -z "$TENANTS_CONFIG" ]; then
                TENANTS_CONFIG="${T_ID}:${QUOTA}"
            else
                TENANTS_CONFIG="${TENANTS_CONFIG},${T_ID}:${QUOTA}"
            fi
        done
        
        echo "Wait 5s for cache propagation..."
        sleep 5
        
        echo "Testing with 10 tenants..."
        TARGET_URL=$GATEWAY_URL TENANTS_CONFIG=$TENANTS_CONFIG DURATION=5m k6 run --insecure-skip-tls-verify gateway_test.js
        ;;
    2)
        echo "Running Scenario 2 (Management API Performance)..."
        k6 run --insecure-skip-tls-verify management_api_test.js
        ;;
    *)
        echo "Invalid scenario number."
        exit 1
        ;;
esac

echo "=========================================="
echo "Experiment completed at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "Check Grafana (http://localhost:3001) for metrics."
