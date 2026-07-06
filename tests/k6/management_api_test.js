import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const tenantCreateLatency = new Trend('tenant_create_latency');
const licenseCreateLatency = new Trend('license_create_latency');
const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    management_api_load: {
      executor: 'constant-arrival-rate',
      rate: 50, // 50 requests per second
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
};

export default function () {
  const url = __ENV.API_URL || 'http://localhost:8080/api/v1';

  // 1. Create Tenant
  const tenantPayload = JSON.stringify({
    name: `K6-Tenant-${Math.floor(Math.random() * 10000000)}`
  });
  
  const tenantRes = http.post(`${url}/tenants`, tenantPayload, {
    headers: { 'Content-Type': 'application/json' }
  });
  
  const tenantSuccess = check(tenantRes, {
    'tenant created': (r) => r.status === 201 || r.status === 200,
  });
  
  if (!tenantSuccess) {
    errorRate.add(1);
    return;
  }
  
  tenantCreateLatency.add(tenantRes.timings.duration);
  const tenantId = tenantRes.json('tenantId') || tenantRes.json('id');
  
  // 2. Create License
  if (tenantId) {
    // start date is today, end date is +30 days
    const now = new Date();
    const future = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
    const startDate = now.toISOString().split('T')[0];
    const endDate = future.toISOString().split('T')[0];

    const licensePayload = JSON.stringify({
      tenantId: tenantId,
      epsQuota: Math.floor(Math.random() * 5000) + 1000,
      startDate: startDate,
      endDate: endDate
    });
    
    const licenseRes = http.post(`${url}/licenses`, licensePayload, {
      headers: { 'Content-Type': 'application/json' }
    });
    
    const licenseSuccess = check(licenseRes, {
      'license created': (r) => r.status === 201 || r.status === 200,
    });

    if (licenseSuccess) {
      licenseCreateLatency.add(licenseRes.timings.duration);
    } else {
      errorRate.add(1);
    }
  }
}
