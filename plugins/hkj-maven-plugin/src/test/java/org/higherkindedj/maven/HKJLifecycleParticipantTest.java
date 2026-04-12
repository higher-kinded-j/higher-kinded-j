// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJLifecycleParticipant.configureCompilerPlugin")
class HKJLifecycleParticipantTest {

  private static final String COMPILER_PLUGIN_KEY =
      "org.apache.maven.plugins:maven-compiler-plugin";

  private HKJLifecycleParticipant participant;
  private MavenProject project;
  private HKJConfiguration defaultConfig;

  @BeforeEach
  void setUp() {
    participant = new HKJLifecycleParticipant();
    project = new MavenProject();
    project.setBuild(new Build());
    defaultConfig = new HKJConfiguration("0.3.7", true, false, false, true);
  }

  private Plugin compilerPlugin() {
    for (Plugin plugin : project.getBuildPlugins()) {
      if (COMPILER_PLUGIN_KEY.equals(plugin.getKey())) {
        return plugin;
      }
    }
    throw new AssertionError("maven-compiler-plugin was not registered");
  }

  private Plugin addCompilerPlugin(Xpp3Dom config, PluginExecution... executions) {
    Plugin plugin = new Plugin();
    plugin.setGroupId("org.apache.maven.plugins");
    plugin.setArtifactId("maven-compiler-plugin");
    if (config != null) {
      plugin.setConfiguration(config);
    }
    for (PluginExecution execution : executions) {
      plugin.addExecution(execution);
    }
    project.getBuild().addPlugin(plugin);
    return plugin;
  }

  private static Xpp3Dom dom(String name) {
    return new Xpp3Dom(name);
  }

  private static Xpp3Dom path(String artifactId, String version) {
    Xpp3Dom path = dom("path");
    child(path, "groupId", "com.example");
    child(path, "artifactId", artifactId);
    child(path, "version", version);
    return path;
  }

  private static Xpp3Dom arg(String value) {
    Xpp3Dom arg = dom("arg");
    arg.setValue(value);
    return arg;
  }

  private static void child(Xpp3Dom parent, String name, String value) {
    Xpp3Dom c = dom(name);
    c.setValue(value);
    parent.addChild(c);
  }

  private static List<String> processorArtifactIds(Xpp3Dom parent, String childName) {
    Xpp3Dom child = parent.getChild(childName);
    if (child == null) {
      return List.of();
    }
    return java.util.Arrays.stream(child.getChildren("path"))
        .map(p -> p.getChild("artifactId").getValue())
        .toList();
  }

  private static List<String> argValues(Xpp3Dom parent, String childName) {
    Xpp3Dom child = parent.getChild(childName);
    if (child == null) {
      return List.of();
    }
    return java.util.Arrays.stream(child.getChildren("arg")).map(Xpp3Dom::getValue).toList();
  }

  @Nested
  @DisplayName("plugin-level configuration")
  class PluginLevel {

    @Test
    @DisplayName("creates annotationProcessorPaths and compilerArgs when absent")
    void createsDefaults_whenAbsent() {
      addCompilerPlugin(null);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom cfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      assertThat(processorArtifactIds(cfg, "annotationProcessorPaths"))
          .contains("hkj-processor-plugins", "hkj-checker");
      assertThat(argValues(cfg, "compilerArgs")).contains("-Xplugin:HKJChecker");
    }

    @Test
    @DisplayName(
        "appends HKJ entries to pre-existing testAnnotationProcessorPaths without removing"
            + " user processors")
    void patchesTestAnnotationProcessorPaths_whenUserDeclared() {
      // User has set testAnnotationProcessorPaths explicitly - this overrides the fallback to
      // annotationProcessorPaths for test compilation, so HKJ must land here too.
      Xpp3Dom cfg = dom("configuration");
      Xpp3Dom testPaths = dom("testAnnotationProcessorPaths");
      testPaths.addChild(path("user-test-processor", "1.0"));
      cfg.addChild(testPaths);
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom finalCfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      assertThat(processorArtifactIds(finalCfg, "testAnnotationProcessorPaths"))
          .containsExactlyInAnyOrder("user-test-processor", "hkj-processor-plugins", "hkj-checker");
    }

