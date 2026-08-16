// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * A javac compiler plugin that detects Path type mismatches at compile time.
 *
 * <p>This plugin hooks into javac's {@code ANALYZE} phase (after type attribution) and runs the
 * {@link PathTypeMismatchChecker} over each compilation unit. It reports errors when different
 * concrete Path types are mixed in chain operations like {@code via()}, {@code zipWith()}, and
 * {@code recoverWith()}.
 *
 * <h2>Usage</h2>
 *
 * <p>Add the checker jar to the annotation processor path and enable the plugin:
 *
 * <pre>{@code
 * // build.gradle.kts
 * dependencies {
 *     annotationProcessor("io.github.higher-kinded-j:hkj-checker:VERSION")
 * }
 * tasks.withType<JavaCompile>().configureEach {
 *     options.compilerArgs.add("-Xplugin:HKJChecker")
 * }
 * }</pre>
 *
 * <p>Or use the HKJ Gradle plugin which configures this automatically.
 *
 * <h2>Configuration</h2>
 *
 * <p>Individual checks can be disabled and the diagnostic severity tuned via plugin arguments:
 *
 * <pre>{@code
 * -Xplugin:HKJChecker disable=effect-composition severity=warn
 * }</pre>
 *
 * <p>See {@link CheckerConfig} for the supported directives and check ids. A single declaration
 * opts out with {@code @SuppressWarnings("<check-id>")}, or out of every check with
 * {@code @SuppressWarnings(}{@value CheckerConfig#SUPPRESS_ALL}{@code )}, and generated types are
 * not checked at all; see {@link HkjCheckScanner} for both.
 *
 * <h2>Registration</h2>
 *
 * <p>This plugin is registered via {@code META-INF/services/com.sun.source.util.Plugin} and via the
 * {@code module-info.java} provides clause.
 */
public class HKJCheckerPlugin implements Plugin {

  /** Creates a new HKJChecker plugin instance. */
  public HKJCheckerPlugin() {}

  /** The plugin name used with {@code -Xplugin:HKJChecker}. */
  public static final String PLUGIN_NAME = "HKJChecker";

  @Override
  public String getName() {
    return PLUGIN_NAME;
  }

  @Override
  public void init(JavacTask task, String... args) {
    Trees trees = Trees.instance(task);
    Types types = task.getTypes();
    Elements elements = task.getElements();
    CheckerConfig config = CheckerConfig.parse(args);

    // Build the enabled checks once, in stable dispatch order. Each check reports at
    // config.severityFor(id): an explicit severity:<id>=… override wins; the warn-default
    // checks (error-type-mismatch, map-nests-effect, migration-nudge) otherwise stay
    // WARNING; everything else uses the global severity. The id travels with the check so
    // the scanner can honour a @SuppressWarnings naming it.
    List<HkjCheckScanner.Check> checks = new ArrayList<>();
    BiConsumer<String, Function<Diagnostic.Kind, CheckVisitor>> enable =
        (id, checker) -> {
          if (config.isEnabled(id)) {
            checks.add(new HkjCheckScanner.Check(id, checker.apply(config.severityFor(id))));
          }
        };

    enable.accept(
        CheckerConfig.PATH_TYPE_MISMATCH, severity -> new PathTypeMismatchChecker(trees, severity));
    enable.accept(
        CheckerConfig.EFFECT_COMPOSITION,
        severity -> new EffectCompositionChecker(trees, severity));
    enable.accept(
        CheckerConfig.TRANSFORMER_MISSING_MONAD,
        severity -> new TransformerMissingMonadChecker(trees, severity));
    enable.accept(
        CheckerConfig.FREE_SWITCH_EXHAUSTIVE,
        severity -> new FreeSwitchExhaustivenessChecker(trees, severity));
    enable.accept(
        CheckerConfig.DISCARDED_EFFECT,
        severity -> new DiscardedEffectChecker(trees, types, elements, severity));
    enable.accept(
        CheckerConfig.STATE_T_MAPT_ARITY, severity -> new StateTMapTArityChecker(trees, severity));
    enable.accept(
        CheckerConfig.ERROR_TYPE_MISMATCH,
        severity -> new ErrorTypeMismatchChecker(trees, types, severity));
    enable.accept(
        CheckerConfig.KIND_VALUE_NARROW, severity -> new KindValueNarrowChecker(trees, severity));
    enable.accept(
        CheckerConfig.WITNESS_ARITY,
        severity -> new WitnessArityChecker(trees, types, elements, severity));
    enable.accept(CheckerConfig.RAW_KIND, severity -> new RawKindChecker(trees, severity));
    enable.accept(
        CheckerConfig.VIA_NON_PATH,
        severity -> new ViaNonPathChecker(trees, types, elements, severity));
    enable.accept(
        CheckerConfig.MAP_NESTS_EFFECT,
        severity -> new MapReturnsPathChecker(trees, types, elements, severity));
    enable.accept(
        CheckerConfig.MIGRATION_NUDGE, severity -> new MigrationNudgeChecker(trees, severity));

    if (checks.isEmpty()) {
      return; // nothing enabled: no listener, no traversal
    }
    HkjCheckScanner scanner = new HkjCheckScanner(trees, checks);

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() != TaskEvent.Kind.ANALYZE) {
              return;
            }
            // javac fires one ANALYZE event per top-level type, each carrying the whole
            // compilation unit. Scanning the unit would walk every sibling type once per
            // event, reporting a type as many times as the file has top-level types and
            // reaching types whose own event was skipped; scanning the analysed type's
            // subtree visits each declaration exactly once.
            TreePath analysed = pathOf(event);
            if (analysed != null) {
              scanner.scan(analysed, HkjCheckScanner.nothingSuppressed());
            }
          }

          /**
           * The subtree an event covers: the analysed type, or the whole unit when javac attaches
           * no type element (package-info, module-info) so the checks stay on by default.
           */
          private TreePath pathOf(TaskEvent event) {
            TypeElement analysed = event.getTypeElement();
            TreePath path = analysed == null ? null : trees.getPath(analysed);
            if (path != null) {
              return path;
            }
            var unit = event.getCompilationUnit();
            return unit == null ? null : new TreePath(unit);
          }
        });
  }
}
