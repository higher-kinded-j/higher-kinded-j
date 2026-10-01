// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.maven.model.Plugin;
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
 *
 * <p>Without a {@code <version>}, the library version is the plugin's own: the version bundled in
 * the plugin jar, or failing that the version the POM declares for the plugin. The consuming
 * project's version is never used, since it names a different artifact.
 */
record HKJConfiguration(
    String version, boolean preview, boolean spring, boolean skills, boolean pathTypeMismatch) {

  /** Reads configuration from the plugin declaration, applying defaults for missing values. */
  static HKJConfiguration fromPlugin(Plugin plugin) {
    Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();

    String version = readString(config, "version", null);
    if (version == null) {
      version = pluginVersion(plugin);
    }
    boolean preview = readBoolean(config, "preview", true);
    boolean spring = readBoolean(config, "spring", false);
    boolean skills = readBoolean(config, "skills", false);
    boolean pathTypeMismatch = readBoolean(config, "pathTypeMismatch", true);

    return new HKJConfiguration(version, preview, spring, skills, pathTypeMismatch);
  }

  /** The plugin's own version: bundled in its jar, else as the POM declares the plugin. */
  static String pluginVersion(Plugin plugin) {
    return pluginVersion(bundledVersion(), plugin);
  }

  static String pluginVersion(String bundled, Plugin plugin) {
    if (bundled != null) {
      return bundled;
    }
    String declared = plugin.getVersion();
    if (declared != null && !declared.isBlank()) {
      return declared.trim();
    }
    throw new IllegalStateException(
        "Could not determine the HKJ plugin version. "
            + "Set <version> in the hkj-maven-plugin <configuration>.");
  }

  private static String bundledVersion() {
    try (InputStream in = HKJConfiguration.class.getResourceAsStream("/hkj-version.properties")) {
      if (in == null) {
        return null;
      }
      Properties props = new Properties();
      props.load(in);
      String version = props.getProperty("version");
      return version == null || version.isBlank() ? null : version.trim();
    } catch (IOException e) {
      return null;
    }
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
