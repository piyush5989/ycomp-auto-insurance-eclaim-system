/**
 * K6 Load Test — Claim Submission Flow
 *
 * Tests the two most critical API paths:
 *   1. POST /api/v1/claims     (claim submission — p99 target: < 2500ms)
 *   2. GET  /api/v1/claims/:id (claim view — p99 target: < 1200ms)
 *
 * Run: k6 run --env BASE_URL=http://localhost:8090/api/v1 infra/load-tests/claim-submission.js
 * With token: k6 run --env BASE_URL=http://localhost:8090/api/v1 --env LOAD_TEST_TOKEN=<jwt> ...
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const submitClaimDuration = new Trend('submitClaim_duration');
const viewClaimDuration = new Trend('viewClaim_duration');
const errorRate = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '1m', target: 50 },    // Ramp up to 50 concurrent users
    { duration: '3m', target: 100 },   // Sustain 100 concurrent users (normal load)
    { duration: '2m', target: 200 },   // Spike to 200 (peak load)
    { duration: '1m', target: 0 },     // Ramp down
  ],
  thresholds: {
    // Per-operation SLAs (granular — not a single 5s blanket)
    'http_req_duration{name:submitClaim}': ['p99<2500'],   // Claim submission p99 < 2.5s
    'http_req_duration{name:viewClaim}':   ['p99<1200'],   // View claim p99 < 1.2s
    'http_req_failed':                     ['rate<0.01'],  // < 1% error rate
    'error_rate':                          ['rate<0.01'],
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090/api/v1';
const TOKEN = __ENV.LOAD_TEST_TOKEN || 'REPLACE_WITH_JWT_TOKEN';

const headers = {
  'Authorization': `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
  'X-Correlation-ID': `load-test-${Date.now()}`
};

export default function () {
  // ─── 1. Submit Claim ─────────────────────────────────────────────────
  const policyNum = `POL-${Math.floor(Math.random() * 100000000).toString().padStart(8, '0')}`;
  const regPlate = `TN${Math.floor(Math.random() * 100).toString().padStart(2, '0')}-${Math.floor(Math.random() * 9999).toString().padStart(4, '0')}`;

  const claimPayload = JSON.stringify({
    policyNumber: policyNum,
    vehicleRegistration: regPlate,
    incidentDate: '2026-04-01',
    incidentLocation: 'Highway 101, Test City',
    description: 'Load test claim — vehicle collision at traffic signal',
    claimType: 'COLLISION',
    policeReportFiled: true,
    policeReportNumber: `PD-LOAD-${Date.now()}`
  });

  const submitRes = http.post(`${BASE_URL}/claims`, claimPayload, {
    headers,
    tags: { name: 'submitClaim' }
  });

  submitClaimDuration.add(submitRes.timings.duration);
  errorRate.add(submitRes.status >= 400);

  const submitOk = check(submitRes, {
    'submit: status 201 or 200': (r) => r.status === 201 || r.status === 200,
    'submit: has claimId': (r) => {
      try {
        return JSON.parse(r.body).data?.claimId !== undefined;
      } catch { return false; }
    }
  });

  if (!submitOk) {
    console.error(`Submit failed: ${submitRes.status} - ${submitRes.body?.substring(0, 200)}`);
    return;
  }

  const claimId = JSON.parse(submitRes.body).data?.claimId;
  sleep(0.5);

  // ─── 2. View Claim Status ─────────────────────────────────────────────
  const viewRes = http.get(`${BASE_URL}/claims/${claimId}`, {
    headers,
    tags: { name: 'viewClaim' }
  });

  viewClaimDuration.add(viewRes.timings.duration);

  check(viewRes, {
    'view: status 200': (r) => r.status === 200,
    'view: has status field': (r) => {
      try {
        return JSON.parse(r.body).data?.status !== undefined;
      } catch { return false; }
    }
  });

  sleep(1);
}

export function handleSummary(data) {
  console.log('\n=== eClaims Load Test Summary ===');
  console.log(`Submit claim p99:  ${data.metrics['http_req_duration{name:submitClaim}']?.values?.['p(99)']?.toFixed(0)}ms (target < 2500ms)`);
  console.log(`View claim p99:    ${data.metrics['http_req_duration{name:viewClaim}']?.values?.['p(99)']?.toFixed(0)}ms (target < 1200ms)`);
  console.log(`Error rate:        ${(data.metrics['http_req_failed']?.values?.rate * 100)?.toFixed(2)}% (target < 1%)`);
  return {};
}
