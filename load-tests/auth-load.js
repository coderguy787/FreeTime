/**
 * K6 Load Testing Configuration
 * 
 * Usage:
 * k6 run load-tests/auth-load.js
 * k6 run --vus 100 --duration 30s load-tests/auth-load.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
export const errorRate = new Rate('errors');
export const successRate = new Rate('success');
export const loginTime = new Trend('login_duration');
export const loginFailures = new Counter('login_failures');
export const registrationTime = new Trend('registration_duration');
export const registrationFailures = new Counter('registration_failures');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Ramp up to 50 users
    { duration: '1m', target: 100 },   // Ramp up to 100 users
    { duration: '30s', target: 200 },  // Ramp up to 200 users
    { duration: '2m', target: 200 },   // Stay at 200 users
    { duration: '30s', target: 0 },    // Ramp down to 0 users
  ],
  thresholds: {
    'http_req_duration': ['p(95)<2000', 'p(99)<3000'],
    'errors': ['rate<0.1'],
    'success': ['rate>0.9'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';
const API_VERSION = '/v1';
let registrationCounter = 0;

/**
 * Generate unique email for this VU
 */
function generateUniqueEmail() {
  const timestamp = Date.now();
  const vuId = __VU;
  registrationCounter++;
  return `user_${vuId}_${registrationCounter}_${timestamp}@loadtest.local`;
}

/**
 * Generate strong password
 */
function generatePassword() {
  return `TestPass123!${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Simulate user registration
 */
function testRegistration() {
  return group('Registration', () => {
    const email = generateUniqueEmail();
    const password = generatePassword();
    const username = `user_${__VU}_${registrationCounter}`;

    const payload = JSON.stringify({
      email,
      password,
      username,
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'K6LoadTest/1.0',
      },
    };

    const response = http.post(
      `${BASE_URL}${API_VERSION}/auth/register`,
      payload,
      params,
    );

    const success = check(response, {
      'registration status is 201': (r) => r.status === 201,
      'registration response has token': (r) => r.json('token') !== null,
      'registration response has user': (r) => r.json('user') !== null,
    });

    registrationTime.add(response.timings.duration);
    if (!success) {
      registrationFailures.add(1);
      errorRate.add(1);
    } else {
      successRate.add(1);
    }

    return response.json('token');
  });
}

/**
 * Simulate user login
 */
function testLogin(email, password) {
  return group('Login', () => {
    const payload = JSON.stringify({
      email,
      password,
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'K6LoadTest/1.0',
      },
    };

    const response = http.post(
      `${BASE_URL}${API_VERSION}/auth/login`,
      payload,
      params,
    );

    const success = check(response, {
      'login status is 200': (r) => r.status === 200,
      'login response has token': (r) => r.json('token') !== null,
      'login response has user': (r) => r.json('user') !== null,
    });

    loginTime.add(response.timings.duration);
    if (!success) {
      loginFailures.add(1);
      errorRate.add(1);
    } else {
      successRate.add(1);
    }

    return response.json('token');
  });
}

/**
 * Simulate brute force attack (rate limiting test)
 */
function testRateLimiting() {
  return group('Rate Limiting', () => {
    const payload = JSON.stringify({
      email: 'attacker@loadtest.local',
      password: 'wrongpassword',
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
      },
      timeout: '5s',
    };

    // Send 10 rapid requests
    for (let i = 0; i < 10; i++) {
      const response = http.post(
        `${BASE_URL}${API_VERSION}/auth/login`,
        payload,
        params,
      );

      // After 5 requests, should get rate limited (429)
      if (i >= 5) {
        check(response, {
          'rate limiting kicks in': (r) => r.status === 429 || r.status === 401,
        });
      }
    }
  });
}

/**
 * Test health check endpoints
 */
function testHealthChecks() {
  return group('Health Checks', () => {
    const endpoints = [
      '/health',
      '/health/live',
      '/health/ready',
    ];

    for (const endpoint of endpoints) {
      const response = http.get(`${BASE_URL}${endpoint}`);

      check(response, {
        [`${endpoint} status is 200`]: (r) => r.status === 200,
        [`${endpoint} response is JSON`]: (r) => r.headers['content-type'].includes('application/json'),
      });
    }
  });
}

/**
 * Main test function
 */
export default function () {
  // 10% of requests test health checks
  if (Math.random() < 0.1) {
    testHealthChecks();
    sleep(1);
    return;
  }

  // 20% test rate limiting
  if (Math.random() < 0.2) {
    testRateLimiting();
    sleep(2);
    return;
  }

  // 70% test normal registration and login
  const token = testRegistration();
  sleep(1);

  if (token) {
    // Success - now test login
    testLogin(generateUniqueEmail(), generatePassword());
  }

  sleep(1);
}

/**
 * Cleanup function
 */
export function teardown() {
  console.log('Load test completed');
}
