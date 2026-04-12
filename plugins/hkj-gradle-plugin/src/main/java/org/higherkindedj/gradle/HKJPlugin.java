// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

/**
 * Gradle plugin for Higher-Kinded-J projects.
 *
 * <p>This plugin configures HKJ dependencies, Java preview features, and compile-time Path type
 * mismatch checking. Apply it with:
 *
 * <pre>{@code
 * plugins {
 *     id("io.github.higher-kinded-j.hkj")
 * }
 * }</pre>
 *
 * <p>Configuration is available through the {@code hkj} extension block. See {@link HKJExtension}
 * for available options.
 */
public class HKJPlugin implements Plugin<Project> {

  /** Creates a new HKJ plugin instance. */
  public HKJPlugin() {}

  private static final String GROUP_ID = "io.github.higher-kinded-j";
  private static final String EXTENSION_NAME = "hkj";

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply("java");

    HKJExtension extension = project.getExtensions().create(EXTENSION_NAME, HKJExtension.class);

    // Set defaults - use the plugin's own published version, not the consuming project's version
    extension.getVersion().convention(readPluginVersion());
    extension.getPreview().convention(true);
    extension.getSpring().convention(false);
    extension.getSkills().convention(false);
    extension.getChecks().getPathTypeMismatch().convention(true);

    project.afterEvaluate(p -> configure(p, extension));

