package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.hkt.Either;
import org.higherkindedj.hkt.EitherInstances;
import org.higherkindedj.hkt.Kind;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for higher-kinded-j core functionality.
 * <p>
 * This configuration is activated when {@link Kind} is on the classpath
 * and provides beans for common type class instances.
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(Kind.class)
public class HkjAutoConfiguration {

    /**
     * Provides the EitherInstances bean for type class operations on Either.
     *
     * @return the Either type class instances
     */
    @Bean
    @ConditionalOnMissingBean
    public EitherInstances eitherInstances() {
        return EitherInstances.instances();
    }
}
