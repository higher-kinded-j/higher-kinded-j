// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.spring.web.returnvalue.EitherReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.EitherTReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.ValidatedReturnValueHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Auto-configuration for higher-kinded-j Spring Web MVC integration.
 *
 * <p>This configuration is activated when:
 *
 * <ul>
 *   <li>{@link Kind} is on the classpath (higher-kinded-j core)
 *   <li>{@link DispatcherServlet} is on the classpath (Spring Web MVC)
 *   <li>The application is a servlet-based web application
 * </ul>
 *
 * <p>Registers return value handlers for functional types:
 *
 * <ul>
 *   <li>{@link EitherReturnValueHandler} - Handles Either return types
 *   <li>{@link ValidatedReturnValueHandler} - Handles Validated return types with error
 *       accumulation
 *   <li>{@link EitherTReturnValueHandler} - Handles EitherT async return types
 * </ul>
 *
 * <p>Uses {@link WebMvcRegistrations} to customize the {@link RequestMappingHandlerAdapter} and
 * inject our handlers BEFORE Spring's default handlers, ensuring they take precedence.
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HkjWebMvcAutoConfiguration {

  /**
   * Customizes the RequestMappingHandlerAdapter to add our return value handlers before Spring's
   * default handlers.
   *
   * <p>Handlers are conditionally registered based on configuration properties:
   *
   * <ul>
   *   <li>hkj.web.either-response-enabled - controls EitherReturnValueHandler
   *   <li>hkj.web.validated-response-enabled - controls ValidatedReturnValueHandler
   *   <li>hkj.web.async-either-t-enabled - controls EitherTReturnValueHandler
   * </ul>
   *
   * @param properties The HKJ configuration properties
   * @param objectMapper The Jackson ObjectMapper bean for JSON serialization
   * @return WebMvcRegistrations that customize the handler adapter
   */
  @Bean
  public WebMvcRegistrations hkjWebMvcRegistrations(
      HkjProperties properties, ObjectMapper objectMapper) {
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

            if (properties.getWeb().isEitherResponseEnabled()) {
              newHandlers.add(
                  new EitherReturnValueHandler(
                      objectMapper, properties.getWeb().getDefaultErrorStatus()));
            }

            if (properties.getWeb().isValidatedResponseEnabled()) {
              newHandlers.add(new ValidatedReturnValueHandler(objectMapper));
            }

            if (properties.getWeb().isAsyncEitherTEnabled()) {
              newHandlers.add(
                  new EitherTReturnValueHandler(properties.getWeb().getDefaultErrorStatus()));
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
