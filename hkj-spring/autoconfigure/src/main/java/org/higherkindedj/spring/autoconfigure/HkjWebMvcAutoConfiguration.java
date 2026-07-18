// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.higherkindedj.spring.web.returnvalue.CompletableFuturePathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.DefaultErrorStatusCodeStrategy;
import org.higherkindedj.spring.web.returnvalue.EitherOrBothPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.EitherPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.ErrorStatusCodeStrategy;
import org.higherkindedj.spring.web.returnvalue.FreePathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.IOPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.MaybePathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.TryPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.VStreamPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.VTaskPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.ValidationPathReturnValueHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration for higher-kinded-j Effect Path API Spring Web MVC integration.
 *
 * <p>This configuration is activated when:
 *
 * <ul>
 *   <li>{@link Kind} is on the classpath (higher-kinded-j core)
 *   <li>{@link DispatcherServlet} is on the classpath (Spring Web MVC)
 *   <li>The application is a servlet-based web application
 * </ul>
 *
 * <p>Registers return value handlers for Effect Path types:
 *
 * <ul>
 *   <li>{@link EitherPathReturnValueHandler} - Handles EitherPath return types
 *   <li>{@link MaybePathReturnValueHandler} - Handles MaybePath return types
 *   <li>{@link TryPathReturnValueHandler} - Handles TryPath return types
 *   <li>{@link ValidationPathReturnValueHandler} - Handles ValidationPath with error accumulation
 *   <li>{@link EitherOrBothPathReturnValueHandler} - Handles EitherOrBothPath
 *       (success-with-warnings)
 *   <li>{@link IOPathReturnValueHandler} - Handles IOPath deferred execution
 *   <li>{@link CompletableFuturePathReturnValueHandler} - Handles async CompletableFuturePath
 *   <li>{@link VTaskPathReturnValueHandler} - Handles VTaskPath virtual thread execution
 *   <li>{@link VStreamPathReturnValueHandler} - Handles VStreamPath SSE streaming on virtual
 *       threads
 * </ul>
 *
 * <p>Uses {@link WebMvcRegistrations} to customize the {@link RequestMappingHandlerAdapter} and
 * inject our handlers BEFORE Spring's default handlers, ensuring they take precedence.
 *
 * <p><b>Note:</b> This version requires Spring Boot 4.0.1+ and uses Jackson 3.x. For Spring Boot
 * 3.5.7, use hkj-spring version 0.2.7.
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HkjWebMvcAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(HkjWebMvcAutoConfiguration.class);

  /** Creates a new HkjWebMvcAutoConfiguration. */
  public HkjWebMvcAutoConfiguration() {}

  /**
   * Detects a silent conflict: Spring Boot consumes {@link WebMvcRegistrations} via {@code
   * getIfUnique()}, so if the application defines its own bean alongside {@code
   * hkjWebMvcRegistrations}, <em>both</em> are ignored without any error and every HKJ Path
   * return-value handler vanishes (controllers returning Path types would then be serialised as raw
   * objects). This check makes that failure mode loud.
   *
   * @param applicationContext the application context
   * @return a callback that logs a warning when more than one registrations bean exists
   */
  @Bean
  public SmartInitializingSingleton hkjWebMvcRegistrationsConflictCheck(
      ApplicationContext applicationContext) {
    return () -> {
      String[] names = applicationContext.getBeanNamesForType(WebMvcRegistrations.class);
      if (names.length > 1) {
        log.warn(
            "Multiple WebMvcRegistrations beans found: {}. Spring Boot resolves"
                + " WebMvcRegistrations with getIfUnique(), so ALL of them are silently ignored —"
                + " including the HKJ Path return-value handlers. Controllers returning Path types"
                + " will be serialised as raw objects. Merge your customisation into a single"
                + " WebMvcRegistrations bean, or exclude HkjWebMvcAutoConfiguration.",
            Arrays.toString(names));
      }
    };
  }

  /**
   * Default {@link ErrorStatusCodeStrategy} that combines the {@code hkj.web.error-status-mappings}
   * property table with the heuristics in {@code ErrorStatusCodeMapper}. Adopters who need
   * field-aware mappings (e.g. {@code MfaThrottledError.retryAfter() ≥ N → 503}) can replace this
   * by declaring their own bean of type {@link ErrorStatusCodeStrategy}; the
   * {@code @ConditionalOnMissingBean} guard ensures the user bean wins.
   *
   * @param properties the HKJ configuration properties
   * @return the default error status code strategy
   */
  @Bean
  @ConditionalOnMissingBean
  public ErrorStatusCodeStrategy hkjErrorStatusCodeStrategy(HkjProperties properties) {
    return new DefaultErrorStatusCodeStrategy(properties.getWeb().getErrorStatusMappings());
  }

  /**
   * Customizes the RequestMappingHandlerAdapter to add Effect Path return value handlers before
   * Spring's default handlers.
   *
   * <p>Handlers are conditionally registered based on configuration properties:
   *
   * <ul>
   *   <li>hkj.web.either-path-enabled - controls EitherPathReturnValueHandler
   *   <li>hkj.web.maybe-path-enabled - controls MaybePathReturnValueHandler
   *   <li>hkj.web.try-path-enabled - controls TryPathReturnValueHandler
   *   <li>hkj.web.validation-path-enabled - controls ValidationPathReturnValueHandler
   *   <li>hkj.web.either-or-both-path-enabled - controls EitherOrBothPathReturnValueHandler
   *   <li>hkj.web.io-path-enabled - controls IOPathReturnValueHandler
   *   <li>hkj.web.completable-future-path-enabled - controls
   *       CompletableFuturePathReturnValueHandler
   *   <li>hkj.web.vtask-path-enabled - controls VTaskPathReturnValueHandler
   *   <li>hkj.web.vstream-path-enabled - controls VStreamPathReturnValueHandler
   * </ul>
   *
   * @param properties The HKJ configuration properties
   * @param jsonMapper The Jackson 3.x JsonMapper bean for JSON serialization
   * @param metricsService The metrics service for recording handler invocations (may be null)
   * @return WebMvcRegistrations that customize the handler adapter
   */
  @Bean
  public WebMvcRegistrations hkjWebMvcRegistrations(
      HkjProperties properties,
      JsonMapper jsonMapper,
      ApplicationContext applicationContext,
      ErrorStatusCodeStrategy errorStatusCodeStrategy,
      @Autowired(required = false) @Nullable HkjMetricsService metricsService) {
    return new WebMvcRegistrations() {
      @Override
      public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
        return new RequestMappingHandlerAdapter() {
          @Override
          public void afterPropertiesSet() {
            super.afterPropertiesSet();

            // Get the current list of handlers
            List<HandlerMethodReturnValueHandler> originalHandlers =
                new ArrayList<>(getReturnValueHandlers());

            // Create new list with our handlers first (if enabled)
            List<HandlerMethodReturnValueHandler> newHandlers = new ArrayList<>();
            HkjProperties.Web webConfig = properties.getWeb();

            // Register Path handlers in order of specificity
            if (webConfig.isEitherPathEnabled()) {
              newHandlers.add(
                  new EitherPathReturnValueHandler(
                      jsonMapper, webConfig.getDefaultErrorStatus(), errorStatusCodeStrategy));
            }

            if (webConfig.isMaybePathEnabled()) {
              newHandlers.add(
                  new MaybePathReturnValueHandler(jsonMapper, webConfig.getMaybeNothingStatus()));
            }

            if (webConfig.isTryPathEnabled()) {
              newHandlers.add(
                  new TryPathReturnValueHandler(
                      jsonMapper,
                      webConfig.getTryFailureStatus(),
                      webConfig.isTryIncludeExceptionDetails()));
            }

            if (webConfig.isValidationPathEnabled()) {
              newHandlers.add(
                  new ValidationPathReturnValueHandler(
                      jsonMapper, webConfig.getValidationInvalidStatus()));
            }

            if (webConfig.isEitherOrBothPathEnabled()) {
              newHandlers.add(
                  new EitherOrBothPathReturnValueHandler(
                      jsonMapper, webConfig.getDefaultErrorStatus(), errorStatusCodeStrategy));
            }

            if (webConfig.isIoPathEnabled()) {
              newHandlers.add(
                  new IOPathReturnValueHandler(
                      jsonMapper,
                      webConfig.getIoFailureStatus(),
                      webConfig.isIoIncludeExceptionDetails()));
            }

            if (webConfig.isCompletableFuturePathEnabled()) {
              newHandlers.add(
                  new CompletableFuturePathReturnValueHandler(
                      jsonMapper,
                      webConfig.getAsyncFailureStatus(),
                      webConfig.isAsyncIncludeExceptionDetails(),
                      properties.getAsync().getDefaultTimeoutMs()));
            }

            // Virtual thread handlers
            HkjProperties.VirtualThreads vtConfig = properties.getVirtualThreads();

            if (webConfig.isVtaskPathEnabled()) {
              newHandlers.add(
                  new VTaskPathReturnValueHandler(
                      jsonMapper,
                      webConfig.getVtaskFailureStatus(),
                      webConfig.isVtaskIncludeExceptionDetails(),
                      vtConfig.getDefaultTimeoutMs(),
                      metricsService));
            }

            if (webConfig.isVstreamPathEnabled()) {
              newHandlers.add(
                  new VStreamPathReturnValueHandler(
                      jsonMapper,
                      webConfig.getVstreamFailureStatus(),
                      webConfig.isVstreamIncludeExceptionDetails(),
                      vtConfig.getStreamTimeoutMs(),
                      metricsService));
            }

            // FreePath handler (effect boundary interpretation)
            if (webConfig.isFreePathEnabled()) {
              newHandlers.add(
                  new FreePathReturnValueHandler(
                      jsonMapper,
                      webConfig.getFreePathFailureStatus(),
                      webConfig.isFreePathIncludeExceptionDetails(),
                      applicationContext));
            }

            newHandlers.addAll(originalHandlers);

            // Set the new list
            setReturnValueHandlers(newHandlers);
          }
        };
      }
    };
  }
}
