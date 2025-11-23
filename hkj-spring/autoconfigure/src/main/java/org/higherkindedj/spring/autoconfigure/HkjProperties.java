// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for higher-kinded-j Spring Boot integration.
 *
 * <p>Configure via application.yml/properties with the prefix "hkj":
 *
 * <pre>
 * hkj:
 *   web:
 *     either-response-enabled: true
 *     default-error-status: 400
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

  /** Web/MVC configuration properties. */
  public static class Web {
    /**
     * Enable automatic Either to ResponseEntity conversion via return value handler. Default: true
     */
    private boolean eitherResponseEnabled = true;

    /**
     * Enable automatic Validated to ResponseEntity conversion via return value handler. Default:
     * true
     */
    private boolean validatedResponseEnabled = true;

    /**
     * Default HTTP status code for Left values when error type is unknown. Default: 400 (Bad
     * Request)
     */
    private int defaultErrorStatus = 400;

    /** Enable EitherT async support (for future implementation). Default: true */
    private boolean asyncEitherTEnabled = true;

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

    public boolean isEitherResponseEnabled() {
      return eitherResponseEnabled;
    }

    public void setEitherResponseEnabled(boolean eitherResponseEnabled) {
      this.eitherResponseEnabled = eitherResponseEnabled;
    }

    public boolean isValidatedResponseEnabled() {
      return validatedResponseEnabled;
    }

    public void setValidatedResponseEnabled(boolean validatedResponseEnabled) {
      this.validatedResponseEnabled = validatedResponseEnabled;
    }

    public int getDefaultErrorStatus() {
      return defaultErrorStatus;
    }

    public void setDefaultErrorStatus(int defaultErrorStatus) {
      this.defaultErrorStatus = defaultErrorStatus;
    }

    public boolean isAsyncEitherTEnabled() {
      return asyncEitherTEnabled;
    }

    public void setAsyncEitherTEnabled(boolean asyncEitherTEnabled) {
      this.asyncEitherTEnabled = asyncEitherTEnabled;
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
