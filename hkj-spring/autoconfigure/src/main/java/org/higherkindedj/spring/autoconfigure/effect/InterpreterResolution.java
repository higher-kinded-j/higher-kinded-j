// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.io.IOKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Shared interpreter discovery for {@link EffectBoundaryRegistrar} and the test-slice registrar.
 *
 * <p>Resolution rules, per effect algebra:
 *
 * <ul>
 *   <li>Only beans annotated {@link Interpreter @Interpreter} whose {@code value()} matches the
 *       algebra are considered.
 *   <li>An interpreter with a non-empty {@link Interpreter#profile()} is eligible only when that
 *       profile expression is active in the {@link Environment}.
 *   <li>An eligible profile-restricted interpreter wins over an unrestricted one — so a {@code
 *       profile = "test"} stub automatically replaces the production interpreter when the test
 *       profile is active.
 *   <li>Within the same specificity tier, exactly one interpreter must remain: none at all →
 *       startup failure listing the available beans; more than one → startup failure listing the
 *       ambiguous candidates (previously the first bean in scan order silently won, which made
 *       interpreter selection dependent on classpath order).
 * </ul>
 */
public final class InterpreterResolution {

  private static final Logger log = LoggerFactory.getLogger(InterpreterResolution.class);

  private InterpreterResolution() {}

  /**
   * Discovers one interpreter per algebra and combines them with {@link Interpreters#combine}.
   *
   * @param applicationContext the context to discover {@code @Interpreter} beans in
   * @param algebras the effect algebras, in composition order
   * @param origin a short label ("EffectBoundary" / "EffectTest") for log and error messages
   * @return the combined natural transformation
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Natural resolveAndCombine(
      ApplicationContext applicationContext, Class<?>[] algebras, String origin) {
    Map<String, Object> interpreterBeans =
        applicationContext.getBeansWithAnnotation(Interpreter.class);
    Environment environment = applicationContext.getEnvironment();

    List<Natural<?, IOKind.Witness>> interpreters = new ArrayList<>();
    List<String> missing = new ArrayList<>();

    for (Class<?> algebra : algebras) {
      // Profile-restricted candidates are more specific and shadow unrestricted ones
      List<String> profiledNames = new ArrayList<>();
      List<String> unrestrictedNames = new ArrayList<>();
      Natural<?, ?> profiledCandidate = null;
      Natural<?, ?> unrestrictedCandidate = null;

      for (Map.Entry<String, Object> entry : interpreterBeans.entrySet()) {
        Object bean = entry.getValue();
        Interpreter annotation = AnnotationUtils.findAnnotation(bean.getClass(), Interpreter.class);
        if (annotation == null
            || !annotation.value().equals(algebra)
            || !(bean instanceof Natural<?, ?> nat)
            || !profileActive(environment, annotation.profile())) {
          continue;
        }
        if (annotation.profile().isEmpty()) {
          unrestrictedNames.add(entry.getKey());
          unrestrictedCandidate = nat;
        } else {
          profiledNames.add(entry.getKey());
          profiledCandidate = nat;
        }
      }

      List<String> tierNames = profiledNames.isEmpty() ? unrestrictedNames : profiledNames;
      Natural<?, ?> selected = profiledNames.isEmpty() ? unrestrictedCandidate : profiledCandidate;

      switch (tierNames.size()) {
        case 0 -> missing.add(algebra.getSimpleName());
        case 1 -> {
          interpreters.add((Natural) selected);
          log.info(
              "{}: found interpreter '{}' for {}",
              origin,
              tierNames.getFirst(),
              algebra.getSimpleName());
        }
        default ->
            throw new BeanCreationException(
                "effectBoundary",
                origin
                    + ": ambiguous interpreters for "
                    + algebra.getSimpleName()
                    + ": "
                    + tierNames
                    + ". Keep exactly one eligible @Interpreter bean per algebra — restrict"
                    + " alternatives to a profile via @Interpreter(profile = \"...\").");
      }
    }

    if (!missing.isEmpty()) {
      throw new BeanCreationException(
          "effectBoundary",
          origin
              + ": no interpreter found for effect algebra(s): "
              + missing
              + ". Declare beans annotated with @Interpreter for each algebra (and check any"
              + " profile restrictions). Available interpreters: "
              + interpreterBeans.keySet());
    }

    return combine(interpreters);
  }

  private static boolean profileActive(Environment environment, String profile) {
    return profile.isEmpty() || environment.acceptsProfiles(Profiles.of(profile));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Natural combine(List<Natural<?, IOKind.Witness>> interpreters) {
    return switch (interpreters.size()) {
      case 1 -> interpreters.getFirst();
      case 2 -> Interpreters.combine((Natural) interpreters.get(0), (Natural) interpreters.get(1));
      case 3 ->
          Interpreters.combine(
              (Natural) interpreters.get(0),
              (Natural) interpreters.get(1),
              (Natural) interpreters.get(2));
      case 4 ->
          Interpreters.combine(
              (Natural) interpreters.get(0),
              (Natural) interpreters.get(1),
              (Natural) interpreters.get(2),
              (Natural) interpreters.get(3));
      default ->
          throw new BeanCreationException(
              "effectBoundary",
              "Interpreters.combine() supports up to 4 effect algebras. Found "
                  + interpreters.size()
                  + ". Group related effects into sub-compositions.");
    };
  }
}
