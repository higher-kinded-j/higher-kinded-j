// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;

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
 * <p>See {@link CheckerConfig} for the supported directives and check ids.
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
    javax.lang.model.util.Types types = task.getTypes();
    javax.lang.model.util.Elements elements = task.getElements();
    CheckerConfig config = CheckerConfig.parse(args);

    // Build the enabled checks once, in stable dispatch order. Each check reports at
    // config.severityFor(id): an explicit severity:<id>=… override wins; the warn-default
    // checks (error-type-mismatch, map-nests-effect, migration-nudge) otherwise stay
    // WARNING; everything else uses the global severity.
    List<CheckVisitor> checks = new ArrayList<>();
    if (config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)) {
      checks.add(
          new PathTypeMismatchChecker(trees, config.severityFor(CheckerConfig.PATH_TYPE_MISMATCH)));
    }
    if (config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)) {
      checks.add(
          new EffectCompositionChecker(
              trees, config.severityFor(CheckerConfig.EFFECT_COMPOSITION)));
    }
    if (config.isEnabled(CheckerConfig.TRANSFORMER_MISSING_MONAD)) {
      checks.add(
          new TransformerMissingMonadChecker(
              trees, config.severityFor(CheckerConfig.TRANSFORMER_MISSING_MONAD)));
    }
    if (config.isEnabled(CheckerConfig.FREE_SWITCH_EXHAUSTIVE)) {
      checks.add(
          new FreeSwitchExhaustivenessChecker(
              trees, config.severityFor(CheckerConfig.FREE_SWITCH_EXHAUSTIVE)));
    }
    if (config.isEnabled(CheckerConfig.DISCARDED_EFFECT)) {
      checks.add(
          new DiscardedEffectChecker(
              trees, types, elements, config.severityFor(CheckerConfig.DISCARDED_EFFECT)));
    }
    if (config.isEnabled(CheckerConfig.STATE_T_MAPT_ARITY)) {
      checks.add(
          new StateTMapTArityChecker(trees, config.severityFor(CheckerConfig.STATE_T_MAPT_ARITY)));
    }
    if (config.isEnabled(CheckerConfig.ERROR_TYPE_MISMATCH)) {
      checks.add(
          new ErrorTypeMismatchChecker(
              trees, types, config.severityFor(CheckerConfig.ERROR_TYPE_MISMATCH)));
    }
    if (config.isEnabled(CheckerConfig.KIND_VALUE_NARROW)) {
      checks.add(
          new KindValueNarrowChecker(trees, config.severityFor(CheckerConfig.KIND_VALUE_NARROW)));
    }
    if (config.isEnabled(CheckerConfig.WITNESS_ARITY)) {
      checks.add(
          new WitnessArityChecker(
              trees, types, elements, config.severityFor(CheckerConfig.WITNESS_ARITY)));
    }
    if (config.isEnabled(CheckerConfig.VIA_NON_PATH)) {
      checks.add(
          new ViaNonPathChecker(
              trees, types, elements, config.severityFor(CheckerConfig.VIA_NON_PATH)));
    }
    if (config.isEnabled(CheckerConfig.MAP_NESTS_EFFECT)) {
      checks.add(
          new MapReturnsPathChecker(
              trees, types, elements, config.severityFor(CheckerConfig.MAP_NESTS_EFFECT)));
    }
    if (config.isEnabled(CheckerConfig.MIGRATION_NUDGE)) {
      checks.add(
          new MigrationNudgeChecker(trees, config.severityFor(CheckerConfig.MIGRATION_NUDGE)));
    }

    if (checks.isEmpty()) {
      return; // nothing enabled: no listener, no traversal
    }
    HkjCheckScanner scanner = new HkjCheckScanner(checks);

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() == TaskEvent.Kind.ANALYZE) {
              var compilationUnit = event.getCompilationUnit();
              if (compilationUnit != null) {
                scanner.scan(compilationUnit, null);
              }
            }
          }
        });
  }
}
