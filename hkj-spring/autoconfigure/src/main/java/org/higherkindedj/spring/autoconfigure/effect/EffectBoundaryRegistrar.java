// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.io.IOKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers an {@link EffectBoundary} bean by discovering {@link
 * Interpreter @Interpreter}-annotated beans and combining them using {@link Interpreters#combine}.
 *
 * <p>This registrar is imported by {@link EnableEffectBoundary} and performs its work at bean
 * definition registration time. The actual interpreter discovery and combination happens during
 * bean creation (lazy), so that all {@code @Interpreter} beans are available in the context.
 *
 * @see EnableEffectBoundary
 * @see Interpreter
 */
public class EffectBoundaryRegistrar implements ImportBeanDefinitionRegistrar {

  private static final Logger log = LoggerFactory.getLogger(EffectBoundaryRegistrar.class);

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

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
      // Discover interpreter beans for each effect algebra
      Map<String, Object> interpreterBeans =
          applicationContext.getBeansWithAnnotation(Interpreter.class);

      List<Natural<?, IOKind.Witness>> interpreters = new ArrayList<>();
      List<String> missing = new ArrayList<>();

      for (Class<?> algebra : effectAlgebras) {
        boolean found = false;
        for (Map.Entry<String, Object> entry : interpreterBeans.entrySet()) {
          Object bean = entry.getValue();
          Interpreter annotation =
              AnnotationUtils.findAnnotation(bean.getClass(), Interpreter.class);
          if (annotation != null && annotation.value().equals(algebra)) {
            if (bean instanceof Natural<?, ?> nat) {
              interpreters.add((Natural) nat);
              found = true;
              log.info(
                  "EffectBoundary: found interpreter '{}' for {}",
                  entry.getKey(),
                  algebra.getSimpleName());
              break;
            }
          }
        }
        if (!found) {
          missing.add(algebra.getSimpleName());
        }
      }

      if (!missing.isEmpty()) {
        throw new BeanCreationException(
            "effectBoundary",
            "No interpreter found for effect algebra(s): "
                + missing
                + ". Declare beans annotated with @Interpreter for each algebra. "
                + "Available interpreters: "
                + interpreterBeans.keySet());
      }

      // Combine interpreters
      Natural combined = combineInterpreters(interpreters);
      return EffectBoundary.of(combined);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Natural combineInterpreters(List<Natural<?, IOKind.Witness>> interpreters) {
      if (interpreters.size() == 1) {
        return interpreters.getFirst();
      } else if (interpreters.size() == 2) {
        return Interpreters.combine((Natural) interpreters.get(0), (Natural) interpreters.get(1));
      } else if (interpreters.size() == 3) {
        return Interpreters.combine(
            (Natural) interpreters.get(0),
            (Natural) interpreters.get(1),
            (Natural) interpreters.get(2));
      } else if (interpreters.size() == 4) {
        return Interpreters.combine(
            (Natural) interpreters.get(0),
            (Natural) interpreters.get(1),
            (Natural) interpreters.get(2),
            (Natural) interpreters.get(3));
      } else {
        throw new BeanCreationException(
            "effectBoundary",
            "Interpreters.combine() supports up to 4 effect algebras. "
                + "Found "
                + interpreters.size()
                + ". Group related effects into sub-compositions.");
      }
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
