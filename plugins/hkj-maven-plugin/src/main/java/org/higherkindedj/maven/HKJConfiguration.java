// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Reads and holds the HKJ Maven plugin configuration.
 *
 * <p>Configuration is read from the plugin's {@code <configuration>} block in the POM:
 *
 * <pre>{@code
 * <configuration>
 *     <version>0.3.7-SNAPSHOT</version>
 *     <preview>true</preview>
 *     <spring>false</spring>
 *     <skills>false</skills>
 *     <pathTypeMismatch>true</pathTypeMismatch>
 * </configuration>
 * }</pre>
 */
record HKJConfiguration(
    String version, boolean preview, boolean spring, boolean skills, boolean pathTypeMismatch) {

  /** Reads configuration from the plugin declaration, applying defaults for missing values. */
  static HKJConfiguration fromPlugin(Plugin plugin, MavenProject project) {
    Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();

    String version = readString(config, "version", project.getVersion());
    boolean preview = readBoolean(config, "preview", true);
    boolean spring = readBoolean(config, "spring", false);
    boolean skills = readBoolean(config, "skills", false);
    boolean pathTypeMismatch = readBoolean(config, "pathTypeMismatch", true);

    return new HKJConfiguration(version, preview, spring, skills, pathTypeMismatch);
  }

  private static String readString(Xpp3Dom config, String name, String defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Xpp3Dom child = config.getChild(name);
    if (child == null || child.getValue() == null || child.getValue().isBlank()) {
      return defaultValue;
    }
    return child.getValue().trim();
  }

  private static boolean readBoolean(Xpp3Dom config, String name, boolean defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Xpp3Dom child = config.getChild(name);
    if (child == null || child.getValue() == null || child.getValue().isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(child.getValue().trim());
  }
}
