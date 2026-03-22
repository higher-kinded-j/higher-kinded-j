// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;

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

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() == TaskEvent.Kind.ANALYZE) {
              var compilationUnit = event.getCompilationUnit();
              if (compilationUnit != null) {
                new PathTypeMismatchChecker(trees).scan(compilationUnit, null);
              }
            }
          }
        });
  }
}
