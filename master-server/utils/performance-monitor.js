class PerformanceMonitor {
  constructor() {
    this.metrics = {
      requestCounts: {},
      requestDurations: {},
      errorCounts: {},
      memorySnapshots: [],
      gcStats: [],
    };

    this.histogramBuckets = [10, 50, 100, 500, 1000, 2000, 5000, 10000];
    this.retentionPeriod = 24 * 60 * 60 * 1000;
  }

  trackRequest(method, path, statusCode, duration, size) {
    const endpoint = `${method} ${path}`;

    if (!this.metrics.requestCounts[endpoint]) {
      this.metrics.requestCounts[endpoint] = { total: 0, statusCodes: {} };
    }
    this.metrics.requestCounts[endpoint].total++;
    this.metrics.requestCounts[endpoint].statusCodes[statusCode] =
      (this.metrics.requestCounts[endpoint].statusCodes[statusCode] || 0) + 1;

    if (!this.metrics.requestDurations[endpoint]) {
      this.metrics.requestDurations[endpoint] = {
        samples: [],
        histogram: {},
      };
    }
    this.metrics.requestDurations[endpoint].samples.push({
      duration,
      timestamp: Date.now(),
    });

    // cap the sample history
    this.metrics.requestDurations[endpoint].samples = this.metrics.requestDurations[endpoint].samples.filter(
      (s) => s.timestamp > Date.now() - this.retentionPeriod,
    );

    if (statusCode >= 400) {
      if (!this.metrics.errorCounts[endpoint]) {
        this.metrics.errorCounts[endpoint] = 0;
      }
      this.metrics.errorCounts[endpoint]++;
    }
  }

  calculatePercentiles(endpoint) {
    const samples = this.metrics.requestDurations[endpoint]?.samples || [];
    if (samples.length === 0) return null;

    const sorted = samples
      .map((s) => s.duration)
      .sort((a, b) => a - b);

    return {
      p50: sorted[Math.floor(sorted.length * 0.5)],
      p95: sorted[Math.floor(sorted.length * 0.95)],
      p99: sorted[Math.floor(sorted.length * 0.99)],
      min: sorted[0],
      max: sorted[sorted.length - 1],
      avg: sorted.reduce((a, b) => a + b, 0) / sorted.length,
    };
  }

  captureMemoryMetrics() {
    const memUsage = process.memoryUsage();
    const snapshot = {
      timestamp: Date.now(),
      heapUsed: memUsage.heapUsed,
      heapTotal: memUsage.heapTotal,
      external: memUsage.external,
      rss: memUsage.rss,
      arrayBuffers: memUsage.arrayBuffers,
    };

    this.metrics.memorySnapshots.push(snapshot);

    this.metrics.memorySnapshots = this.metrics.memorySnapshots.filter(
      (s) => s.timestamp > Date.now() - this.retentionPeriod,
    );

    return snapshot;
  }

  trackGCEvent(type, duration, freed) {
    this.metrics.gcStats.push({
      timestamp: Date.now(),
      type,
      duration,
      freed,
    });

    this.metrics.gcStats = this.metrics.gcStats.filter(
      (s) => s.timestamp > Date.now() - this.retentionPeriod,
    );
  }

  exportMetrics() {
    let output = '';

    output += '# HELP securechat_http_requests_total Total HTTP requests\n';
    output += '# TYPE securechat_http_requests_total counter\n';

    for (const [endpoint, data] of Object.entries(this.metrics.requestCounts)) {
      const [method, path] = endpoint.split(' ');
      for (const [status, count] of Object.entries(data.statusCodes)) {
        output += `securechat_http_requests_total{method="${method}",path="${path}",status="${status}"} ${count}\n`;
      }
    }

    output += '\n# HELP securechat_http_request_duration_seconds HTTP request duration in seconds\n';
    output += '# TYPE securechat_http_request_duration_seconds histogram\n';

    for (const [endpoint, data] of Object.entries(this.metrics.requestDurations)) {
      const [method, path] = endpoint.split(' ');
      const percentiles = this.calculatePercentiles(endpoint);

      if (percentiles) {
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="0.01"} ${this.countBucket(data.samples, 10)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="0.05"} ${this.countBucket(data.samples, 50)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="0.1"} ${this.countBucket(data.samples, 100)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="0.5"} ${this.countBucket(data.samples, 500)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="1"} ${this.countBucket(data.samples, 1000)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="2"} ${this.countBucket(data.samples, 2000)}\n`;
        output += `securechat_http_request_duration_seconds_bucket{method="${method}",path="${path}",le="+Inf"} ${data.samples.length}\n`;
        output += `securechat_http_request_duration_seconds_sum{method="${method}",path="${path}"} ${data.samples.reduce((a, s) => a + s.duration, 0)}\n`;
        output += `securechat_http_request_duration_seconds_count{method="${method}",path="${path}"} ${data.samples.length}\n`;
      }
    }

    output += '\n# HELP securechat_process_memory_bytes Memory usage in bytes\n';
    output += '# TYPE securechat_process_memory_bytes gauge\n';

    if (this.metrics.memorySnapshots.length > 0) {
      const latest = this.metrics.memorySnapshots[this.metrics.memorySnapshots.length - 1];
      output += `securechat_process_memory_bytes{type="heap_used"} ${latest.heapUsed}\n`;
      output += `securechat_process_memory_bytes{type="heap_total"} ${latest.heapTotal}\n`;
      output += `securechat_process_memory_bytes{type="rss"} ${latest.rss}\n`;
      output += `securechat_process_memory_bytes{type="external"} ${latest.external}\n`;
    }

    output += '\n# HELP securechat_errors_total Total errors\n';
    output += '# TYPE securechat_errors_total counter\n';

    for (const [endpoint, count] of Object.entries(this.metrics.errorCounts)) {
      const [method, path] = endpoint.split(' ');
      output += `securechat_errors_total{method="${method}",path="${path}"} ${count}\n`;
    }

    output += '\n# HELP securechat_gc_events_total Garbage collection events\n';
    output += '# TYPE securechat_gc_events_total counter\n';

    const gcByCounts = this.metrics.gcStats.reduce((acc, stat) => {
      acc[stat.type] = (acc[stat.type] || 0) + 1;
      return acc;
    }, {});

    for (const [type, count] of Object.entries(gcByCounts)) {
      output += `securechat_gc_events_total{type="${type}"} ${count}\n`;
    }

    return output;
  }

  countBucket(samples, threshold) {
    return samples.filter((s) => s.duration <= threshold).length;
  }

  getSummary() {
    const summary = {};

    for (const [endpoint, data] of Object.entries(this.metrics.requestDurations)) {
      const percentiles = this.calculatePercentiles(endpoint);
      summary[endpoint] = {
        count: data.samples.length,
        percentiles,
        errorCount: this.metrics.errorCounts[endpoint] || 0,
      };
    }

    return summary;
  }

  reset() {
    this.metrics = {
      requestCounts: {},
      requestDurations: {},
      errorCounts: {},
      memorySnapshots: [],
      gcStats: [],
    };
  }
}

function performanceMonitoringMiddleware(monitor) {
  return (req, res, next) => {
    const startTime = Date.now();
    const startMem = process.memoryUsage().heapUsed;

    res.on('finish', () => {
      const duration = Date.now() - startTime;
      const size = res.get('content-length') || 0;

      monitor.trackRequest(req.method, req.path, res.statusCode, duration, size);
    });

    next();
  };
}

module.exports = {
  PerformanceMonitor,
  performanceMonitoringMiddleware,
};
