// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for higher-kinded-j Spring Boot 4.0.3+ integration.
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
  private VirtualThreads virtualThreads = new VirtualThreads();
  private EffectConfig effectBoundary = new EffectConfig();

  /** Creates a new HkjProperties with default values. */
  public HkjProperties() {}

  /**
   * Returns the web configuration.
   *
   * @return the web configuration
   */
  public Web getWeb() {
    return web;
  }

  /**
   * Sets the web configuration.
   *
   * @param web the web configuration
   */
  public void setWeb(Web web) {
    this.web = web;
  }

  /**
   * Returns the validation configuration.
   *
   * @return the validation configuration
   */
  public Validation getValidation() {
    return validation;
  }

  /**
   * Sets the validation configuration.
   *
   * @param validation the validation configuration
   */
  public void setValidation(Validation validation) {
    this.validation = validation;
  }

  /**
   * Returns the JSON configuration.
   *
   * @return the JSON configuration
   */
  public Jackson getJson() {
    return json;
  }

  /**
   * Sets the JSON configuration.
   *
   * @param json the JSON configuration
   */
  public void setJson(Jackson json) {
    this.json = json;
  }

  /**
   * Returns the async configuration.
   *
   * @return the async configuration
   */
  public Async getAsync() {
    return async;
  }

  /**
   * Sets the async configuration.
   *
   * @param async the async configuration
   */
  public void setAsync(Async async) {
    this.async = async;
  }

  /**
   * Returns the actuator configuration.
   *
   * @return the actuator configuration
   */
  public Actuator getActuator() {
    return actuator;
  }

  /**
   * Sets the actuator configuration.
   *
   * @param actuator the actuator configuration
   */
  public void setActuator(Actuator actuator) {
    this.actuator = actuator;
  }

  /**
   * Returns the security configuration.
   *
   * @return the security configuration
   */
  public Security getSecurity() {
    return security;
  }

  /**
   * Sets the security configuration.
   *
   * @param security the security configuration
   */
  public void setSecurity(Security security) {
    this.security = security;
  }

  /**
   * Returns the virtual threads configuration.
   *
   * @return the virtual threads configuration
   */
  public VirtualThreads getVirtualThreads() {
    return virtualThreads;
  }

  /**
   * Sets the virtual threads configuration.
   *
   * @param virtualThreads the virtual threads configuration
   */
  public void setVirtualThreads(VirtualThreads virtualThreads) {
    this.virtualThreads = virtualThreads;
  }

  /**
   * Returns the effect boundary configuration.
   *
   * @return the effect boundary configuration
   */
  public EffectConfig getEffectBoundary() {
    return effectBoundary;
  }

  /**
   * Sets the effect boundary configuration.
   *
   * @param effectBoundary the effect boundary configuration
   */
  public void setEffectBoundary(EffectConfig effectBoundary) {
    this.effectBoundary = effectBoundary;
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

    /** Enable VTaskPath return value handler (virtual threads). Default: true */
    private boolean vtaskPathEnabled = true;

    /** Enable VStreamPath return value handler (SSE streaming on virtual threads). Default: true */
    private boolean vstreamPathEnabled = true;

    /** Enable FreePath return value handler (effect boundary interpretation). Default: true */
    private boolean freePathEnabled = true;

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

    /** HTTP status code for VTaskPath failures. Default: 500 (Internal Server Error) */
    private int vtaskFailureStatus = 500;

    /** HTTP status code for VStreamPath failures. Default: 500 (Internal Server Error) */
    private int vstreamFailureStatus = 500;

    /**
     * HTTP status code for FreePath interpretation failures. Default: 500 (Internal Server Error)
     */
    private int freePathFailureStatus = 500;

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
     * Include exception details in VTaskPath failure responses. Default: false (production safe)
     */
    private boolean vtaskIncludeExceptionDetails = false;

    /** Include exception details in VStreamPath error events. Default: false (production safe) */
    private boolean vstreamIncludeExceptionDetails = false;

    /** Include exception details in FreePath failure responses. Default: false (production safe) */
    private boolean freePathIncludeExceptionDetails = false;

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

    /** Creates a new Web configuration with default values. */
    public Web() {}

    /**
     * Returns whether EitherPath handler is enabled.
     *
     * @return whether EitherPath handler is enabled
     */
    public boolean isEitherPathEnabled() {
      return eitherPathEnabled;
    }

    /**
     * Sets whether EitherPath handler is enabled.
     *
     * @param eitherPathEnabled whether EitherPath handler is enabled
     */
    public void setEitherPathEnabled(boolean eitherPathEnabled) {
      this.eitherPathEnabled = eitherPathEnabled;
    }

    /**
     * Returns whether MaybePath handler is enabled.
     *
     * @return whether MaybePath handler is enabled
     */
    public boolean isMaybePathEnabled() {
      return maybePathEnabled;
    }

    /**
     * Sets whether MaybePath handler is enabled.
     *
     * @param maybePathEnabled whether MaybePath handler is enabled
     */
    public void setMaybePathEnabled(boolean maybePathEnabled) {
      this.maybePathEnabled = maybePathEnabled;
    }

    /**
     * Returns whether TryPath handler is enabled.
     *
     * @return whether TryPath handler is enabled
     */
    public boolean isTryPathEnabled() {
      return tryPathEnabled;
    }

    /**
     * Sets whether TryPath handler is enabled.
     *
     * @param tryPathEnabled whether TryPath handler is enabled
     */
    public void setTryPathEnabled(boolean tryPathEnabled) {
      this.tryPathEnabled = tryPathEnabled;
    }

    /**
     * Returns whether ValidationPath handler is enabled.
     *
     * @return whether ValidationPath handler is enabled
     */
    public boolean isValidationPathEnabled() {
      return validationPathEnabled;
    }

    /**
     * Sets whether ValidationPath handler is enabled.
     *
     * @param validationPathEnabled whether ValidationPath handler is enabled
     */
    public void setValidationPathEnabled(boolean validationPathEnabled) {
      this.validationPathEnabled = validationPathEnabled;
    }

    /**
     * Returns whether IOPath handler is enabled.
     *
     * @return whether IOPath handler is enabled
     */
    public boolean isIoPathEnabled() {
      return ioPathEnabled;
    }

    /**
     * Sets whether IOPath handler is enabled.
     *
     * @param ioPathEnabled whether IOPath handler is enabled
     */
    public void setIoPathEnabled(boolean ioPathEnabled) {
      this.ioPathEnabled = ioPathEnabled;
    }

    /**
     * Returns whether CompletableFuturePath handler is enabled.
     *
     * @return whether CompletableFuturePath handler is enabled
     */
    public boolean isCompletableFuturePathEnabled() {
      return completableFuturePathEnabled;
    }

    /**
     * Sets whether CompletableFuturePath handler is enabled.
     *
     * @param completableFuturePathEnabled whether CompletableFuturePath handler is enabled
     */
    public void setCompletableFuturePathEnabled(boolean completableFuturePathEnabled) {
      this.completableFuturePathEnabled = completableFuturePathEnabled;
    }

    /**
     * Returns the default error HTTP status code.
     *
     * @return the default error HTTP status code
     */
    public int getDefaultErrorStatus() {
      return defaultErrorStatus;
    }

    /**
     * Sets the default error HTTP status code.
     *
     * @param defaultErrorStatus the default error HTTP status code
     */
    public void setDefaultErrorStatus(int defaultErrorStatus) {
      this.defaultErrorStatus = defaultErrorStatus;
    }

    /**
     * Returns the HTTP status code for MaybePath Nothing values.
     *
     * @return the HTTP status code for MaybePath Nothing values
     */
    public int getMaybeNothingStatus() {
      return maybeNothingStatus;
    }

    /**
     * Sets the HTTP status code for MaybePath Nothing values.
     *
     * @param maybeNothingStatus the HTTP status code for MaybePath Nothing values
     */
    public void setMaybeNothingStatus(int maybeNothingStatus) {
      this.maybeNothingStatus = maybeNothingStatus;
    }

    /**
     * Returns the HTTP status code for TryPath Failure values.
     *
     * @return the HTTP status code for TryPath Failure values
     */
    public int getTryFailureStatus() {
      return tryFailureStatus;
    }

    /**
     * Sets the HTTP status code for TryPath Failure values.
     *
     * @param tryFailureStatus the HTTP status code for TryPath Failure values
     */
    public void setTryFailureStatus(int tryFailureStatus) {
      this.tryFailureStatus = tryFailureStatus;
    }

    /**
     * Returns the HTTP status code for ValidationPath Invalid values.
     *
     * @return the HTTP status code for ValidationPath Invalid values
     */
    public int getValidationInvalidStatus() {
      return validationInvalidStatus;
    }

    /**
     * Sets the HTTP status code for ValidationPath Invalid values.
     *
     * @param validationInvalidStatus the HTTP status code for ValidationPath Invalid values
     */
    public void setValidationInvalidStatus(int validationInvalidStatus) {
      this.validationInvalidStatus = validationInvalidStatus;
    }

    /**
     * Returns the HTTP status code for IOPath failures.
     *
     * @return the HTTP status code for IOPath failures
     */
    public int getIoFailureStatus() {
      return ioFailureStatus;
    }

    /**
     * Sets the HTTP status code for IOPath failures.
     *
     * @param ioFailureStatus the HTTP status code for IOPath failures
     */
    public void setIoFailureStatus(int ioFailureStatus) {
      this.ioFailureStatus = ioFailureStatus;
    }

    /**
     * Returns the HTTP status code for async failures.
     *
     * @return the HTTP status code for async failures
     */
    public int getAsyncFailureStatus() {
      return asyncFailureStatus;
    }

    /**
     * Sets the HTTP status code for async failures.
     *
     * @param asyncFailureStatus the HTTP status code for async failures
     */
    public void setAsyncFailureStatus(int asyncFailureStatus) {
      this.asyncFailureStatus = asyncFailureStatus;
    }

    /**
     * Returns whether TryPath responses include exception details.
     *
     * @return whether TryPath responses include exception details
     */
    public boolean isTryIncludeExceptionDetails() {
      return tryIncludeExceptionDetails;
    }

    /**
     * Sets whether TryPath responses include exception details.
     *
     * @param tryIncludeExceptionDetails whether TryPath responses include exception details
     */
    public void setTryIncludeExceptionDetails(boolean tryIncludeExceptionDetails) {
      this.tryIncludeExceptionDetails = tryIncludeExceptionDetails;
    }

    /**
     * Returns whether IOPath responses include exception details.
     *
     * @return whether IOPath responses include exception details
     */
    public boolean isIoIncludeExceptionDetails() {
      return ioIncludeExceptionDetails;
    }

    /**
     * Sets whether IOPath responses include exception details.
     *
     * @param ioIncludeExceptionDetails whether IOPath responses include exception details
     */
    public void setIoIncludeExceptionDetails(boolean ioIncludeExceptionDetails) {
      this.ioIncludeExceptionDetails = ioIncludeExceptionDetails;
    }

    /**
     * Returns whether async responses include exception details.
     *
     * @return whether async responses include exception details
     */
    public boolean isAsyncIncludeExceptionDetails() {
      return asyncIncludeExceptionDetails;
    }

    /**
     * Sets whether async responses include exception details.
     *
     * @param asyncIncludeExceptionDetails whether async responses include exception details
     */
    public void setAsyncIncludeExceptionDetails(boolean asyncIncludeExceptionDetails) {
      this.asyncIncludeExceptionDetails = asyncIncludeExceptionDetails;
    }

    /**
     * Returns whether VTaskPath handler is enabled.
     *
     * @return whether VTaskPath handler is enabled
     */
    public boolean isVtaskPathEnabled() {
      return vtaskPathEnabled;
    }

    /**
     * Sets whether VTaskPath handler is enabled.
     *
     * @param vtaskPathEnabled whether VTaskPath handler is enabled
     */
    public void setVtaskPathEnabled(boolean vtaskPathEnabled) {
      this.vtaskPathEnabled = vtaskPathEnabled;
    }

    /**
     * Returns whether VStreamPath handler is enabled.
     *
     * @return whether VStreamPath handler is enabled
     */
    public boolean isVstreamPathEnabled() {
      return vstreamPathEnabled;
    }

    /**
     * Sets whether VStreamPath handler is enabled.
     *
     * @param vstreamPathEnabled whether VStreamPath handler is enabled
     */
    public void setVstreamPathEnabled(boolean vstreamPathEnabled) {
      this.vstreamPathEnabled = vstreamPathEnabled;
    }

    /**
     * Returns the HTTP status code for VTaskPath failures.
     *
     * @return the HTTP status code for VTaskPath failures
     */
    public int getVtaskFailureStatus() {
      return vtaskFailureStatus;
    }

    /**
     * Sets the HTTP status code for VTaskPath failures.
     *
     * @param vtaskFailureStatus the HTTP status code for VTaskPath failures
     */
    public void setVtaskFailureStatus(int vtaskFailureStatus) {
      this.vtaskFailureStatus = vtaskFailureStatus;
    }

    /**
     * Returns the HTTP status code for VStreamPath failures.
     *
     * @return the HTTP status code for VStreamPath failures
     */
    public int getVstreamFailureStatus() {
      return vstreamFailureStatus;
    }

    /**
     * Sets the HTTP status code for VStreamPath failures.
     *
     * @param vstreamFailureStatus the HTTP status code for VStreamPath failures
     */
    public void setVstreamFailureStatus(int vstreamFailureStatus) {
      this.vstreamFailureStatus = vstreamFailureStatus;
    }

    /**
     * Returns whether VTaskPath responses include exception details.
     *
     * @return whether VTaskPath responses include exception details
     */
    public boolean isVtaskIncludeExceptionDetails() {
      return vtaskIncludeExceptionDetails;
    }

    /**
     * Sets whether VTaskPath responses include exception details.
     *
     * @param vtaskIncludeExceptionDetails whether VTaskPath responses include exception details
     */
    public void setVtaskIncludeExceptionDetails(boolean vtaskIncludeExceptionDetails) {
      this.vtaskIncludeExceptionDetails = vtaskIncludeExceptionDetails;
    }

    /**
     * Returns whether VStreamPath responses include exception details.
     *
     * @return whether VStreamPath responses include exception details
     */
    public boolean isVstreamIncludeExceptionDetails() {
      return vstreamIncludeExceptionDetails;
    }

    /**
     * Sets whether VStreamPath responses include exception details.
     *
     * @param vstreamIncludeExceptionDetails whether VStreamPath responses include exception details
     */
    public void setVstreamIncludeExceptionDetails(boolean vstreamIncludeExceptionDetails) {
      this.vstreamIncludeExceptionDetails = vstreamIncludeExceptionDetails;
    }

    /**
     * Returns whether FreePath handler is enabled.
     *
     * @return whether FreePath handler is enabled
     */
    public boolean isFreePathEnabled() {
      return freePathEnabled;
    }

    /**
     * Sets whether FreePath handler is enabled.
     *
     * @param freePathEnabled whether FreePath handler is enabled
     */
    public void setFreePathEnabled(boolean freePathEnabled) {
      this.freePathEnabled = freePathEnabled;
    }

    /**
     * Returns the HTTP status code for FreePath failures.
     *
     * @return the HTTP status code for FreePath failures
     */
    public int getFreePathFailureStatus() {
      return freePathFailureStatus;
    }

    /**
     * Sets the HTTP status code for FreePath failures.
     *
     * @param freePathFailureStatus the HTTP status code for FreePath failures
     */
    public void setFreePathFailureStatus(int freePathFailureStatus) {
      this.freePathFailureStatus = freePathFailureStatus;
    }

    /**
     * Returns whether FreePath responses include exception details.
     *
     * @return whether FreePath responses include exception details
     */
    public boolean isFreePathIncludeExceptionDetails() {
      return freePathIncludeExceptionDetails;
    }

    /**
     * Sets whether FreePath responses include exception details.
     *
     * @param freePathIncludeExceptionDetails whether FreePath responses include exception details
     */
    public void setFreePathIncludeExceptionDetails(boolean freePathIncludeExceptionDetails) {
      this.freePathIncludeExceptionDetails = freePathIncludeExceptionDetails;
    }

    /**
     * Returns the custom error status code mappings.
     *
     * @return the custom error status code mappings
     */
    public Map<String, Integer> getErrorStatusMappings() {
      return errorStatusMappings;
    }

    /**
     * Sets the custom error status code mappings.
     *
     * @param errorStatusMappings the custom error status code mappings
     */
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

    /** Creates a new Validation configuration with default values. */
    public Validation() {}

    /**
     * Returns whether validation is enabled.
     *
     * @return whether validation is enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether validation is enabled.
     *
     * @param enabled whether validation is enabled
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns whether errors are accumulated.
     *
     * @return whether errors are accumulated
     */
    public boolean isAccumulateErrors() {
      return accumulateErrors;
    }

    /**
     * Sets whether errors are accumulated.
     *
     * @param accumulateErrors whether errors are accumulated
     */
    public void setAccumulateErrors(boolean accumulateErrors) {
      this.accumulateErrors = accumulateErrors;
    }

    /**
     * Returns the maximum number of errors to accumulate.
     *
     * @return the maximum number of errors to accumulate
     */
    public int getMaxErrors() {
      return maxErrors;
    }

    /**
     * Sets the maximum number of errors to accumulate.
     *
     * @param maxErrors the maximum number of errors to accumulate
     */
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

    /** Creates a new Jackson configuration with default values. */
    public Jackson() {}

    /**
     * Returns whether custom serializers are enabled.
     *
     * @return whether custom serializers are enabled
     */
    public boolean isCustomSerializersEnabled() {
      return customSerializersEnabled;
    }

    /**
     * Sets whether custom serializers are enabled.
     *
     * @param customSerializersEnabled whether custom serializers are enabled
     */
    public void setCustomSerializersEnabled(boolean customSerializersEnabled) {
      this.customSerializersEnabled = customSerializersEnabled;
    }

    /**
     * Returns the Either serialization format.
     *
     * @return the Either serialization format
     */
    public SerializationFormat getEitherFormat() {
      return eitherFormat;
    }

    /**
     * Sets the Either serialization format.
     *
     * @param eitherFormat the Either serialization format
     */
    public void setEitherFormat(SerializationFormat eitherFormat) {
      this.eitherFormat = eitherFormat;
    }

    /**
     * Returns the Validated serialization format.
     *
     * @return the Validated serialization format
     */
    public SerializationFormat getValidatedFormat() {
      return validatedFormat;
    }

    /**
     * Sets the Validated serialization format.
     *
     * @param validatedFormat the Validated serialization format
     */
    public void setValidatedFormat(SerializationFormat validatedFormat) {
      this.validatedFormat = validatedFormat;
    }

    /**
     * Returns the Maybe serialization format.
     *
     * @return the Maybe serialization format
     */
    public SerializationFormat getMaybeFormat() {
      return maybeFormat;
    }

    /**
     * Sets the Maybe serialization format.
     *
     * @param maybeFormat the Maybe serialization format
     */
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

    /** Creates a new Async configuration with default values. */
    public Async() {}

    /**
     * Returns the executor core pool size.
     *
     * @return the executor core pool size
     */
    public int getExecutorCorePoolSize() {
      return executorCorePoolSize;
    }

    /**
     * Sets the executor core pool size.
     *
     * @param executorCorePoolSize the executor core pool size
     */
    public void setExecutorCorePoolSize(int executorCorePoolSize) {
      this.executorCorePoolSize = executorCorePoolSize;
    }

    /**
     * Returns the executor maximum pool size.
     *
     * @return the executor maximum pool size
     */
    public int getExecutorMaxPoolSize() {
      return executorMaxPoolSize;
    }

    /**
     * Sets the executor maximum pool size.
     *
     * @param executorMaxPoolSize the executor maximum pool size
     */
    public void setExecutorMaxPoolSize(int executorMaxPoolSize) {
      this.executorMaxPoolSize = executorMaxPoolSize;
    }

    /**
     * Returns the executor queue capacity.
     *
     * @return the executor queue capacity
     */
    public int getExecutorQueueCapacity() {
      return executorQueueCapacity;
    }

    /**
     * Sets the executor queue capacity.
     *
     * @param executorQueueCapacity the executor queue capacity
     */
    public void setExecutorQueueCapacity(int executorQueueCapacity) {
      this.executorQueueCapacity = executorQueueCapacity;
    }

    /**
     * Returns the executor thread name prefix.
     *
     * @return the executor thread name prefix
     */
    public String getExecutorThreadNamePrefix() {
      return executorThreadNamePrefix;
    }

    /**
     * Sets the executor thread name prefix.
     *
     * @param executorThreadNamePrefix the executor thread name prefix
     */
    public void setExecutorThreadNamePrefix(String executorThreadNamePrefix) {
      this.executorThreadNamePrefix = executorThreadNamePrefix;
    }

    /**
     * Returns the default timeout in milliseconds.
     *
     * @return the default timeout in milliseconds
     */
    public long getDefaultTimeoutMs() {
      return defaultTimeoutMs;
    }

    /**
     * Sets the default timeout in milliseconds.
     *
     * @param defaultTimeoutMs the default timeout in milliseconds
     */
    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
      this.defaultTimeoutMs = defaultTimeoutMs;
    }
  }

  /** Actuator configuration properties. */
  public static class Actuator {
    /** Enable Micrometer metrics for HKJ handlers. Default: true */
    private boolean metricsEnabled = true;

    /** Creates a new Actuator configuration with default values. */
    public Actuator() {}

    /**
     * Returns whether metrics are enabled.
     *
     * @return whether metrics are enabled
     */
    public boolean isMetricsEnabled() {
      return metricsEnabled;
    }

    /**
     * Sets whether metrics are enabled.
     *
     * @param metricsEnabled whether metrics are enabled
     */
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

    /** Creates a new Security configuration with default values. */
    public Security() {}

    /**
     * Returns whether security integration is enabled.
     *
     * @return whether security integration is enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether security integration is enabled.
     *
     * @param enabled whether security integration is enabled
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns whether ValidatedUserDetailsService is enabled.
     *
     * @return whether ValidatedUserDetailsService is enabled
     */
    public boolean isValidatedUserDetails() {
      return validatedUserDetails;
    }

    /**
     * Sets whether ValidatedUserDetailsService is enabled.
     *
     * @param validatedUserDetails whether ValidatedUserDetailsService is enabled
     */
    public void setValidatedUserDetails(boolean validatedUserDetails) {
      this.validatedUserDetails = validatedUserDetails;
    }

    /**
     * Returns whether EitherAuthenticationConverter is enabled.
     *
     * @return whether EitherAuthenticationConverter is enabled
     */
    public boolean isEitherAuthentication() {
      return eitherAuthentication;
    }

    /**
     * Sets whether EitherAuthenticationConverter is enabled.
     *
     * @param eitherAuthentication whether EitherAuthenticationConverter is enabled
     */
    public void setEitherAuthentication(boolean eitherAuthentication) {
      this.eitherAuthentication = eitherAuthentication;
    }

    /**
     * Returns whether EitherAuthorizationManager is enabled.
     *
     * @return whether EitherAuthorizationManager is enabled
     */
    public boolean isEitherAuthorization() {
      return eitherAuthorization;
    }

    /**
     * Sets whether EitherAuthorizationManager is enabled.
     *
     * @param eitherAuthorization whether EitherAuthorizationManager is enabled
     */
    public void setEitherAuthorization(boolean eitherAuthorization) {
      this.eitherAuthorization = eitherAuthorization;
    }

    /**
     * Returns the JWT authorities claim name.
     *
     * @return the JWT authorities claim name
     */
    public String getJwtAuthoritiesClaim() {
      return jwtAuthoritiesClaim;
    }

    /**
     * Sets the JWT authorities claim name.
     *
     * @param jwtAuthoritiesClaim the JWT authorities claim name
     */
    public void setJwtAuthoritiesClaim(String jwtAuthoritiesClaim) {
      this.jwtAuthoritiesClaim = jwtAuthoritiesClaim;
    }

    /**
     * Returns the JWT authority prefix.
     *
     * @return the JWT authority prefix
     */
    public String getJwtAuthorityPrefix() {
      return jwtAuthorityPrefix;
    }

    /**
     * Sets the JWT authority prefix.
     *
     * @param jwtAuthorityPrefix the JWT authority prefix
     */
    public void setJwtAuthorityPrefix(String jwtAuthorityPrefix) {
      this.jwtAuthorityPrefix = jwtAuthorityPrefix;
    }
  }

  /**
   * Virtual thread configuration properties for VTask and VStream integration.
   *
   * <p>These properties control the behaviour of virtual-thread-based handlers. Unlike the {@link
   * Async} configuration which manages a fixed thread pool, virtual threads require no pool sizing
   * — they scale automatically with the JVM.
   *
   * <p>Example configuration:
   *
   * <pre>
   * hkj:
   *   virtual-threads:
   *     default-timeout-ms: 30000
   *     stream-timeout-ms: 60000
   * </pre>
   */
  public static class VirtualThreads {

    /** Default timeout for VTaskPath responses in milliseconds. Default: 30000 (30 seconds) */
    private long defaultTimeoutMs = 30000;

    /**
     * Default timeout for VStreamPath streaming responses in milliseconds. Default: 60000 (60
     * seconds). Set to 0 for no timeout.
     */
    private long streamTimeoutMs = 60000;

    /** Error rate threshold for virtual thread health indicator (0.0 to 1.0). Default: 0.5 */
    private double healthErrorThreshold = 0.5;

    /** Creates a new VirtualThreads configuration with default values. */
    public VirtualThreads() {}

    /**
     * Returns the default timeout in milliseconds.
     *
     * @return the default timeout in milliseconds
     */
    public long getDefaultTimeoutMs() {
      return defaultTimeoutMs;
    }

    /**
     * Sets the default timeout in milliseconds.
     *
     * @param defaultTimeoutMs the default timeout in milliseconds
     */
    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
      this.defaultTimeoutMs = defaultTimeoutMs;
    }

    /**
     * Returns the stream timeout in milliseconds.
     *
     * @return the stream timeout in milliseconds
     */
    public long getStreamTimeoutMs() {
      return streamTimeoutMs;
    }

    /**
     * Sets the stream timeout in milliseconds.
     *
     * @param streamTimeoutMs the stream timeout in milliseconds
     */
    public void setStreamTimeoutMs(long streamTimeoutMs) {
      this.streamTimeoutMs = streamTimeoutMs;
    }

    /**
     * Returns the health error threshold.
     *
     * @return the health error threshold
     */
    public double getHealthErrorThreshold() {
      return healthErrorThreshold;
    }

    /**
     * Sets the health error threshold.
     *
     * @param healthErrorThreshold the health error threshold
     */
    public void setHealthErrorThreshold(double healthErrorThreshold) {
      this.healthErrorThreshold = healthErrorThreshold;
    }
  }

  /**
   * Effect boundary configuration properties.
   *
   * <p>Configure via application.yml:
   *
   * <pre>
   * hkj:
   *   effect-boundary:
   *     enabled: true
   *     startup-validation: true
   *     interpreter-selection:
   *       payment-gateway: stripe
   *       fraud-check: ml-model
   * </pre>
   */
  public static class EffectConfig {

    /** Enable EffectBoundary auto-configuration. Default: true */
    private boolean enabled = true;

    /** Fail fast at startup if any declared effect has no matching interpreter. Default: true */
    private boolean startupValidation = true;

    /**
     * Configuration-driven interpreter selection by qualifier.
     *
     * <p>Maps effect algebra names (kebab-case) to interpreter qualifier names. When set, the
     * registrar selects the interpreter matching the configured qualifier instead of using
     * auto-discovery.
     *
     * <p>Example: {@code payment-gateway: stripe} selects the interpreter annotated with
     * {@code @Interpreter(value = PaymentGatewayOp.class, qualifier = "stripe")}.
     */
    private Map<String, String> interpreterSelection = new HashMap<>();

    /** Creates a new EffectConfig with default values. */
    public EffectConfig() {}

    /**
     * Returns whether EffectBoundary auto-configuration is enabled.
     *
     * @return whether enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether EffectBoundary auto-configuration is enabled.
     *
     * @param enabled whether enabled
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns whether startup validation is enabled.
     *
     * @return whether startup validation is enabled
     */
    public boolean isStartupValidation() {
      return startupValidation;
    }

    /**
     * Sets whether startup validation is enabled.
     *
     * @param startupValidation whether startup validation is enabled
     */
    public void setStartupValidation(boolean startupValidation) {
      this.startupValidation = startupValidation;
    }

    /**
     * Returns the interpreter selection map.
     *
     * @return the interpreter selection map
     */
    public Map<String, String> getInterpreterSelection() {
      return interpreterSelection;
    }

    /**
     * Sets the interpreter selection map.
     *
     * @param interpreterSelection the interpreter selection map
     */
    public void setInterpreterSelection(Map<String, String> interpreterSelection) {
      this.interpreterSelection = interpreterSelection;
    }
  }
}