    registerInstallSkillsTask(project, extension);
    registerDiagnosticsTask(project, extension);
  }

  private void configure(Project project, HKJExtension extension) {
    String version = extension.getVersion().get();
    DependencyHandler deps = project.getDependencies();
    SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

    deps.add("implementation", GROUP_ID + ":hkj-core:" + version);

    // Register annotation processors on every source set's processor classpath so that
    // all JavaCompile tasks (compileJava, compileTestJava, and any custom source sets
    // such as integrationTest) can load -Xplugin:HKJChecker. Using sourceSets.all(...)
    // also wires up source sets added after the plugin is configured.
    sourceSets.all(
        sourceSet ->
            deps.add(
                sourceSet.getAnnotationProcessorConfigurationName(),
                GROUP_ID + ":hkj-processor-plugins:" + version));

    // Preview features
    if (Boolean.TRUE.equals(extension.getPreview().get())) {
      configurePreviewFeatures(project);
    }

    // Compile-time checks
    if (Boolean.TRUE.equals(extension.getChecks().getPathTypeMismatch().get())) {
      sourceSets.all(
          sourceSet ->
              deps.add(
                  sourceSet.getAnnotationProcessorConfigurationName(),
                  GROUP_ID + ":hkj-checker:" + version));
      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task -> {
                List<String> args = task.getOptions().getCompilerArgs();
                if (!args.contains("-Xplugin:HKJChecker")) {
                  args.add("-Xplugin:HKJChecker");
                }
                // The checker accesses jdk.compiler internals at compile time
                task.getOptions().setFork(true);
                task.getOptions()
                    .getForkOptions()
                    .getJvmArgs()
                    .addAll(
                        List.of(
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"));
              });
    }

    // Spring integration
    if (Boolean.TRUE.equals(extension.getSpring().get())) {
      deps.add("implementation", GROUP_ID + ":hkj-spring-boot-starter:" + version);
    }
  }

  private void configurePreviewFeatures(Project project) {
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            task -> {
              if (!task.getOptions().getCompilerArgs().contains("--enable-preview")) {
                task.getOptions().getCompilerArgs().add("--enable-preview");
              }
            });

    project
        .getTasks()
        .withType(Test.class)
        .configureEach(
            task -> {
              if (task.getJvmArgs() == null || !task.getJvmArgs().contains("--enable-preview")) {
                task.jvmArgs("--enable-preview");
              }
            });

    project
        .getTasks()
        .withType(JavaExec.class)
        .configureEach(
            task -> {
              if (task.getJvmArgs() == null || !task.getJvmArgs().contains("--enable-preview")) {
                task.jvmArgs("--enable-preview");
              }
            });

    project
        .getTasks()
        .withType(Javadoc.class)
        .configureEach(
            task -> {
              StandardJavadocDocletOptions options =
                  (StandardJavadocDocletOptions) task.getOptions();
              options.addBooleanOption("-enable-preview", true);
            });
  }

  /** Left-pads {@code label} with trailing spaces so it occupies at least {@code width} chars. */
  private static String padLabel(String label, int width) {
    if (label.length() >= width) {
      return label + " ";
    }
    StringBuilder sb = new StringBuilder(width + 1).append(label);
    while (sb.length() < width) {
      sb.append(' ');
    }
    sb.append(' ');
    return sb.toString();
  }

  private static String readPluginVersion() {
    Properties props = new Properties();
    try (InputStream in = HKJPlugin.class.getResourceAsStream("/hkj-version.properties")) {
      if (in != null) {
        props.load(in);
        String version = props.getProperty("version");
        if (version != null && !version.trim().isEmpty()) {
          return version.trim();
        }
      }
    } catch (IOException e) {
      // fall through
    }
    throw new IllegalStateException(
        "Could not determine HKJ plugin version. "
            + "Please set hkj.version explicitly in your build script.");
  }

  private void registerInstallSkillsTask(Project project, HKJExtension extension) {
    var installTask = project.getTasks().register("hkjInstallSkills", HKJInstallSkillsTask.class);

    // When skills = true, wire the install task into the build lifecycle
    project.afterEvaluate(
        p -> {
          if (Boolean.TRUE.equals(extension.getSkills().get())) {
            p.getTasks()
                .named("classes")
                .configure(classesTask -> classesTask.dependsOn(installTask));
          }
        });
  }

  private void registerDiagnosticsTask(Project project, HKJExtension extension) {
    project
        .getTasks()
        .register(
            "hkjDiagnostics",
            task -> {
              task.setGroup("help");
              task.setDescription("Prints the current HKJ plugin configuration");
              task.doLast(
                  t -> {
                    String version = extension.getVersion().get();
                    boolean preview = extension.getPreview().get();
                    boolean spring = extension.getSpring().get();
                    boolean skills = extension.getSkills().get();
                    boolean checks = extension.getChecks().getPathTypeMismatch().get();

                    SourceSetContainer sourceSets =
                        project.getExtensions().getByType(SourceSetContainer.class);

                    // Compute padding so labels align with the longest configuration name.
                    int labelWidth = "implementation:".length();
                    for (var sourceSet : sourceSets) {
                      labelWidth =
                          Math.max(
                              labelWidth,
                              sourceSet.getAnnotationProcessorConfigurationName().length() + 1);
                    }
                    final int width = labelWidth;

                    List<String> depsAdded = new ArrayList<>();
                    depsAdded.add(
                        padLabel("implementation:", width) + GROUP_ID + ":hkj-core:" + version);
                    sourceSets.forEach(
                        sourceSet -> {
                          String label = sourceSet.getAnnotationProcessorConfigurationName() + ":";
                          depsAdded.add(
                              padLabel(label, width)
                                  + GROUP_ID
                                  + ":hkj-processor-plugins:"
                                  + version);
                        });
                    if (checks) {
                      for (var sourceSet : sourceSets) {
                        String label = sourceSet.getAnnotationProcessorConfigurationName() + ":";
                        depsAdded.add(
                            padLabel(label, width) + GROUP_ID + ":hkj-checker:" + version);
                      }
                    }
                    if (spring) {
                      depsAdded.add(
                          padLabel("implementation:", width)
                              + GROUP_ID
                              + ":hkj-spring-boot-starter:"
                              + version);
                    }

                    List<String> compilerArgs = new ArrayList<>();
                    if (preview) {
                      compilerArgs.add("--enable-preview");
                    }
                    if (checks) {
                      compilerArgs.add("-Xplugin:HKJChecker");
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("HKJ Configuration:\n");
                    sb.append("  Version:            ").append(version).append("\n");
                    sb.append("  Preview features:   ")
                        .append(preview ? "enabled" : "disabled")
                        .append("\n");
                    sb.append("  Spring integration: ")
                        .append(spring ? "enabled" : "disabled")
                        .append("\n");
                    sb.append("  Claude Code skills: ")
                        .append(skills ? "enabled" : "disabled")
                        .append("\n");
                    sb.append("  Compile-time checks:\n");
                    sb.append("    Path type mismatch: ")
                        .append(checks ? "enabled" : "disabled")
                        .append("\n");
                    sb.append("  Dependencies added:\n");
                    for (String dep : depsAdded) {
                      sb.append("    ").append(dep).append("\n");
                    }
                    if (!compilerArgs.isEmpty()) {
                      sb.append("  Compiler args added:\n");
                      for (String arg : compilerArgs) {
                        sb.append("    ").append(arg).append("\n");
                      }
                    }

                    project.getLogger().lifecycle(sb.toString().stripTrailing());
                  });
            });
  }
}
