// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for higher-kinded-j Spring Boot 4.0.1+ integration.
 *
 * <p>This version uses the Effect Path API exclusively. For Spring Boot 3.5.7, use hkj-spring
 * version 0.2.7.
 *
 * <p>Configure via application.yml/properties with the prefix "hkj":
 *
 * <pre>
 * hkj:
 *   web:
 *     either-path-enabled: true
 *     maybe-path-enabled: true
 *     try-path-enabled: true
 *     validation-path-enabled: true
 *     io-path-enabled: true
 *     completable-future-path-enabled: true
 *     default-error-status: 400
 *     maybe-nothing-status: 404
 *     try-include-exception-details: false
 *   validation:
 *     accumulate-errors: true
 *   json:
 *     custom-serializers-enabled: true
 *     either-format: TAGGED
 * </pre>
 */
@ConfigurationProperties(prefix = "hkj")
public class HkjProperties {

  private Web web = new Web();
  private Validation validation = new Validation();
  private Jackson json = new Jackson();
  private Async async = new Async();
  private Actuator actuator = new Actuator();
  private Security security = new Security();

  public Web getWeb() {
    return web;
  }

  public void setWeb(Web web) {
    this.web = web;
  }

  public Validation getValidation() {
    return validation;
  }

  public void setValidation(Validation validation) {
    this.validation = validation;
  }

  public Jackson getJson() {
    return json;
  }

  public void setJson(Jackson json) {
    this.json = json;
  }

  public Async getAsync() {
    return async;
  }

  public void setAsync(Async async) {
    this.async = async;
  }

  public Actuator getActuator() {
    return actuator;
  }

