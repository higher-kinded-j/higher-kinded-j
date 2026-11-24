// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
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
 * <p>The three share one discovery of the generators per compilation, through {@link #load}, which
 * reports a plugin that cannot be loaded and loads the rest.
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
   * The generators each compilation has loaded, by the loader they were found on, keyed weakly by
   * its {@link Elements}, as {@code WaitingSpecs} keys the specs it has met: the one utility a
   * build tool hands every processor unwrapped. The processors that load the generators share what
   * the first of them loaded, so a broken plugin is found, and reported, once.
   *
   * <p>Each generator is held weakly too, and kept alive by the processors that loaded it, so a
   * finished compilation is never kept reachable, whatever a plugin holds on to.
   */
  private static final Map<Elements, Map<ClassLoader, List<Reference<TraversableGenerator>>>>
      LOADED = new WeakHashMap<>();

  /**
   * The generators {@link ServiceLoader} finds on {@code classLoader}, in registration order,
   * loaded once per compilation: each processor that asks after the first is handed the same
   * generators, so one instance of each serves every record, round and processor. The cache holds
   * them weakly, so it is the processors that keep them for the compilation.
   *
   * <p>A provider that cannot be loaded is reported as an error, naming the provider where the
   * JVM's report does, and the rest are loaded. Without that, one broken plugin on the processor
   * path stops every compilation with an uncaught exception, whatever it compiles. The likeliest is
   * a plugin built against an earlier release that extends a built-in generator and overrides a
   * form of {@code generateModifyF} the built-in declares final, which the JVM refuses to link.
   * {@link ServiceLoader} moves past a provider that failed, whatever the failure, and past a
   * service file it cannot read, but not past a loader that cannot list the service files, which
   * fails the same way on every call. So a run of {@value #READ_FAILURES_IN_A_ROW} failures to read
   * the service files, with nothing else between them, ends the loading, and only the first of a
   * run is reported.
   *
   * @param elements the compilation's element utilities, which key what it has loaded
   * @param classLoader the loader to discover {@link TraversableGenerator} services with
   * @param messager the messager a provider that cannot be loaded is reported through
   * @return the generators loaded
   * @throws NullPointerException if any argument is null
   */
  static List<TraversableGenerator> load(
      Elements elements, ClassLoader classLoader, Messager messager) {
    Objects.requireNonNull(elements, "elements");
    Objects.requireNonNull(classLoader, "classLoader");
    Objects.requireNonNull(messager, "messager");
    Optional<List<TraversableGenerator>> cached;
    synchronized (LOADED) {
      cached =
          Optional.ofNullable(LOADED.get(elements))
              .map(byLoader -> byLoader.get(classLoader))
              // Stream.toList keeps a cleared reference's null, which the check that follows
              // refuses.
              .map(held -> held.stream().map(Reference::get).toList())
              .filter(generators -> generators.stream().allMatch(Objects::nonNull));
    }
    return cached.orElseGet(
        () -> {
          // Discovered outside the lock, so a plugin's constructor never runs holding it.
          List<TraversableGenerator> discovered = discover(classLoader, messager);
          synchronized (LOADED) {
            LOADED
                .computeIfAbsent(elements, key -> new HashMap<>())
                .put(
                    classLoader,
                    discovered.stream()
                        .<Reference<TraversableGenerator>>map(WeakReference::new)
                        .toList());
          }
          return discovered;
        });
  }

  /**
   * How many failures to read the service files, with nothing else between them, end the loading.
   */
  static final int READ_FAILURES_IN_A_ROW = 64;

  /** Every generator on {@code classLoader} that loads, each failure reported once. */
  private static List<TraversableGenerator> discover(ClassLoader classLoader, Messager messager) {
    List<TraversableGenerator> loaded = new ArrayList<>();
    Set<String> reported = new HashSet<>();
    // A provider is found, reading the service files and loading its class, as the stream advances,
    // and created by get: a constructor's own IOException reaches get as the cause a file that
    // cannot be read gives, so where it is thrown tells the two apart.
    Iterator<ServiceLoader.Provider<TraversableGenerator>> providers =
        ServiceLoader.load(TraversableGenerator.class, classLoader).stream().iterator();
    int readFailuresInARow = 0;
    while (readFailuresInARow < READ_FAILURES_IN_A_ROW) {
      ServiceLoader.Provider<TraversableGenerator> provider;
      try {
        if (!providers.hasNext()) {
          return List.copyOf(loaded);
        }
        provider = providers.next();
      } catch (ServiceConfigurationError | LinkageError failure) {
        // Only a failure to read the service files can repeat with the loader not moving on, so
        // only those count, and only the first of a run is reported, whatever its cause's text.
        boolean readFailure = cannotReadServiceFiles(failure);
        boolean firstOfItsRun = !readFailure || readFailuresInARow == 0;
        readFailuresInARow = readFailure ? readFailuresInARow + 1 : 0;
        if (firstOfItsRun) {
          report(failure, readFailure, reported, messager);
        }
        continue;
      }
      readFailuresInARow = 0;
      try {
        loaded.add(provider.get());
      } catch (ServiceConfigurationError | LinkageError failure) {
        report(failure, false, reported, messager);
      }
    }
    return List.copyOf(loaded);
  }

  /** Reports a plugin that cannot be loaded, unless an earlier failure was reported alike. */
  private static void report(
      Throwable failure, boolean readFailure, Set<String> reported, Messager messager) {
    String report = jvmReport(failure);
    // Two plugins can fail alike, as two extending the same missing class do: one error.
    if (reported.add(report)) {
      messager.printMessage(Diagnostic.Kind.ERROR, unloadable(failure, readFailure, report));
    }
  }

  /**
   * Whether ServiceLoader, finding a provider, could not list or read the service files, as an I/O
   * failure.
   */
  private static boolean cannotReadServiceFiles(Throwable failure) {
    return failure instanceof ServiceConfigurationError
        && failure.getCause() instanceof IOException;
  }

  /**
   * The error for a plugin that cannot be loaded: what, why and the fix, then the JVM's own report,
   * last, as the JVM cuts a long one short.
   */
  private static String unloadable(Throwable failure, boolean readFailure, String report) {
    Unloadable reason = Unloadable.of(failure, readFailure);
    return "A TraversableGenerator plugin on the annotation processor path cannot be loaded. "
        + reason.why
        + " "
        + reason.fix
        + " The JVM reported: "
        + report;
  }

  /** Why a plugin cannot be loaded, as the error words it, and the fix for each. */
  private enum Unloadable {
    DOES_NOT_LINK(
        "It does not link against this hkj-processor, as a plugin built against an earlier one"
            + " does where it extends a built-in generator and overrides a form of generateModifyF"
            + " the built-in declares final.",
        "Update the plugin to a release built for this hkj-processor, or remove it from the"
            + " annotation processor path. To fix the plugin itself, override the generateModifyF"
            + " form that takes a GenerationContext instead."),
    UNREADABLE(
        "The annotation processor path's service files could not be read.",
        "A jar there may be damaged: replace it with a fresh copy, or remove it from the"
            + " annotation processor path."),
    FAILED(
        "A service file names a class badly, or a class it names is missing, does not link or is"
            + " not a TraversableGenerator, or its constructor or static initialiser threw.",
        "Add the jar holding any class the plugin is missing to the annotation processor path,"
            + " update the plugin to a release built for this hkj-processor, or remove it from the"
            + " annotation processor path.");

    private final String why;
    private final String fix;

    Unloadable(String why, String fix) {
      this.why = why;
      this.fix = fix;
    }

    static Unloadable of(Throwable failure, boolean readFailure) {
      if (failure instanceof IncompatibleClassChangeError) {
        return DOES_NOT_LINK;
      }
      return readFailure ? UNREADABLE : FAILED;
    }
  }

  /**
   * The JVM's report of a failure, with the deepest of its causes where it has one, which is where
   * the reason usually is: a plugin's static initialiser fails inside an {@code
   * ExceptionInInitializerError} inside a {@link ServiceConfigurationError}. A chain of causes can
   * loop back on itself, so each is followed once, as {@link Throwable#printStackTrace()} does.
   * Where the plugin's own code threw, the report ends with the frame it threw from.
   */
  static String jvmReport(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    seen.add(failure);
    Throwable root = failure;
    while (root.getCause() != null && seen.add(root.getCause())) {
      root = root.getCause();
    }
    String report = root == failure ? failure.toString() : failure + ", caused by " + root;
    return report + thrownFrom(root).map(frame -> ", at " + frame).orElse("");
  }

  /** The packages whose frames are the JDK's, not a plugin's. */
  private static final List<String> JDK_PACKAGES = List.of("java.", "jdk.", "sun.", "com.sun.");

  /**
   * The frame a plugin's own code threw from: the first above the loader's frames that is not the
   * JDK's. A class that is missing or does not link has none, as the JVM raised it, not the plugin.
   */
  private static Optional<StackTraceElement> thrownFrom(Throwable root) {
    if (root instanceof LinkageError || root instanceof ReflectiveOperationException) {
      return Optional.empty();
    }
    return Arrays.stream(root.getStackTrace())
        .takeWhile(frame -> !frame.getClassName().startsWith(ServiceLoader.class.getName()))
        .filter(frame -> JDK_PACKAGES.stream().noneMatch(frame.getClassName()::startsWith))
        .findFirst();
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
