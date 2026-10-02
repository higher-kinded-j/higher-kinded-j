// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
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

@DisplayName("HKJLifecycleParticipant")
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

  private Xpp3Dom pluginConfig() {
    return (Xpp3Dom) compilerPlugin().getConfiguration();
  }

  private static PluginExecution execution(String id) {
    PluginExecution execution = new PluginExecution();
    execution.setId(id);
    return execution;
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
    return Arrays.stream(child.getChildren("path"))
        .map(p -> p.getChild("artifactId").getValue())
        .toList();
  }

  private static List<String> argValues(Xpp3Dom parent, String childName) {
    Xpp3Dom child = parent.getChild(childName);
    if (child == null) {
      return List.of();
    }
    return Arrays.stream(child.getChildren("arg")).map(Xpp3Dom::getValue).toList();
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
      assertThat(argValues(cfg, "compilerArgs")).contains("-Xplugin:HKJChecker", "-parameters");
    }

    @Test
    @DisplayName("leaves testAnnotationProcessorPaths alone, since no released compiler reads it")
    void leavesTestAnnotationProcessorPaths() {
      Xpp3Dom cfg = dom("configuration");
      Xpp3Dom testPaths = dom("testAnnotationProcessorPaths");
      testPaths.addChild(path("user-test-processor", "1.0"));
      cfg.addChild(testPaths);
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(processorArtifactIds(pluginConfig(), "testAnnotationProcessorPaths"))
          .containsExactly("user-test-processor");
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
          .containsExactlyInAnyOrder("--user-test-arg", "-Xplugin:HKJChecker", "-parameters");
    }

    @Test
    @DisplayName("does not create testCompilerArgs, which would replace compilerArgs for tests")
    void doesNotCreateTestCompilerArgs_whenAbsent() {
      addCompilerPlugin(null);

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(pluginConfig().getChild("testCompilerArgs")).isNull();
      assertThat(pluginConfig().getChild("testAnnotationProcessorPaths")).isNull();
    }
  }

  @Nested
  @DisplayName("execution-level configuration")
  class ExecutionLevel {

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
          .containsExactlyInAnyOrder("--user-arg", "-Xplugin:HKJChecker", "-parameters");
    }

    @Test
    @DisplayName("configures lifecycle executions that carry no configuration of their own")
    void configuresExecutionsWithoutConfig() {
      // Maven's lifecycle injection gives default-compile and default-testCompile no
      // configuration, and they then read nothing from the plugin level.
      addCompilerPlugin(null, execution("default-compile"), execution("default-testCompile"));

      participant.configureCompilerPlugin(project, defaultConfig);

      for (PluginExecution execution : compilerPlugin().getExecutions()) {
        Xpp3Dom cfg = (Xpp3Dom) execution.getConfiguration();
        assertThat(cfg).as(execution.getId()).isNotNull();
        assertThat(cfg.getChild("release").getValue()).isEqualTo("25");
        assertThat(cfg.getChild("enablePreview").getValue()).isEqualTo("true");
        assertThat(processorArtifactIds(cfg, "annotationProcessorPaths"))
            .containsExactlyInAnyOrder("hkj-processor-plugins", "hkj-checker");
        assertThat(argValues(cfg, "compilerArgs"))
            .containsExactlyInAnyOrder("-parameters", "-Xplugin:HKJChecker");
      }
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
      cfg.addChild(dom("testCompilerArgs"));
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, disabledConfig());

      Xpp3Dom finalCfg = (Xpp3Dom) compilerPlugin().getConfiguration();
      assertThat(processorArtifactIds(finalCfg, "annotationProcessorPaths"))
          .contains("hkj-processor-plugins")
          .doesNotContain("hkj-checker");
      assertThat(argValues(finalCfg, "testCompilerArgs"))
          .contains("-parameters")
          .doesNotContain("-Xplugin:HKJChecker");
      assertThat(argValues(finalCfg, "compilerArgs"))
          .contains("-parameters")
          .doesNotContain("-Xplugin:HKJChecker");
    }
  }

  @Nested
  @DisplayName("spring integration")
  class SpringIntegration {

    private HKJConfiguration springConfig() {
      return new HKJConfiguration("0.3.7", true, true, false, true);
    }

    @Test
    @DisplayName("adds the @HkjHttpClient processor to the compile and testCompile executions")
    void addsClientProcessor_whenSpringEnabled() {
      addCompilerPlugin(null, execution("default-compile"), execution("default-testCompile"));

      participant.configureCompilerPlugin(project, springConfig());

      assertThat(processorArtifactIds(pluginConfig(), "annotationProcessorPaths"))
          .containsExactlyInAnyOrder(
              "hkj-processor-plugins", "hkj-checker", "hkj-spring-boot-client-processor");
      for (PluginExecution execution : compilerPlugin().getExecutions()) {
        assertThat(
                processorArtifactIds(
                    (Xpp3Dom) execution.getConfiguration(), "annotationProcessorPaths"))
            .as(execution.getId())
            .containsExactlyInAnyOrder(
                "hkj-processor-plugins", "hkj-checker", "hkj-spring-boot-client-processor");
      }
    }

    @Test
    @DisplayName("keeps a processor path the user already declares, without a duplicate")
    void keepsUserClientProcessor_withoutDuplicate() {
      Xpp3Dom cfg = dom("configuration");
      Xpp3Dom paths = dom("annotationProcessorPaths");
      Xpp3Dom userPath = dom("path");
      child(userPath, "groupId", "io.github.higher-kinded-j");
      child(userPath, "artifactId", "hkj-spring-boot-client-processor");
      child(userPath, "version", "0.4.7");
      paths.addChild(userPath);
      cfg.addChild(paths);
      addCompilerPlugin(cfg);

      participant.configureCompilerPlugin(project, springConfig());

      assertThat(processorArtifactIds(pluginConfig(), "annotationProcessorPaths"))
          .containsExactlyInAnyOrder(
              "hkj-spring-boot-client-processor", "hkj-processor-plugins", "hkj-checker");
      assertThat(
              pluginConfig()
                  .getChild("annotationProcessorPaths")
                  .getChildren("path")[0]
                  .getChild("version")
                  .getValue())
          .isEqualTo("0.4.7");
    }

    @Test
    @DisplayName("adds no @HkjHttpClient processor when spring is disabled")
    void addsNoClientProcessor_whenSpringDisabled() {
      addCompilerPlugin(null, execution("default-compile"));

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(processorArtifactIds(pluginConfig(), "annotationProcessorPaths"))
          .doesNotContain("hkj-spring-boot-client-processor");
      assertThat(
              processorArtifactIds(
                  (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration(),
                  "annotationProcessorPaths"))
          .doesNotContain("hkj-spring-boot-client-processor");
    }
  }

  @Nested
  @DisplayName("named processors")
  class NamedProcessors {

    private static final String LENS = "org.higherkindedj.optics.processing.LensProcessor";
    private static final String COMPANION =
        "org.higherkindedj.optics.processing.CompanionAnnotationProcessor";
    private static final String CLIENT =
        "org.higherkindedj.spring.client.processor.HkjHttpClientProcessor";

    private List<String> processorNames(Xpp3Dom cfg) {
      return Arrays.stream(cfg.getChild("annotationProcessors").getChildren("annotationProcessor"))
          .map(Xpp3Dom::getValue)
          .toList();
    }

    private Xpp3Dom namingConfig(String... names) {
      Xpp3Dom cfg = dom("configuration");
      Xpp3Dom processors = dom("annotationProcessors");
      for (String name : names) {
        child(processors, "annotationProcessor", name);
      }
      cfg.addChild(processors);
      return cfg;
    }

    private final String release = System.getProperty("hkj.expectedVersion");

    @Test
    @DisplayName("adds HKJ's processors to a build that names its processors")
    void addsHkjProcessors_toNamedList() {
      addCompilerPlugin(namingConfig("lombok.launch.AnnotationProcessorHider$AnnotationProcessor"));

      participant.configureCompilerPlugin(
          project, new HKJConfiguration(release, true, false, false, true));

      assertThat(processorNames(pluginConfig()))
          .startsWith("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
          .contains(LENS, COMPANION)
          .doesNotContain(CLIENT)
          .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("adds the @HkjHttpClient processors too when spring is enabled")
    void addsClientProcessors_whenSpringEnabled() {
      addCompilerPlugin(namingConfig(LENS));

      participant.configureCompilerPlugin(
          project, new HKJConfiguration(release, true, true, false, true));

      assertThat(processorNames(pluginConfig()))
          .contains(
              LENS,
              CLIENT,
              "org.higherkindedj.spring.client.processor.CompanionAnnotationProcessor")
          .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("leaves a named list alone when the build pins another release")
    void leavesNamedList_whenAnotherReleasePinned() {
      addCompilerPlugin(namingConfig(LENS));

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(processorNames(pluginConfig())).containsExactly(LENS);
    }

    @Test
    @DisplayName("names no processor when the build names none, so javac discovers them")
    void namesNothing_whenBuildNamesNone() {
      addCompilerPlugin(null);

      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(pluginConfig().getChild("annotationProcessors")).isNull();
    }
  }

  @Nested
  @DisplayName("skills")
  class Skills {

    private Plugin hkjPlugin() {
      Plugin plugin = new Plugin();
      plugin.setGroupId("io.github.higher-kinded-j");
      plugin.setArtifactId("hkj-maven-plugin");
      return plugin;
    }

    @Test
    @DisplayName("binds install-skills into the build")
    void bindsInstallSkills() {
      Plugin plugin = hkjPlugin();

      participant.bindInstallSkills(plugin);
      participant.bindInstallSkills(plugin);

      assertThat(plugin.getExecutions())
          .singleElement()
          .satisfies(
              execution -> {
                assertThat(execution.getGoals()).containsExactly("install-skills");
                assertThat(execution.getPhase()).isEqualTo("generate-resources");
              });
    }

    @Test
    @DisplayName("keeps an install-skills execution the build declares")
    void keepsDeclaredExecution() {
      Plugin plugin = hkjPlugin();
      PluginExecution declared = execution("my-skills");
      declared.addGoal("install-skills");
      declared.setPhase("validate");
      plugin.addExecution(declared);

      participant.bindInstallSkills(plugin);

      assertThat(plugin.getExecutions()).containsExactly(declared);
    }
  }

  @Nested
  @DisplayName("release")
  class Release {

    @Test
    @DisplayName("sets release 25 without preview when the build names no release")
    void setsRelease_whenPreviewOffAndNoneNamed() {
      addCompilerPlugin(null, execution("default-compile"));

      participant.configureCompilerPlugin(
          project, new HKJConfiguration("0.3.7", false, false, false, true));

      Xpp3Dom executionConfig =
          (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration();
      assertThat(executionConfig.getChild("release").getValue()).isEqualTo("25");
      assertThat(executionConfig.getChild("enablePreview")).isNull();
    }

    @Test
    @DisplayName("keeps the build's own release when preview is off")
    void keepsNamedRelease_whenPreviewOff() {
      project.getProperties().setProperty("maven.compiler.release", "26");
      addCompilerPlugin(null, execution("default-compile"));

      participant.configureCompilerPlugin(
          project, new HKJConfiguration("0.3.7", false, false, false, true));

      Xpp3Dom executionConfig =
          (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration();
      assertThat(executionConfig.getChild("release")).isNull();
    }

    @Test
    @DisplayName("leaves an execution's own release, and its preview, alone")
    void keepsExecutionsOwnRelease() {
      Xpp3Dom multiRelease = dom("configuration");
      child(multiRelease, "release", "17");
      PluginExecution java17 = execution("java17");
      java17.setConfiguration(multiRelease);
      addCompilerPlugin(null, execution("default-compile"), java17);

      participant.configureCompilerPlugin(project, defaultConfig);

      Xpp3Dom java17Config = (Xpp3Dom) compilerPlugin().getExecutions().get(1).getConfiguration();
      assertThat(java17Config.getChild("release").getValue()).isEqualTo("17");
      assertThat(java17Config.getChild("enablePreview")).isNull();
      assertThat(processorArtifactIds(java17Config, "annotationProcessorPaths"))
          .contains("hkj-processor-plugins");
      Xpp3Dom defaultCompile = (Xpp3Dom) compilerPlugin().getExecutions().get(0).getConfiguration();
      assertThat(defaultCompile.getChild("release").getValue()).isEqualTo("25");
    }

    @Test
    @DisplayName("adds no compiler to a project that has none, such as a pom")
    void addsNoCompiler_whenAbsent() {
      participant.configureCompilerPlugin(project, defaultConfig);

      assertThat(project.getBuildPlugins()).isEmpty();
    }
  }

  @Nested
  @DisplayName("surefire")
  class Surefire {

    private Plugin addSurefire(Xpp3Dom config, PluginExecution... executions) {
      Plugin surefire = new Plugin();
      surefire.setGroupId("org.apache.maven.plugins");
      surefire.setArtifactId("maven-surefire-plugin");
      if (config != null) {
        surefire.setConfiguration(config);
      }
      for (PluginExecution execution : executions) {
        surefire.addExecution(execution);
      }
      project.getBuild().addPlugin(surefire);
      return surefire;
    }

    @Test
    @DisplayName("adds --enable-preview to the argLine property, writing no element")
    void addsEnablePreview_toArgLineProperty() {
      Plugin surefire = addSurefire(null, execution("default-test"));

      participant.configureSurefirePlugin(project, defaultConfig);

      assertThat(project.getProperties().getProperty("argLine")).isEqualTo("--enable-preview");
      assertThat(surefire.getConfiguration()).isNull();
      assertThat(surefire.getExecutions().get(0).getConfiguration()).isNull();
    }

    @Test
    @DisplayName("keeps what another plugin put in the argLine property, such as JaCoCo's agent")
    void keepsArgLinePropertyValue() {
      project.getProperties().setProperty("argLine", "-javaagent:jacoco.jar");

      participant.configureSurefirePlugin(project, defaultConfig);

      assertThat(project.getProperties().getProperty("argLine"))
          .isEqualTo("-javaagent:jacoco.jar --enable-preview");
    }

    @Test
    @DisplayName("adds --enable-preview once to an <argLine> the build writes")
    void appendsToExplicitArgLine_once() {
      Xpp3Dom cfg = dom("configuration");
      child(cfg, "argLine", "-Xmx1g");
      Plugin surefire = addSurefire(cfg);

      participant.configureSurefirePlugin(project, defaultConfig);
      participant.configureSurefirePlugin(project, defaultConfig);

      assertThat(((Xpp3Dom) surefire.getConfiguration()).getChild("argLine").getValue())
          .isEqualTo("-Xmx1g --enable-preview");
      assertThat(project.getProperties().getProperty("argLine")).isEqualTo("--enable-preview");
    }

    @Test
    @DisplayName("leaves the argLine alone when preview is off")
    void leavesArgLine_whenPreviewOff() {
      participant.configureSurefirePlugin(
          project, new HKJConfiguration("0.3.7", false, false, false, true));

      assertThat(project.getProperties().getProperty("argLine")).isNull();
    }
  }
}