  public void setActuator(Actuator actuator) {
    this.actuator = actuator;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  /**
   * Web/MVC configuration properties for Effect Path API handlers.
   *
   * <p>Spring Boot 4.0.1+ uses Effect Path API handlers exclusively. For Spring Boot 3.5.7, use
   * hkj-spring version 0.2.7.
   */
  public static class Web {
    /** Enable EitherPath return value handler. Default: true */
    private boolean eitherPathEnabled = true;

    /** Enable MaybePath return value handler. Default: true */
    private boolean maybePathEnabled = true;

    /** Enable TryPath return value handler. Default: true */
    private boolean tryPathEnabled = true;

    /** Enable ValidationPath return value handler. Default: true */
    private boolean validationPathEnabled = true;

    /** Enable IOPath return value handler. Default: true */
    private boolean ioPathEnabled = true;

    /** Enable CompletableFuturePath return value handler. Default: true */
    private boolean completableFuturePathEnabled = true;

    /**
     * Default HTTP status code for Left values when error type is unknown. Default: 400 (Bad
     * Request)
     */
    private int defaultErrorStatus = 400;

    /** HTTP status code for MaybePath Nothing values. Default: 404 (Not Found) */
    private int maybeNothingStatus = 404;

    /** HTTP status code for TryPath Failure values. Default: 500 (Internal Server Error) */
    private int tryFailureStatus = 500;

    /** HTTP status code for ValidationPath Invalid values. Default: 400 (Bad Request) */
    private int validationInvalidStatus = 400;

    /** HTTP status code for IOPath execution failures. Default: 500 (Internal Server Error) */
    private int ioFailureStatus = 500;

    /** HTTP status code for CompletableFuturePath failures. Default: 500 (Internal Server Error) */
    private int asyncFailureStatus = 500;

    /** Include exception details in TryPath failure responses. Default: false (production safe) */
    private boolean tryIncludeExceptionDetails = false;

    /** Include exception details in IOPath failure responses. Default: false (production safe) */
    private boolean ioIncludeExceptionDetails = false;

    /**
     * Include exception details in CompletableFuturePath failure responses. Default: false
     * (production safe)
     */
    private boolean asyncIncludeExceptionDetails = false;

    /**
     * Custom error status code mappings.
     *
     * <p>Maps error class names (simple name) to HTTP status codes. For example:
     * UserNotFoundError=404, ValidationError=400
     *
     * <p>Example in YAML:
     *
     * <pre>
     * hkj:
     *   web:
     *     error-status-mappings:
     *       UserNotFoundError: 404
     *       ValidationError: 400
     *       AuthorizationError: 403
     * </pre>
     */
    private Map<String, Integer> errorStatusMappings = new HashMap<>();

    public boolean isEitherPathEnabled() {
      return eitherPathEnabled;
    }

    public void setEitherPathEnabled(boolean eitherPathEnabled) {
      this.eitherPathEnabled = eitherPathEnabled;
    }

    public boolean isMaybePathEnabled() {
      return maybePathEnabled;
    }

    public void setMaybePathEnabled(boolean maybePathEnabled) {
      this.maybePathEnabled = maybePathEnabled;
    }

    public boolean isTryPathEnabled() {
      return tryPathEnabled;
    }

    public void setTryPathEnabled(boolean tryPathEnabled) {
      this.tryPathEnabled = tryPathEnabled;
    }

    public boolean isValidationPathEnabled() {
      return validationPathEnabled;
    }

    public void setValidationPathEnabled(boolean validationPathEnabled) {
      this.validationPathEnabled = validationPathEnabled;
    }

    public boolean isIoPathEnabled() {
      return ioPathEnabled;
    }

    public void setIoPathEnabled(boolean ioPathEnabled) {
      this.ioPathEnabled = ioPathEnabled;
    }

    public boolean isCompletableFuturePathEnabled() {
      return completableFuturePathEnabled;
    }

    public void setCompletableFuturePathEnabled(boolean completableFuturePathEnabled) {
      this.completableFuturePathEnabled = completableFuturePathEnabled;
    }

    public int getDefaultErrorStatus() {
      return defaultErrorStatus;
    }

    public void setDefaultErrorStatus(int defaultErrorStatus) {
      this.defaultErrorStatus = defaultErrorStatus;
    }

    public int getMaybeNothingStatus() {
      return maybeNothingStatus;
    }

    public void setMaybeNothingStatus(int maybeNothingStatus) {
      this.maybeNothingStatus = maybeNothingStatus;
    }

    public int getTryFailureStatus() {
      return tryFailureStatus;
    }

    public void setTryFailureStatus(int tryFailureStatus) {
      this.tryFailureStatus = tryFailureStatus;
    }

    public int getValidationInvalidStatus() {
      return validationInvalidStatus;
    }

    public void setValidationInvalidStatus(int validationInvalidStatus) {
      this.validationInvalidStatus = validationInvalidStatus;
    }

    public int getIoFailureStatus() {
      return ioFailureStatus;
    }

    public void setIoFailureStatus(int ioFailureStatus) {
      this.ioFailureStatus = ioFailureStatus;
    }

    public int getAsyncFailureStatus() {
      return asyncFailureStatus;
    }

    public void setAsyncFailureStatus(int asyncFailureStatus) {
      this.asyncFailureStatus = asyncFailureStatus;
    }

    public boolean isTryIncludeExceptionDetails() {
      return tryIncludeExceptionDetails;
    }

    public void setTryIncludeExceptionDetails(boolean tryIncludeExceptionDetails) {
      this.tryIncludeExceptionDetails = tryIncludeExceptionDetails;
    }

    public boolean isIoIncludeExceptionDetails() {
      return ioIncludeExceptionDetails;
    }

    public void setIoIncludeExceptionDetails(boolean ioIncludeExceptionDetails) {
      this.ioIncludeExceptionDetails = ioIncludeExceptionDetails;
    }

    public boolean isAsyncIncludeExceptionDetails() {
      return asyncIncludeExceptionDetails;
    }

    public void setAsyncIncludeExceptionDetails(boolean asyncIncludeExceptionDetails) {
      this.asyncIncludeExceptionDetails = asyncIncludeExceptionDetails;
    }

    public Map<String, Integer> getErrorStatusMappings() {
      return errorStatusMappings;
    }

    public void setErrorStatusMappings(Map<String, Integer> errorStatusMappings) {
      this.errorStatusMappings = errorStatusMappings;
    }
  }

  /** Validation configuration properties. */
  public static class Validation {
    /** Enable Validated-based validation support. Default: true */
    private boolean enabled = true;

    /**
     * Accumulate all validation errors (true) or fail-fast on first error (false). Default: true
     */
    private boolean accumulateErrors = true;

    /** Maximum number of errors to accumulate (0 = unlimited). Default: 0 (unlimited) */
    private int maxErrors = 0;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isAccumulateErrors() {
      return accumulateErrors;
    }

    public void setAccumulateErrors(boolean accumulateErrors) {
      this.accumulateErrors = accumulateErrors;
    }

    public int getMaxErrors() {
      return maxErrors;
    }

    public void setMaxErrors(int maxErrors) {
      this.maxErrors = maxErrors;
    }
  }

  /** Jackson JSON serialization configuration properties. */
  public static class Jackson {
    /** Enable custom Jackson serializers for Either, Validated types. Default: true */
    private boolean customSerializersEnabled = true;

    /**
     * Format for Either JSON output.
     *
     * <ul>
     *   <li>TAGGED: {"isRight": true/false, "right/left": ...} (default)
     *   <li>SIMPLE: {"value": ...} or {"error": ...}
     * </ul>
     *
     * Default: TAGGED
     */
    private SerializationFormat eitherFormat = SerializationFormat.TAGGED;

    /**
     * Format for Validated JSON output.
     *
     * <ul>
     *   <li>TAGGED: {"valid": true/false, "value/errors": ...} (default)
     *   <li>SIMPLE: {"value": ...} or {"errors": ...}
     * </ul>
     *
     * Default: TAGGED
     */
    private SerializationFormat validatedFormat = SerializationFormat.TAGGED;

    /**
     * Format for Maybe JSON output.
     *
     * <ul>
     *   <li>TAGGED: {"present": true/false, "value": ...} (default)
     *   <li>SIMPLE: {"value": ...} or null
     * </ul>
     *
     * Default: TAGGED
     */
    private SerializationFormat maybeFormat = SerializationFormat.TAGGED;

    public boolean isCustomSerializersEnabled() {
      return customSerializersEnabled;
    }

    public void setCustomSerializersEnabled(boolean customSerializersEnabled) {
      this.customSerializersEnabled = customSerializersEnabled;
    }

    public SerializationFormat getEitherFormat() {
      return eitherFormat;
    }

    public void setEitherFormat(SerializationFormat eitherFormat) {
      this.eitherFormat = eitherFormat;
    }

    public SerializationFormat getValidatedFormat() {
      return validatedFormat;
    }

    public void setValidatedFormat(SerializationFormat validatedFormat) {
      this.validatedFormat = validatedFormat;
    }

    public SerializationFormat getMaybeFormat() {
      return maybeFormat;
    }

    public void setMaybeFormat(SerializationFormat maybeFormat) {
      this.maybeFormat = maybeFormat;
    }

    /** JSON serialization format for HKJ types. */
    public enum SerializationFormat {
      /** Simple format: minimal wrapping, direct value or error */
      SIMPLE,

      /** Tagged format: includes discriminator tags and both branches */
      TAGGED
    }
  }

  /** Async configuration properties (for future EitherT support). */
  public static class Async {
    /** Core thread pool size for async IO execution. Default: 10 */
    private int executorCorePoolSize = 10;

    /** Maximum thread pool size for async IO execution. Default: 20 */
    private int executorMaxPoolSize = 20;

    /** Queue capacity for async IO executor. Default: 100 */
    private int executorQueueCapacity = 100;

    /** Thread name prefix for async IO executor. Default: "hkj-async-" */
    private String executorThreadNamePrefix = "hkj-async-";

    /** Default timeout for async operations (milliseconds). Default: 30000 (30 seconds) */
    private long defaultTimeoutMs = 30000;

    public int getExecutorCorePoolSize() {
      return executorCorePoolSize;
    }

    public void setExecutorCorePoolSize(int executorCorePoolSize) {
      this.executorCorePoolSize = executorCorePoolSize;
    }

    public int getExecutorMaxPoolSize() {
      return executorMaxPoolSize;
    }

    public void setExecutorMaxPoolSize(int executorMaxPoolSize) {
      this.executorMaxPoolSize = executorMaxPoolSize;
    }

    public int getExecutorQueueCapacity() {
      return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
      this.executorQueueCapacity = executorQueueCapacity;
    }

    public String getExecutorThreadNamePrefix() {
      return executorThreadNamePrefix;
    }

    public void setExecutorThreadNamePrefix(String executorThreadNamePrefix) {
      this.executorThreadNamePrefix = executorThreadNamePrefix;
    }

    public long getDefaultTimeoutMs() {
      return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
      this.defaultTimeoutMs = defaultTimeoutMs;
    }
  }

  /** Actuator configuration properties. */
  public static class Actuator {
    /** Enable Micrometer metrics for HKJ handlers. Default: true */
    private boolean metricsEnabled = true;

    public boolean isMetricsEnabled() {
      return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
      this.metricsEnabled = metricsEnabled;
    }
  }

  /** Security configuration properties. */
  public static class Security {
    /** Enable HKJ Spring Security integration. Default: false (opt-in) */
    private boolean enabled = false;

    /** Enable ValidatedUserDetailsService for error accumulation. Default: true */
    private boolean validatedUserDetails = true;

    /** Enable EitherAuthenticationConverter for JWT processing. Default: true */
    private boolean eitherAuthentication = true;

    /** Enable EitherAuthorizationManager for functional authorization. Default: true */
    private boolean eitherAuthorization = true;

    /** JWT claim name containing user authorities/roles. Default: "roles" */
    private String jwtAuthoritiesClaim = "roles";

    /** Prefix to add to JWT authorities (e.g., "ROLE_"). Default: "ROLE_" */
    private String jwtAuthorityPrefix = "ROLE_";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isValidatedUserDetails() {
      return validatedUserDetails;
    }

    public void setValidatedUserDetails(boolean validatedUserDetails) {
      this.validatedUserDetails = validatedUserDetails;
    }

    public boolean isEitherAuthentication() {
      return eitherAuthentication;
    }

    public void setEitherAuthentication(boolean eitherAuthentication) {
      this.eitherAuthentication = eitherAuthentication;
    }

    public boolean isEitherAuthorization() {
      return eitherAuthorization;
    }

    public void setEitherAuthorization(boolean eitherAuthorization) {
      this.eitherAuthorization = eitherAuthorization;
    }

    public String getJwtAuthoritiesClaim() {
      return jwtAuthoritiesClaim;
    }

    public void setJwtAuthoritiesClaim(String jwtAuthoritiesClaim) {
      this.jwtAuthoritiesClaim = jwtAuthoritiesClaim;
    }

    public String getJwtAuthorityPrefix() {
      return jwtAuthorityPrefix;
    }

    public void setJwtAuthorityPrefix(String jwtAuthorityPrefix) {
      this.jwtAuthorityPrefix = jwtAuthorityPrefix;
    }
  }
}
