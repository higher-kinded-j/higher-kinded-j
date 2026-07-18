// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers an {@link EffectBoundary} bean by discovering {@link
 * Interpreter @Interpreter}-annotated beans and combining them using {@link Interpreters#combine}.
 *
 * <p>This registrar is imported by {@link EnableEffectBoundary} and performs its work at bean
 * definition registration time. The actual interpreter discovery and combination happens during
 * bean creation (lazy), so that all {@code @Interpreter} beans are available in the context.
 * Discovery honours {@link Interpreter#profile()} and fails fast on ambiguous matches — see {@link
 * InterpreterResolution}.
 *
 * <p>The whole mechanism can be switched off with {@code hkj.effect-boundary.enabled=false}.
 *
 * @see EnableEffectBoundary
 * @see Interpreter
 */
public class EffectBoundaryRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

  private static final Logger log = LoggerFactory.getLogger(EffectBoundaryRegistrar.class);

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

    if (environment != null
        && !environment.getProperty("hkj.effect-boundary.enabled", Boolean.class, true)) {
      log.info("EffectBoundary: disabled via hkj.effect-boundary.enabled=false");
      return;
    }

    Map<String, Object> attrs =
        importingClassMetadata.getAnnotationAttributes(EnableEffectBoundary.class.getName());

    if (attrs == null) {
      return;
    }

    Class<?>[] effectAlgebras = (Class<?>[]) attrs.get("value");
    if (effectAlgebras == null || effectAlgebras.length == 0) {
      log.warn("@EnableEffectBoundary has no effect algebras declared");
      return;
    }

    if (registry.containsBeanDefinition("effectBoundary")) {
      log.debug("EffectBoundary: effectBoundary bean already registered, skipping");
      return;
    }

    List<String> algebraNames = Arrays.stream(effectAlgebras).map(Class::getSimpleName).toList();
    log.info(
        "EffectBoundary: registering boundary for {} effect algebras: {}",
        effectAlgebras.length,
        algebraNames);

    // Register a factory bean that will discover interpreters at creation time
    GenericBeanDefinition beanDef = new GenericBeanDefinition();
    beanDef.setBeanClass(EffectBoundaryFactoryBean.class);
    beanDef.setScope(BeanDefinition.SCOPE_SINGLETON);
    beanDef.getConstructorArgumentValues().addGenericArgumentValue(effectAlgebras);
    beanDef.setLazyInit(false);

    registry.registerBeanDefinition("effectBoundary", beanDef);
  }

  /**
   * Factory bean that creates an {@link EffectBoundary} by discovering {@link Interpreter} beans at
   * creation time.
   */
  public static class EffectBoundaryFactoryBean implements FactoryBean<EffectBoundary<?>> {

    private final Class<?>[] effectAlgebras;
    private ApplicationContext applicationContext;

    public EffectBoundaryFactoryBean(Class<?>[] effectAlgebras) {
      this.effectAlgebras = effectAlgebras;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EffectBoundary<?> getObject() {
      Natural combined =
          InterpreterResolution.resolveAndCombine(
              applicationContext, effectAlgebras, "EffectBoundary");
      return EffectBoundary.of(combined);
    }

    @Override
    public Class<?> getObjectType() {
      return EffectBoundary.class;
    }

    @Override
    public boolean isSingleton() {
      return true;
    }
  }
}