    @Test
    @DisplayName("appends -Xplugin:HKJChecker to pre-existing testCompilerArgs")
    void patchesTestCompilerArgs_whenUserDeclared() {
      Xpp3Dom cfg = dom("configuration");
      Xpp3Dom testArgs = dom("testCompilerArgs");
      testArgs.addChild(arg("--user-test-arg"));
      cfg.addChild(testArgs);
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom finalCfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      assertThat(argValues(finalCfg, "testCompilerArgs"))
          .containsExactlyInAnyOrder("--user-test-arg", "-Xplugin:HKJChecker");
    }

    @Test
    @DisplayName(
        "does NOT create testAnnotationProcessorPaths when user hasn't declared it (preserves"
            + " fallback)")
    void doesNotCreateTestAnnotationProcessorPaths_whenAbsent() {
      addCompilerPlugin(null);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom cfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      // If we created testAnnotationProcessorPaths with only HKJ entries, testCompile would
      // lose the fallback to annotationProcessorPaths and clobber any user main-side
      // processors. The plugin must not create this node.
      assertThat(cfg.getChild("testAnnotationProcessorPaths")).isNull();
      assertThat(cfg.getChild("testCompilerArgs")).isNull();
    }
  }

  @Nested
  @DisplayName("execution-level configuration")
  class ExecutionLevel {

    @Test
    @DisplayName(
        "appends HKJ entries to default-testCompile execution's testAnnotationProcessorPaths")
    void patchesExecutionTestAnnotationProcessorPaths() {
      Xpp3Dom execConfig = dom("configuration");
      Xpp3Dom testPaths = dom("testAnnotationProcessorPaths");
      testPaths.addChild(path("user-test-processor", "1.0"));
      execConfig.addChild(testPaths);

      PluginExecution execution = new PluginExecution();
      execution.setId("default-testCompile");
      execution.setConfiguration(execConfig);

      addCompilerPlugin(null, execution);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom finalExecConfig =
          (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration();
      assertThat(processorArtifactIds(finalExecConfig, "testAnnotationProcessorPaths"))
          .containsExactlyInAnyOrder("user-test-processor", "hkj-processor-plugins", "hkj-checker");
    }

    @Test
    @DisplayName("appends -Xplugin:HKJChecker to execution-level compilerArgs override")
    void patchesExecutionCompilerArgs() {
      Xpp3Dom execConfig = dom("configuration");
      Xpp3Dom args = dom("compilerArgs");
      args.addChild(arg("--user-arg"));
      execConfig.addChild(args);

      PluginExecution execution = new PluginExecution();
      execution.setId("default-compile");
      execution.setConfiguration(execConfig);

      addCompilerPlugin(null, execution);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom finalExecConfig =
          (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration();
      assertThat(argValues(finalExecConfig, "compilerArgs"))
          .containsExactlyInAnyOrder("--user-arg", "-Xplugin:HKJChecker");
    }

    @Test
    @DisplayName("skips executions with no configuration")
    void skipsExecutionsWithoutConfig() {
      PluginExecution execution = new PluginExecution();
      execution.setId("default-compile");
      // No configuration set.
      addCompilerPlugin(null, execution);

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(compilerPlugin().getExecutions().get(0).getConfiguration()).isNull();
    }
  }

  @Nested
  @DisplayName("checks disabled")
  class ChecksDisabled {

    private HKJConfiguration disabledConfig() {
      return new HKJConfiguration("0.3.7", true, false, false, false);
    }

    @Test
    @DisplayName("does not add hkj-checker or -Xplugin:HKJChecker anywhere")
    void doesNotAddChecker_whenDisabled() {
      Xpp3Dom cfg = dom("configuration");
      cfg.addChild(dom("testAnnotationProcessorPaths"));
      cfg.addChild(dom("testCompilerArgs"));
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, disabledConfig());

      Xpp3Dom finalCfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      assertThat(processorArtifactIds(finalCfg, "annotationProcessorPaths"))
          .contains("hkj-processor-plugins")
          .doesNotContain("hkj-checker");
      assertThat(processorArtifactIds(finalCfg, "testAnnotationProcessorPaths"))
          .contains("hkj-processor-plugins")
          .doesNotContain("hkj-checker");
      assertThat(argValues(finalCfg, "testCompilerArgs")).doesNotContain("-Xplugin:HKJChecker");
      assertThat(finalCfg.getChild("compilerArgs")).isNull();
    }
  }
}
