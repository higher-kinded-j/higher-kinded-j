package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.spring.web.returnvalue.EitherReturnValueHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration for higher-kinded-j Spring Web MVC integration.
 * <p>
 * This configuration is activated when:
 * <ul>
 *   <li>{@link Kind} is on the classpath (higher-kinded-j core)</li>
 *   <li>{@link DispatcherServlet} is on the classpath (Spring Web MVC)</li>
 *   <li>The application is a servlet-based web application</li>
 * </ul>
 * </p>
 * <p>
 * Registers return value handlers for functional types:
 * <ul>
 *   <li>{@link EitherReturnValueHandler} - Handles Either return types</li>
 * </ul>
 * </p>
 */
@AutoConfiguration(after = HkjAutoConfiguration.class)
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HkjWebMvcAutoConfiguration {

    /**
     * Configuration for Either return value handling.
     */
    @Configuration(proxyBeanMethods = false)
    static class EitherReturnValueConfiguration implements WebMvcConfigurer {

        @Override
        public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
            handlers.add(new EitherReturnValueHandler());
        }
    }
}
