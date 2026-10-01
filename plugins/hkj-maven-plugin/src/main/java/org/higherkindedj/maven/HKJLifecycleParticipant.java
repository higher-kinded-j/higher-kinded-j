// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

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

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
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
    }
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
    // testCompile reads annotationProcessorPaths and compilerArgs too, unless the test-specific
    // overrides below are set.
    addHkjProcessorPaths(configNode, "annotationProcessorPaths", config, /* create= */ true);
    addHkjCompilerArgs(configNode, "compilerArgs", config, /* create= */ true);
    // When <testAnnotationProcessorPaths> / <testCompilerArgs> are set, the testCompile goal
    // reads from them exclusively, so HKJ must be added there too.
    applyTestOverrides(configNode, config);
  }

  private void applyTestOverrides(Xpp3Dom configNode, HKJConfiguration config) {
    // Only patch test overrides that already exist - creating them blindly would replace
    // the fallback to annotationProcessorPaths / compilerArgs and risk clobbering any
    // user-defined test-only processors or args.
    addHkjProcessorPaths(configNode, "testAnnotationProcessorPaths", config, /* create= */ false);
    addHkjCompilerArgs(configNode, "testCompilerArgs", config, /* create= */ false);
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
