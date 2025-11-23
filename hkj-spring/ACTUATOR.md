# Spring Boot Actuator Integration

This guide covers the Spring Boot Actuator integration for higher-kinded-j, providing comprehensive monitoring and health checking for functional programming constructs in your Spring applications.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Components](#components)
  - [HkjMetricsService](#hkjmetricsservice)
  - [HkjMetricsEndpoint](#hkjmetricsendpoint)
  - [HkjAsyncHealthIndicator](#hkjasynchealthindicator)
- [Configuration](#configuration)
- [Metrics Details](#metrics-details)
- [Health Checks](#health-checks)
- [Integration Examples](#integration-examples)
- [Production Considerations](#production-considerations)
- [Testing](#testing)

## Overview

The higher-kinded-j Actuator integration provides:

- **Metrics tracking** for Either, Validated, and EitherT operations
- **Custom actuator endpoint** exposing HKJ configuration and metrics
- **Health indicator** for async executor monitoring
- **Micrometer integration** for metrics export to monitoring systems

### Benefits

- Monitor functional error handling in production
- Track success/failure rates for Either and EitherT
- Validate thread pool health for async operations
- Export metrics to Prometheus, Graphite, or other systems
- Gain insights into validation patterns with Validated

## Quick Start

### 1. Add Dependencies

The Actuator integration is included in `hkj-spring-boot-starter`:

```xml
<dependency>
    <groupId>org.higherkindedj</groupId>
    <artifactId>hkj-spring-boot-starter</artifactId>
    <version>${hkj.version}</version>
</dependency>

<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. Enable Actuator Features

```yaml
# application.yml
hkj:
  actuator:
    metrics:
      enabled: true          # Enable HKJ metrics (default: true)
    health:
      async-executor:
        enabled: true        # Enable async health indicator (default: true)

# Expose actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,hkj
```

### 3. Access Metrics

Start your application and access:

- **HKJ Endpoint**: `http://localhost:8080/actuator/hkj`
- **Health**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`

## Components

### HkjMetricsService

The `HkjMetricsService` tracks metrics for all functional constructs using Micrometer.

#### Tracked Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `hkj.either.invocations` | Counter | Either invocations (tagged: success/error) |
| `hkj.either.errors` | Counter | Either errors (tagged: error_type) |
| `hkj.validated.invocations` | Counter | Validated invocations (tagged: valid/invalid) |
| `hkj.validated.error_count` | Summary | Number of errors in Invalid cases |
| `hkj.either_t.invocations` | Counter | EitherT invocations (tagged: success/error) |
| `hkj.either_t.errors` | Counter | EitherT errors (tagged: error_type) |
| `hkj.either_t.async.duration` | Timer | EitherT async operation duration |
| `hkj.either_t.exceptions` | Counter | EitherT exceptions (tagged: exception_type) |

#### Usage

Metrics are automatically recorded by the return value handlers. You can also use the service directly:

```java
@Service
public class UserService {
    private final HkjMetricsService metricsService;

    public Either<UserError, User> findUser(String id) {
        Either<UserError, User> result = // ... your logic

        // Metrics are auto-recorded by handlers, but you can also record manually:
        result.fold(
            error -> {
                metricsService.recordEitherError(error.getClass().getSimpleName());
                return null;
            },
            user -> {
                metricsService.recordEitherSuccess();
                return user;
            }
        );

        return result;
    }
}
```

### HkjMetricsEndpoint

A custom actuator endpoint providing a comprehensive snapshot of HKJ configuration and metrics.

#### Endpoint URL

```
GET /actuator/hkj
```

#### Response Structure

```json
{
  "configuration": {
    "web": {
      "eitherResponseEnabled": true,
      "validatedResponseEnabled": true,
      "asyncEitherTEnabled": true,
      "defaultErrorStatus": 400
    },
    "jackson": {
      "customSerializersEnabled": true,
      "eitherFormat": "TAGGED",
      "validatedFormat": "TAGGED",
      "maybeFormat": "TAGGED"
    }
  },
  "metrics": {
    "either": {
      "successCount": 150,
      "errorCount": 25,
      "totalCount": 175,
      "successRate": 0.857
    },
    "validated": {
      "validCount": 200,
      "invalidCount": 50,
      "totalCount": 250,
      "validRate": 0.800
    },
    "eitherT": {
      "successCount": 75,
      "errorCount": 10,
      "totalCount": 85,
      "successRate": 0.882
    }
  }
}
```

#### Using in Monitoring

```bash
# Check success rates
curl http://localhost:8080/actuator/hkj | jq '.metrics.either.successRate'

# Monitor configuration
curl http://localhost:8080/actuator/hkj | jq '.configuration.web'

# Track all metrics
watch -n 5 'curl -s http://localhost:8080/actuator/hkj | jq .metrics'
```

### HkjAsyncHealthIndicator

Monitors the health of the async thread pool executor used by EitherT operations.

#### Health Statuses

| Status | Condition |
|--------|-----------|
| `UP` | Thread pool is healthy, queue has capacity |
| `DOWN` | Thread pool shutdown or queue is full |
| `OUT_OF_SERVICE` | Async executor not configured |

#### Health Response

```json
{
  "status": "UP",
  "components": {
    "hkjAsync": {
      "status": "UP",
      "details": {
        "activeCount": 2,
        "poolSize": 10,
        "corePoolSize": 10,
        "maxPoolSize": 20,
        "queueSize": 5,
        "queueCapacity": 100,
        "queueRemainingCapacity": 95
      }
    }
  }
}
```

#### Details Explained

- **activeCount**: Currently executing tasks
- **poolSize**: Current number of threads in pool
- **corePoolSize**: Minimum number of threads to keep alive
- **maxPoolSize**: Maximum number of threads allowed
- **queueSize**: Number of tasks waiting in queue
- **queueCapacity**: Maximum queue size
- **queueRemainingCapacity**: Available queue slots

## Configuration

### Complete Configuration Options

```yaml
hkj:
  # Web configuration
  web:
    either-response-enabled: true
    validated-response-enabled: true
    async-either-t-enabled: true
    default-error-status: 400

  # Jackson serialization
  jackson:
    custom-serializers-enabled: true
    either-format: TAGGED
    validated-format: TAGGED
    maybe-format: TAGGED

  # Async executor for EitherT
  async:
    core-pool-size: 10
    max-pool-size: 20
    queue-capacity: 100
    thread-name-prefix: "hkj-async-"

  # Actuator integration
  actuator:
    metrics:
      enabled: true
    health:
      async-executor:
        enabled: true

# Spring Boot Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,hkj
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
```

### Conditional Configuration

Disable metrics if not needed:

```yaml
hkj:
  actuator:
    metrics:
      enabled: false  # Metrics service will not be created
```

Disable async health checks:

```yaml
hkj:
  actuator:
    health:
      async-executor:
        enabled: false  # Health indicator will not be registered
```

## Metrics Details

### Either Metrics

Track success and error rates for synchronous Either operations.

**Use Cases:**
- Monitor API error rates
- Track validation failures
- Identify error patterns

**Example Queries:**

```java
// Prometheus query for Either error rate
rate(hkj_either_invocations_total{result="error"}[5m])
  /
rate(hkj_either_invocations_total[5m])

// Track specific error types
sum by (error_type) (hkj_either_errors_total)
```

### Validated Metrics

Track validation success and accumulated error counts.

**Use Cases:**
- Monitor form validation success rates
- Track validation error distributions
- Identify common validation failures

**Example Queries:**

```java
// Prometheus query for validation success rate
rate(hkj_validated_invocations_total{result="valid"}[5m])
  /
rate(hkj_validated_invocations_total[5m])

// Average errors per invalid validation
rate(hkj_validated_error_count_sum[5m])
  /
rate(hkj_validated_error_count_count[5m])
```

### EitherT Metrics

Track async operations with success rates, durations, and exception handling.

**Use Cases:**
- Monitor async API performance
- Track async error rates
- Identify slow async operations
- Monitor exception patterns

**Example Queries:**

```java
// Prometheus query for EitherT latency (p95)
histogram_quantile(0.95,
  rate(hkj_either_t_async_duration_seconds_bucket[5m]))

// EitherT error rate
rate(hkj_either_t_invocations_total{result="error"}[5m])

// Exception types distribution
sum by (exception_type) (hkj_either_t_exceptions_total)
```

## Health Checks

### Async Executor Health

The async health indicator monitors thread pool saturation and queue fullness.

#### Healthy State

```json
{
  "status": "UP",
  "details": {
    "activeCount": 3,
    "poolSize": 10,
    "queueRemainingCapacity": 95
  }
}
```

**Indicates:** Thread pool operating normally with available capacity.

#### Warning Signs

```json
{
  "status": "DOWN",
  "details": {
    "activeCount": 20,
    "poolSize": 20,
    "queueSize": 100,
    "queueRemainingCapacity": 0
  }
}
```

**Indicates:** Queue is full, new async operations will be rejected.

**Action:** Increase pool size or queue capacity:

```yaml
hkj:
  async:
    max-pool-size: 40      # Increase max threads
    queue-capacity: 200    # Increase queue size
```

### Custom Health Checks

You can create custom health indicators for your functional code:

```java
@Component
public class UserServiceHealthIndicator implements HealthIndicator {

    private final UserService userService;
    private final HkjMetricsService metricsService;

    @Override
    public Health health() {
        double errorRate = metricsService.getEitherErrorCount()
            / (metricsService.getEitherSuccessCount() + metricsService.getEitherErrorCount());

        if (errorRate > 0.5) {
            return Health.down()
                .withDetail("errorRate", errorRate)
                .withDetail("message", "User service error rate too high")
                .build();
        }

        return Health.up()
            .withDetail("errorRate", errorRate)
            .build();
    }
}
```

## Integration Examples

### Prometheus Integration

#### 1. Add Prometheus Dependency

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 2. Configure Endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,hkj
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 3. Scrape Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

#### 4. Example Queries

```promql
# Either error rate over time
rate(hkj_either_invocations_total{result="error"}[5m])

# EitherT p95 latency
histogram_quantile(0.95,
  rate(hkj_either_t_async_duration_seconds_bucket[5m]))

# Validated success rate
sum(rate(hkj_validated_invocations_total{result="valid"}[5m]))
  /
sum(rate(hkj_validated_invocations_total[5m]))
```

### Grafana Dashboard

Example Grafana queries for HKJ metrics:

```json
{
  "panels": [
    {
      "title": "Either Success Rate",
      "targets": [
        {
          "expr": "rate(hkj_either_invocations_total{result=\"success\"}[5m]) / rate(hkj_either_invocations_total[5m])"
        }
      ]
    },
    {
      "title": "EitherT Async Duration (p95)",
      "targets": [
        {
          "expr": "histogram_quantile(0.95, rate(hkj_either_t_async_duration_seconds_bucket[5m]))"
        }
      ]
    },
    {
      "title": "Thread Pool Utilization",
      "targets": [
        {
          "expr": "hkj_async_active_count / hkj_async_pool_size"
        }
      ]
    }
  ]
}
```

### Alerting Rules

Set up alerts for critical metrics:

```yaml
# Prometheus alerting rules
groups:
  - name: hkj_alerts
    rules:
      # Alert when Either error rate exceeds 50%
      - alert: HighEitherErrorRate
        expr: |
          rate(hkj_either_invocations_total{result="error"}[5m])
            /
          rate(hkj_either_invocations_total[5m]) > 0.5
        for: 5m
        annotations:
          summary: "High Either error rate"
          description: "Either error rate is {{ $value }} (>50%)"

      # Alert when async queue is getting full
      - alert: AsyncQueueNearFull
        expr: hkj_async_queue_remaining_capacity < 10
        for: 2m
        annotations:
          summary: "Async queue near capacity"
          description: "Only {{ $value }} slots remaining"

      # Alert when EitherT operations are slow
      - alert: SlowEitherTOperations
        expr: |
          histogram_quantile(0.95,
            rate(hkj_either_t_async_duration_seconds_bucket[5m])) > 5
        for: 5m
        annotations:
          summary: "Slow EitherT operations"
          description: "p95 latency is {{ $value }}s (>5s)"
```

## Production Considerations

### Performance Impact

Metrics collection has minimal overhead:

- **Counter increment**: ~5-10 nanoseconds
- **Timer recording**: ~50-100 nanoseconds
- **Memory**: ~1KB per unique metric tag combination

### Tuning Thread Pool

Monitor these indicators to tune the async executor:

```yaml
# Conservative (low traffic)
hkj:
  async:
    core-pool-size: 5
    max-pool-size: 10
    queue-capacity: 50

# Moderate (medium traffic)
hkj:
  async:
    core-pool-size: 10
    max-pool-size: 20
    queue-capacity: 100

# Aggressive (high traffic)
hkj:
  async:
    core-pool-size: 20
    max-pool-size: 50
    queue-capacity: 500
```

**Monitoring Guidelines:**

- If `queueRemainingCapacity` often near 0: Increase `queue-capacity`
- If `activeCount` always equals `maxPoolSize`: Increase `max-pool-size`
- If `poolSize` rarely exceeds `corePoolSize`: Decrease `core-pool-size`

### Metrics Retention

Configure Micrometer to limit metric cardinality:

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter meterFilter() {
        return MeterFilter.maximumAllowableTags("hkj.either.errors",
            "error_type", 100, MeterFilter.deny());
    }
}
```

### Security

Restrict actuator endpoints in production:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info  # Don't expose metrics in production

# Use Spring Security to protect endpoints
spring:
  security:
    user:
      name: admin
      password: ${ACTUATOR_PASSWORD}
```

```java
@Configuration
public class ActuatorSecurityConfig {

    @Bean
    public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
        http
            .requestMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .anyRequest().hasRole("ACTUATOR_ADMIN"));

        return http.build();
    }
}
```

## Testing

### Testing Metrics Collection

```java
@SpringBootTest
@AutoConfigureMetrics
class MetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private UserController controller;

    @Test
    void shouldRecordEitherMetrics() {
        // Act
        controller.getUser("1");

        // Assert
        Counter successCounter = meterRegistry.counter("hkj.either.invocations",
            "result", "success");
        assertThat(successCounter.count()).isEqualTo(1.0);
    }
}
```

### Testing Health Indicators

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthCheckIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReportAsyncHealthAsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> health = response.getBody();
        assertThat(health.get("status")).isEqualTo("UP");

        Map<String, Object> components = (Map) health.get("components");
        Map<String, Object> hkjAsync = (Map) components.get("hkjAsync");
        assertThat(hkjAsync.get("status")).isEqualTo("UP");
    }
}
```

### Testing Custom Endpoint

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HkjEndpointIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldExposeHkjMetrics() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/actuator/hkj", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("configuration", "metrics");

        Map<String, Object> metrics = (Map) body.get("metrics");
        assertThat(metrics).containsKeys("either", "validated", "eitherT");
    }
}
```

## Troubleshooting

### Metrics Not Appearing

**Problem:** `/actuator/metrics` doesn't show HKJ metrics

**Solutions:**
1. Check metrics are enabled:
   ```yaml
   hkj:
     actuator:
       metrics:
         enabled: true
   ```

2. Verify HkjMetricsService bean exists:
   ```java
   @Autowired(required = false)
   private HkjMetricsService metricsService;

   assertThat(metricsService).isNotNull();
   ```

3. Ensure return value handlers are processing:
   ```java
   // Check handler is registered
   @Autowired
   private RequestMappingHandlerAdapter adapter;

   boolean hasEitherHandler = adapter.getReturnValueHandlers().stream()
       .anyMatch(h -> h instanceof EitherReturnValueHandler);
   ```

### Health Check Always DOWN

**Problem:** `hkjAsync` always shows DOWN status

**Solutions:**
1. Check executor is initialized:
   ```yaml
   hkj:
     async:
       core-pool-size: 10  # Must be > 0
   ```

2. Verify executor bean:
   ```java
   @Autowired(required = false)
   @Qualifier("hkjAsyncExecutor")
   private ThreadPoolTaskExecutor executor;

   assertThat(executor).isNotNull();
   assertThat(executor.getThreadPoolExecutor()).isNotNull();
   ```

### HKJ Endpoint Returns 404

**Problem:** `/actuator/hkj` returns 404 Not Found

**Solutions:**
1. Expose the endpoint:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: hkj  # Add hkj to exposed endpoints
   ```

2. Check endpoint is registered:
   ```bash
   curl http://localhost:8080/actuator | jq '.._links.hkj'
   ```

## Summary

The higher-kinded-j Actuator integration provides:

✅ **Comprehensive metrics** for Either, Validated, and EitherT
✅ **Health monitoring** for async thread pools
✅ **Custom endpoint** for configuration and metrics snapshots
✅ **Micrometer integration** for export to monitoring systems
✅ **Production-ready** with security and performance considerations
✅ **Testing support** with comprehensive test examples

For more information:
- [Main Documentation](README.md)
- [Web Integration](WEB.md)
- [Security Integration](SECURITY.md)
- [Spring Boot Actuator Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Docs](https://micrometer.io/docs)
