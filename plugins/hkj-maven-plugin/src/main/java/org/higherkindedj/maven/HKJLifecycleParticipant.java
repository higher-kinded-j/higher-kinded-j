// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Maven lifecycle participant that configures HKJ dependencies, preview features, and compile-time
 * checks.
 *
 * <p>This extension runs early in the Maven build lifecycle (before dependency resolution),
 * allowing it to add dependencies and configure compiler settings automatically.
 *
 * <p>Activated when the {@code hkj-maven-plugin} is declared with {@code
 * <extensions>true</extensions>}.
 */
@Named("hkj")
@Singleton
public class HKJLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  /** Creates a new HKJLifecycleParticipant. */
  public HKJLifecycleParticipant() {}

  private static final String GROUP_ID = "io.github.higher-kinded-j";
  private static final String PLUGIN_KEY = GROUP_ID + ":hkj-maven-plugin";

  private static final String COMPILER_PLUGIN_KEY =
      "org.apache.maven.plugins:maven-compiler-plugin";
  private static final String SUREFIRE_PLUGIN_KEY =
      "org.apache.maven.plugins:maven-surefire-plugin";

  /** The processors HKJ's jars register, bundled at build time. */
  private static final List<String> CORE_PROCESSORS;

  private static final List<String> SPRING_PROCESSORS;

  static {
    Properties registered = loadRegisteredProcessors();
    CORE_PROCESSORS = registeredProcessors(registered, "core");
    SPRING_PROCESSORS = registeredProcessors(registered, "spring");
  }

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    List<MavenProject> wantSkills = new ArrayList<>();
    for (MavenProject project : session.getProjects()) {
      Optional<Plugin> hkjPlugin = findPlugin(project, PLUGIN_KEY);
      if (hkjPlugin.isEmpty()) {
        continue;
      }

      HKJConfiguration config;
      try {
        config = HKJConfiguration.fromPlugin(hkjPlugin.get());
      } catch (IllegalStateException e) {
        throw new MavenExecutionException(e.getMessage(), project.getFile());
      }
      configureDependencies(project, config);
      configureCompilerPlugin(project, config);
      configureSurefirePlugin(project, config);
      if (config.skills()) {
        wantSkills.add(project);
      }
    }
    skillsHome(wantSkills, session.getRequest().getMultiModuleProjectDirectory())
        .flatMap(home -> findPlugin(home, PLUGIN_KEY))
        .ifPresent(this::bindInstallSkills);
  }

  /**
   * The one project that installs the skills: the reactor's root, where Claude Code reads them, or
   * else the first project that asks for them. A module inheriting {@code <skills>} from its parent
   * would otherwise install its own copy.
   */
  static Optional<MavenProject> skillsHome(List<MavenProject> wantSkills, File root) {
    return wantSkills.stream()
        .filter(project -> root != null && root.equals(project.getBasedir()))
        .findFirst()
        .or(() -> wantSkills.stream().findFirst());
  }

  private static Optional<Plugin> findPlugin(MavenProject project, String pluginKey) {
    return project.getBuildPlugins().stream().filter(p -> pluginKey.equals(p.getKey())).findFirst();
  }

  private void configureDependencies(MavenProject project, HKJConfiguration config) {
    addDependency(project, "hkj-core", config.version(), "compile");
    addDependency(project, "hkj-processor-plugins", config.version(), "provided");

    if (config.pathTypeMismatch()) {
      addDependency(project, "hkj-checker", config.version(), "provided");
    }

    if (config.spring()) {
      addDependency(project, "hkj-spring-boot-starter", config.version(), "compile");
    }
  }

  private void addDependency(
      MavenProject project, String artifactId, String version, String scope) {
    // Check if already declared
    for (Dependency dep : project.getDependencies()) {
      if (GROUP_ID.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
        return;
      }
    }

    Dependency dep = new Dependency();
    dep.setGroupId(GROUP_ID);
    dep.setArtifactId(artifactId);
    dep.setVersion(version);
    dep.setScope(scope);
    project.getDependencies().add(dep);
  }

  void configureCompilerPlugin(MavenProject project, HKJConfiguration config) {
    // Only a compiler the build already has: a packaging that compiles nothing (pom) has none,
    // and one added here would carry no version.
    Optional<Plugin> found = findPlugin(project, COMPILER_PLUGIN_KEY);
    if (found.isEmpty()) {
      return;
    }
    Plugin compilerPlugin = found.get();

    // The plugin-level configuration serves a goal invoked directly (mvn compiler:compile). By
    // the time a lifecycle participant runs, Maven has already merged it into each execution,
    // and a lifecycle-bound execution (default-compile, default-testCompile) reads only its own,
    // so every execution is configured too.
    Xpp3Dom pluginNode = getOrCreateConfiguration(compilerPlugin);
    Optional<String> pluginRelease = childValue(pluginNode, "release");
    configureCompilerNode(pluginNode, project, config, /* ownRelease= */ false);
    for (PluginExecution execution : compilerPlugin.getExecutions()) {
      Xpp3Dom executionNode = getOrCreateConfiguration(execution);
      // A release that differs from the plugin's was set on the execution on purpose, as for a
      // multi-release jar, so that execution keeps its release and preview settings.
      Optional<String> executionRelease = childValue(executionNode, "release");
      boolean ownRelease = executionRelease.isPresent() && !executionRelease.equals(pluginRelease);
      configureCompilerNode(executionNode, project, config, ownRelease);
    }
  }

  private void configureCompilerNode(
      Xpp3Dom configNode, MavenProject project, HKJConfiguration config, boolean ownRelease) {
    // hkj-core is compiled for Java 25, and preview ties the release to the JDK's own; without
    // preview, a release the build already names is kept.
    if (!ownRelease) {
      if (config.preview()) {
        setChildValue(configNode, "release", "25");
        setChildValue(configNode, "enablePreview", "true");
      } else if (childValue(configNode, "release").isEmpty()
          && project.getProperties().getProperty("maven.compiler.release") == null) {
        setChildValue(configNode, "release", "25");
      }
    }
    // testCompile reads annotationProcessorPaths and compilerArgs too.
    addHkjProcessorPaths(configNode, "annotationProcessorPaths", config, /* create= */ true);
    addHkjCompilerArgs(configNode, "compilerArgs", config, /* create= */ true);
    // maven-compiler-plugin 4 reads <testCompilerArgs> in place of <compilerArgs> when it is set,
    // so HKJ joins an existing one; creating it would drop the user's <compilerArgs> from tests.
    addHkjCompilerArgs(configNode, "testCompilerArgs", config, /* create= */ false);
    addHkjProcessorNames(configNode, project, config);
  }

  private void addHkjProcessorNames(
      Xpp3Dom configNode, MavenProject project, HKJConfiguration config) {
    // <annotationProcessors> names the processors javac runs, and javac then discovers no other,
    // so HKJ's join a list the build already has. The plugin knows the processors of its own
    // release only, so it adds them only while the processors on the path are that release.
    Xpp3Dom names = configNode.getChild("annotationProcessors");
    if (names == null || !config.usesPluginRelease()) {
      return;
    }
    if (pathsAtRelease(
        project, configNode, config.version(), "hkj-processor", "hkj-processor-plugins")) {
      CORE_PROCESSORS.forEach(name -> addProcessorName(names, name));
    }
    if (config.spring()
        && pathsAtRelease(
            project, configNode, config.version(), "hkj-spring-boot-client-processor")) {
      SPRING_PROCESSORS.forEach(name -> addProcessorName(names, name));
    }
  }

  /** Whether every processor path naming one of {@code artifactIds} carries {@code version}. */
  /**
   * Whether the processors {@code artifactIds} name resolve to {@code version}. A processor path
   * without a version, and under {@code annotationProcessorPathsUseDepMgmt} every processor jar,
   * takes its version from the project's dependency management, as maven-compiler-plugin does; a
   * version that cannot be established counts as another release.
   */
  private static boolean pathsAtRelease(
      MavenProject project, Xpp3Dom configNode, String version, String... artifactIds) {
    List<String> ids = List.of(artifactIds);
    boolean useDepMgmt =
        childValue(configNode, "annotationProcessorPathsUseDepMgmt")
            .map(Boolean::parseBoolean)
            .orElse(false);
    if (useDepMgmt
        && ids.stream()
            .map(id -> managedVersion(project, GROUP_ID, id))
            .flatMap(Optional::stream)
            .anyMatch(managed -> !managed.equals(version))) {
      return false;
    }
    Xpp3Dom paths = configNode.getChild("annotationProcessorPaths");
    if (paths == null) {
      return true;
    }
    return Arrays.stream(paths.getChildren("path"))
        .filter(path -> childValue(path, "artifactId").filter(ids::contains).isPresent())
        .allMatch(
            path ->
                childValue(path, "version")
                    .or(() -> useDepMgmt ? managedVersion(project, path) : Optional.empty())
                    .map(version::equals)
                    .orElse(false));
  }

  private static Optional<String> managedVersion(MavenProject project, Xpp3Dom path) {
    return managedVersion(
        project,
        childValue(path, "groupId").orElse(GROUP_ID),
        childValue(path, "artifactId").orElse(""));
  }

  private static Optional<String> managedVersion(
      MavenProject project, String groupId, String artifactId) {
    return Optional.ofNullable(project.getDependencyManagement()).stream()
        .flatMap(management -> management.getDependencies().stream())
        .filter(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()))
        .map(Dependency::getVersion)
        .flatMap(v -> Optional.ofNullable(v).stream())
        .findFirst();
  }

  private static List<String> registeredProcessors(Properties registered, String group) {
    return Arrays.stream(registered.getProperty(group, "").split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .toList();
  }

  private static Properties loadRegisteredProcessors() {
    Properties properties = new Properties();
    try (InputStream in =
        HKJLifecycleParticipant.class.getResourceAsStream("/hkj-processors.properties")) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException _) {
      // unreadable: a named-processor list is left as the build wrote it
    }
    return properties;
  }

  private void addProcessorName(Xpp3Dom names, String name) {
    // Maven reads every child of <annotationProcessors>, whatever its element name.
    for (Xpp3Dom existing : names.getChildren()) {
      if (name.equals(existing.getValue())) {
        return;
      }
    }
    Xpp3Dom processor = new Xpp3Dom("annotationProcessor");
    processor.setValue(name);
    names.addChild(processor);
  }

  void bindInstallSkills(Plugin hkjPlugin) {
    // As the Gradle plugin's skills = true does, install the skills during every build.
    boolean bound =
        hkjPlugin.getExecutions().stream().anyMatch(e -> e.getGoals().contains("install-skills"));
    if (bound) {
      return;
    }
    PluginExecution execution = new PluginExecution();
    execution.setId("hkj-install-skills");
    execution.setPhase("generate-resources");
    execution.addGoal("install-skills");
    hkjPlugin.addExecution(execution);
  }

  private void addHkjProcessorPaths(
      Xpp3Dom configNode, String childName, HKJConfiguration config, boolean create) {
    Xpp3Dom paths =
        create ? getOrCreateChild(configNode, childName) : configNode.getChild(childName);
    if (paths == null) {
      return;
    }
    addAnnotationProcessorPath(paths, "hkj-processor-plugins", config.version());
    if (config.pathTypeMismatch()) {
      addAnnotationProcessorPath(paths, "hkj-checker", config.version());
    }
    // A dependency never adds to the processor path, so the starter cannot bring the processor
    // that generates @HkjHttpClient clients.
    if (config.spring()) {
      addAnnotationProcessorPath(paths, "hkj-spring-boot-client-processor", config.version());
    }
  }

  private void addHkjCompilerArgs(
      Xpp3Dom configNode, String childName, HKJConfiguration config, boolean create) {
    Xpp3Dom args =
        create ? getOrCreateChild(configNode, childName) : configNode.getChild(childName);
    if (args == null) {
      return;
    }
    // -parameters is unconditional (matching the Gradle plugin); the checker arg only
    // applies when the path-type-mismatch check is enabled.
    addArgIfMissing(args, "-parameters");
    if (config.pathTypeMismatch()) {
      addArgIfMissing(args, "-Xplugin:HKJChecker");
    }
  }

  void configureSurefirePlugin(MavenProject project, HKJConfiguration config) {
    if (!config.preview()) {
      return;
    }

    // Surefire reads the argLine property unless the build writes an <argLine> element, and
    // other plugins, such as JaCoCo's prepare-agent, extend that property. The flag joins the
    // property, so an element is never introduced to shadow it.
    Properties properties = project.getProperties();
    properties.setProperty("argLine", withEnablePreview(properties.getProperty("argLine")));

    // An <argLine> element the build writes itself replaces the property, in whichever
    // execution it reaches, so the flag joins it there too.
    findPlugin(project, SUREFIRE_PLUGIN_KEY)
        .ifPresent(
            surefire -> {
              appendToArgLine(surefire);
              surefire.getExecutions().forEach(this::appendToArgLine);
            });
  }

  private void appendToArgLine(ConfigurationContainer container) {
    if (container.getConfiguration() instanceof Xpp3Dom config
        && config.getChild("argLine") instanceof Xpp3Dom argLine) {
      argLine.setValue(withEnablePreview(argLine.getValue()));
    }
  }

  private static String withEnablePreview(String argLine) {
    if (argLine == null || argLine.isBlank()) {
      return "--enable-preview";
    }
    return argLine.contains("--enable-preview") ? argLine : argLine + " --enable-preview";
  }

  private Xpp3Dom getOrCreateConfiguration(ConfigurationContainer container) {
    if (container.getConfiguration() instanceof Xpp3Dom config) {
      return config;
    }
    Xpp3Dom config = new Xpp3Dom("configuration");
    container.setConfiguration(config);
    return config;
  }

  private Xpp3Dom getOrCreateChild(Xpp3Dom parent, String name) {
    Xpp3Dom child = parent.getChild(name);
    if (child == null) {
      child = new Xpp3Dom(name);
      parent.addChild(child);
    }
    return child;
  }

  private static Optional<String> childValue(Xpp3Dom parent, String name) {
    return Optional.ofNullable(parent.getChild(name))
        .map(Xpp3Dom::getValue)
        .map(String::trim)
        .filter(v -> !v.isEmpty());
  }

  private void setChildValue(Xpp3Dom parent, String name, String value) {
    Xpp3Dom child = getOrCreateChild(parent, name);
    child.setValue(value);
  }

  private void addAnnotationProcessorPath(Xpp3Dom parent, String artifactId, String version) {
    // Check if already present
    for (Xpp3Dom path : parent.getChildren("path")) {
      Xpp3Dom aid = path.getChild("artifactId");
      if (aid != null && artifactId.equals(aid.getValue())) {
        return;
      }
    }

    Xpp3Dom path = new Xpp3Dom("path");
    setChildValue(path, "groupId", GROUP_ID);
    setChildValue(path, "artifactId", artifactId);
    setChildValue(path, "version", version);
    parent.addChild(path);
  }

  private void addArgIfMissing(Xpp3Dom compilerArgs, String arg) {
    for (Xpp3Dom child : compilerArgs.getChildren("arg")) {
      if (arg.equals(child.getValue())) {
        return;
      }
    }
    Xpp3Dom argElement = new Xpp3Dom("arg");
    argElement.setValue(arg);
    compilerArgs.addChild(argElement);
  }
}
