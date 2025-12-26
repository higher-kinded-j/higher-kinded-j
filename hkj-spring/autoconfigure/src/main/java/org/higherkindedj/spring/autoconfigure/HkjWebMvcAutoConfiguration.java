// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.spring.web.returnvalue.CompletableFuturePathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.EitherPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.IOPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.MaybePathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.TryPathReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.ValidationPathReturnValueHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
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
 *   <li>{@link IOPathReturnValueHandler} - Handles IOPath deferred execution
 *   <li>{@link CompletableFuturePathReturnValueHandler} - Handles async CompletableFuturePath
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
   *   <li>hkj.web.io-path-enabled - controls IOPathReturnValueHandler
   *   <li>hkj.web.completable-future-path-enabled - controls
   *       CompletableFuturePathReturnValueHandler
   * </ul>
   *
   * @param properties The HKJ configuration properties
   * @param jsonMapper The Jackson 3.x JsonMapper bean for JSON serialization
   * @return WebMvcRegistrations that customize the handler adapter
   */
  @Bean
  public WebMvcRegistrations hkjWebMvcRegistrations(
      HkjProperties properties, JsonMapper jsonMapper) {
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
                  new EitherPathReturnValueHandler(jsonMapper, webConfig.getDefaultErrorStatus()));
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

            newHandlers.addAll(originalHandlers);

            // Set the new list
            setReturnValueHandlers(newHandlers);
          }
        };
      }
    };
  }
}
