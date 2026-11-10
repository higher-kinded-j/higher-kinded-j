// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Resolves which {@link TraversableGenerator} handles a container type.
 *
 * <p>The one home for generator selection: every site that chooses a generator
 * ({@code @GenerateFocus} widening, {@code @GenerateTraversals}, {@code @ImportOptics}) reads its
 * choice from here, so the three annotations cannot disagree about which generator claims a type.
 *
 * <p>Resolution honours {@link TraversableGenerator#priority()}: the highest-priority generator
 * that supports the type wins, wherever its {@code META-INF/services} entry lands. Among generators
 * of equal priority the first registered wins and a compile-time warning names both, anchored to
 * the record component the lookup is made for. A lookup made for no component, as a re-walk is,
 * reports nothing.
 *
 * <p>It also answers, for all three, one question about the generator it chose: whether that
 * generator orders a container by a wildcard, which leaves its rebuild untypeable ({@link
 * #ordersByWildcard}).
 *
 * @since 0.4.11
 */
public final class GeneratorRegistry {

  private final List<TraversableGenerator> generators;
  private final Messager messager;

  private GeneratorRegistry(List<TraversableGenerator> generators, Messager messager) {
    // A stable sort: equal priorities keep their registration order.
    this.generators =
        generators.stream()
            .sorted(Comparator.comparingInt(TraversableGenerator::priority).reversed())
            .toList();
    this.messager = Objects.requireNonNull(messager, "messager");
  }

  /**
   * Creates a registry over the generators {@link ServiceLoader} finds on {@code classLoader}.
   *
   * @param classLoader the loader to discover {@link TraversableGenerator} services with
   * @param messager the messager equal-priority conflicts are reported through
   * @return the registry
   */
  public static GeneratorRegistry fromServiceLoader(ClassLoader classLoader, Messager messager) {
    return new GeneratorRegistry(
        ServiceLoader.load(TraversableGenerator.class, classLoader).stream()
            .map(ServiceLoader.Provider::get)
            .toList(),
        messager);
  }

  /**
   * Creates a registry over an explicit list of generators.
   *
   * @param generators the generators to resolve among, in registration order
   * @param messager the messager equal-priority conflicts are reported through
   * @return the registry
   */
  public static GeneratorRegistry of(List<TraversableGenerator> generators, Messager messager) {
    return new GeneratorRegistry(generators, messager);
  }

  /**
   * The highest-priority generator that supports {@code type}, or null when none does.
   *
   * <p>A tie at the winning priority is reported as a warning against {@code component} and the
   * first registered of the tied generators wins; a lower-priority generator loses silently, which
   * is what priority is for.
   *
   * @param type the container type to resolve
   * @param component the record component to report an equal-priority conflict against, or null for
   *     a re-walk that stays silent because the analysis over the declaration reports the tie
   * @return the generator to use, or null
   */
  public TraversableGenerator generatorFor(TypeMirror type, Element component) {
    TraversableGenerator matched = null;
    for (TraversableGenerator generator : generators) {
      if (generator.supports(type)) {
        if (matched == null) {
          matched = generator;
        } else if (matched.priority() == generator.priority()) {
          warnOfConflict(type, component, matched, generator);
        }
      }
    }
    return matched;
  }

  /**
   * Whether {@code generator} orders {@code container} by a type argument written as a wildcard, so
   * that the rebuild it writes for the container cannot be typed.
   *
   * <p>A sorted container is rebuilt with the source's comparator, and a wildcard where it is
   * ordered leaves that comparator typed for an unknown type: see {@link
   * TraversableGenerator#getOrderedTypeArgumentIndex()}. Each route that generates a traversal
   * reads this one answer, so {@code @GenerateTraversals}, {@code @ImportOptics} and the fix line
   * {@code @GenerateFocus} writes cannot disagree about which components are refused a traversal.
   *
   * @param container the container type, as the component declares it
   * @param generator the generator that supports it
   * @return true when the argument the generator orders by is a wildcard
   */
  public static boolean ordersByWildcard(DeclaredType container, TraversableGenerator generator) {
    OptionalInt index = generator.getOrderedTypeArgumentIndex();
    List<? extends TypeMirror> arguments = container.getTypeArguments();
    // A raw container has no argument at the index, so names no wildcard there.
    return index.isPresent()
        && index.getAsInt() < arguments.size()
        && arguments.get(index.getAsInt()).getKind() == TypeKind.WILDCARD;
  }

  /** Warns that two generators of equal priority both claim a type, and which one won. */
  private void warnOfConflict(
      TypeMirror type,
      Element component,
      TraversableGenerator matched,
      TraversableGenerator other) {
    if (component == null) {
      return;
    }
    messager.printMessage(
        Diagnostic.Kind.WARNING,
        "Multiple TraversableGenerator SPI providers with equal priority ("
            + other.priority()
            + ") support type "
            + ProcessorUtils.simpleTypeName(type)
            + ": "
            + matched.getClass().getName()
            + " and "
            + other.getClass().getName()
            + ". Using the first registered. Rank one of them with priority(), or drop one from"
            + " the annotation processor path.",
        component);
  }
}
