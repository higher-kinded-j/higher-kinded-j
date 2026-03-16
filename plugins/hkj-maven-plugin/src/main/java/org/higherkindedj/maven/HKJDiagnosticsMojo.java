// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Prints the current HKJ configuration for diagnostics and troubleshooting.
 *
 * <p>Usage: {@code mvn hkj:diagnostics}
 */
@Mojo(name = "diagnostics", requiresProject = true)
public class HKJDiagnosticsMojo extends AbstractMojo {

  /** Creates a new HKJDiagnosticsMojo. */
  public HKJDiagnosticsMojo() {}

  private static final String GROUP_ID = "io.github.higher-kinded-j";
  private static final String PLUGIN_KEY = GROUP_ID + ":hkj-maven-plugin";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Override
  public void execute() {
    Plugin hkjPlugin = findHKJPlugin();
    HKJConfiguration config;
    if (hkjPlugin != null) {
      config = HKJConfiguration.fromPlugin(hkjPlugin, project);
    } else {
      config = new HKJConfiguration(project.getVersion(), true, false, true);
    }

    List<String> depsAdded = new ArrayList<>();
    for (Dependency dep : project.getDependencies()) {
      if (GROUP_ID.equals(dep.getGroupId())) {
        depsAdded.add(
            dep.getScope()
                + ": "
                + dep.getGroupId()
                + ":"
                + dep.getArtifactId()
                + ":"
                + dep.getVersion());
      }
    }

    List<String> compilerArgs = new ArrayList<>();
    if (config.preview()) {
      compilerArgs.add("--enable-preview");
    }
    if (config.pathTypeMismatch()) {
      compilerArgs.add("-Xplugin:HKJChecker");
    }

    StringBuilder sb = new StringBuilder();
    sb.append("HKJ Configuration:\n");
    sb.append("  Version:            ").append(config.version()).append("\n");
    sb.append("  Preview features:   ")
        .append(config.preview() ? "enabled" : "disabled")
        .append("\n");
    sb.append("  Spring integration: ")
        .append(config.spring() ? "enabled" : "disabled")
        .append("\n");
    sb.append("  Compile-time checks:\n");
    sb.append("    Path type mismatch: ")
        .append(config.pathTypeMismatch() ? "enabled" : "disabled")
        .append("\n");

    if (!depsAdded.isEmpty()) {
      sb.append("  Dependencies found:\n");
      for (String dep : depsAdded) {
        sb.append("    ").append(dep).append("\n");
      }
    }

    if (!compilerArgs.isEmpty()) {
      sb.append("  Compiler args configured:\n");
      for (String arg : compilerArgs) {
        sb.append("    ").append(arg).append("\n");
      }
    }

    getLog().info(sb.toString().stripTrailing());
  }

  private Plugin findHKJPlugin() {
    for (Plugin plugin : project.getBuildPlugins()) {
      if (PLUGIN_KEY.equals(plugin.getKey())) {
        return plugin;
      }
    }
    return null;
  }
}
