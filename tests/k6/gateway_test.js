import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const acceptedLogs = new Counter('accepted_logs');
const droppedLogs = new Counter('dropped_logs');
const errorRate = new Rate('errors');
const processingLatency = new Trend('processing_latency');

// Parse TENANTS_CONFIG: "tenantId1:eps1,tenantId2:eps2"
const tenantsConfigStr = __ENV.TENANTS_CONFIG || 'test-tenant:1000';
const tenants = tenantsConfigStr.split(',').map(t => {
  const parts = t.split(':');
  return { id: parts[0], quota: parseInt(parts[1], 10) };
});

const dynamicScenarios = {};
tenants.forEach((t, index) => {
  // OTEL Collector Batch Processor logic: 
  // Target fluctuating EPS, but base batch logic on quota
  let batchSize = Math.round(Math.min(8000, t.quota * 0.2));
  if (batchSize < 1) batchSize = 1;

  // Generate 5 deterministic random stages (1m each) for fluctuation between 80% and 150%
  const stages = [];
  let seed = index + 1;
  for (let i = 0; i < 5; i++) {
    let r = Math.sin(seed++) * 10000;
    r = r - Math.floor(r); // pseudo random 0 to 1
    const pct = 2.0 + (r * 4.0); // 200% to 600%
    const targetEps = Math.round(t.quota * pct);
    stages.push({ duration: '1m', target: targetEps });
  }

  // VUs calculated based on absolute maximum possible EPS (600%)
  const maxEps = t.quota * 6.0;
  const maxIntervalSec = batchSize / maxEps;
  const maxIterRate = 1 / maxIntervalSec;

  // Set startRate based on the first random value
  let rStart = Math.sin(seed++) * 10000;
  rStart = rStart - Math.floor(rStart);
  const startEps = Math.round(t.quota * (2.0 + rStart * 4.0));

  dynamicScenarios[`tenant_${index}`] = {
    executor: 'ramping-arrival-rate',
    startRate: startEps,
    timeUnit: `${batchSize}s`,
    stages: stages,
    preAllocatedVUs: Math.max(2, Math.min(50, Math.ceil(maxIterRate * 2))),
    maxVUs: Math.max(10, Math.min(200, Math.ceil(maxIterRate * 10))),
    env: {
      CURRENT_TENANT_ID: t.id,
      CURRENT_BATCH_SIZE: batchSize.toString()
    },
  };
});

export const options = {
  scenarios: dynamicScenarios,
};

export default function () {
  const url = __ENV.TARGET_URL || 'http://localhost:4318/api/v1/logs';
  const tenantId = __ENV.CURRENT_TENANT_ID || 'test-tenant';
  const agentName = __ENV.AGENT_NAME || 'k6-agent';
  const tenantBatchSize = parseInt(__ENV.CURRENT_BATCH_SIZE || '1', 10);

  let payloadArray = [];
  for (let i = 0; i < tenantBatchSize; i++) {
    payloadArray.push({
      "log.source": "k6-load-test",
      "timestamp": new Date().toISOString(),
      "payload": {
        "event_id": Math.floor(Math.random() * 1000000),
        "sourceIp": "10.0.0.1",
        "message": "Test log from K6 load generator"
      }
    });
  }

  const payload = JSON.stringify(payloadArray);

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': tenantId,
      'X-Agent-Name': agentName,
    },
  };

  const res = http.post(url, payload, params);

  processingLatency.add(res.timings.duration);

  // Status 202 Accepted, 429 Too Many Requests
  const isAccepted = res.status === 202 || res.status === 200;
  const isDropped = res.status === 429;

  check(res, {
    'is accepted (202)': (r) => r.status === 202 || r.status === 200,
    'is dropped (429)': (r) => r.status === 429,
  });

  if (isAccepted) {
    acceptedLogs.add(tenantBatchSize);
  } else if (isDropped) {
    droppedLogs.add(tenantBatchSize);
  } else {
    errorRate.add(1);
  }
}
