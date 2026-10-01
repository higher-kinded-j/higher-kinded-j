// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

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
      Plugin hkjPlugin = findHKJPlugin(project);
      if (hkjPlugin == null) {
        continue;
      }

      HKJConfiguration config;
      try {
        config = HKJConfiguration.fromPlugin(hkjPlugin);
      } catch (IllegalStateException e) {
        throw new MavenExecutionException(e.getMessage(), project.getFile());
      }
      configureDependencies(project, config);
      configureCompilerPlugin(project, config);
      configureSurefirePlugin(project, config);
    }
  }

  private Plugin findHKJPlugin(MavenProject project) {
    for (Plugin plugin : project.getBuildPlugins()) {
      if (PLUGIN_KEY.equals(plugin.getKey())) {
        return plugin;
      }
    }
    return null;
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
    Plugin compilerPlugin = findOrCreatePlugin(project, COMPILER_PLUGIN_KEY);

    // The plugin-level configuration serves a goal invoked directly (mvn compiler:compile). By
    // the time a lifecycle participant runs, Maven has already merged it into each execution,
    // and a lifecycle-bound execution (default-compile, default-testCompile) reads only its own,
    // so every execution is configured too.
    configureCompilerNode(getOrCreateConfiguration(compilerPlugin), config);
    for (PluginExecution execution : compilerPlugin.getExecutions()) {
      configureCompilerNode(getOrCreateConfiguration(execution), config);
    }
  }

  private void configureCompilerNode(Xpp3Dom configNode, HKJConfiguration config) {
    if (config.preview()) {
      setChildValue(configNode, "release", "25");
      setChildValue(configNode, "enablePreview", "true");
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

    // As for the compiler, default-test reads only its own execution's configuration.
    Plugin surefirePlugin = findOrCreatePlugin(project, SUREFIRE_PLUGIN_KEY);
    addEnablePreviewArg(getOrCreateConfiguration(surefirePlugin));
    for (PluginExecution execution : surefirePlugin.getExecutions()) {
      addEnablePreviewArg(getOrCreateConfiguration(execution));
    }
  }

  private void addEnablePreviewArg(Xpp3Dom configNode) {
    Xpp3Dom argLine = getOrCreateChild(configNode, "argLine");
    if (argLine.getValue() == null || !argLine.getValue().contains("--enable-preview")) {
      String existing = argLine.getValue() != null ? argLine.getValue() + " " : "";
      argLine.setValue(existing + "--enable-preview");
    }
  }

  private Plugin findOrCreatePlugin(MavenProject project, String pluginKey) {
    String[] parts = pluginKey.split(":");
    String groupId = parts[0];
    String artifactId = parts[1];

    for (Plugin plugin : project.getBuildPlugins()) {
      if (pluginKey.equals(plugin.getKey())) {
        return plugin;
      }
    }

    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    project.getBuild().addPlugin(plugin);
    return plugin;
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
