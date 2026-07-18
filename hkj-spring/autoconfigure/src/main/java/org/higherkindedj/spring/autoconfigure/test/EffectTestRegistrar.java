// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.spring.autoconfigure.effect.Interpreter;
import org.higherkindedj.spring.autoconfigure.effect.InterpreterResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers an {@link EffectBoundary} bean for test contexts by discovering {@link
 * Interpreter @Interpreter}-annotated beans and combining them.
 *
 * <p>This registrar is imported by {@link EffectTest} and only activates when the {@code effects}
 * parameter is non-empty. It mirrors the production {@link
 * org.higherkindedj.spring.autoconfigure.effect.EffectBoundaryRegistrar} but is designed for test
 * contexts where the web layer may not be present. Discovery is shared with the production path via
 * {@link InterpreterResolution}, so profile filtering and ambiguity detection behave identically.
 *
 * @see EffectTest
 */
public class EffectTestRegistrar implements ImportBeanDefinitionRegistrar {

  private static final Logger log = LoggerFactory.getLogger(EffectTestRegistrar.class);

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

    Map<String, Object> attrs =
        importingClassMetadata.getAnnotationAttributes(EffectTest.class.getName());

    if (attrs == null) {
      return;
    }

    Class<?>[] effects = (Class<?>[]) attrs.get("effects");
    if (effects == null || effects.length == 0) {
      return; // No effects declared — skip auto-wiring
    }

    // Don't register if one already exists (e.g., from @EnableEffectBoundary on the app class)
    if (registry.containsBeanDefinition("effectBoundary")) {
      log.debug("EffectTest: effectBoundary bean already registered, skipping");
      return;
    }

    List<String> algebraNames = Arrays.stream(effects).map(Class::getSimpleName).toList();
    log.info("EffectTest: registering test boundary for effects: {}", algebraNames);

    GenericBeanDefinition beanDef = new GenericBeanDefinition();
    beanDef.setBeanClass(EffectTestBoundaryFactoryBean.class);
    beanDef.setScope(BeanDefinition.SCOPE_SINGLETON);
    beanDef.getConstructorArgumentValues().addGenericArgumentValue(effects);
    beanDef.setLazyInit(false);

    registry.registerBeanDefinition("effectBoundary", beanDef);
  }

  /**
   * Factory bean that creates an {@link EffectBoundary} for test contexts by discovering {@link
   * Interpreter} beans.
   */
  public static class EffectTestBoundaryFactoryBean implements FactoryBean<EffectBoundary<?>> {

    private final Class<?>[] effects;
    private ApplicationContext applicationContext;

    public EffectTestBoundaryFactoryBean(Class<?>[] effects) {
      this.effects = effects;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EffectBoundary<?> getObject() {
      Natural combined =
          InterpreterResolution.resolveAndCombine(applicationContext, effects, "EffectTest");
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
