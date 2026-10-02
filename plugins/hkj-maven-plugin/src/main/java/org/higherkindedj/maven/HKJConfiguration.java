// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Reads and holds the HKJ Maven plugin configuration.
 *
 * <p>Configuration is read from the plugin's {@code <configuration>} block in the POM, and every
 * element is optional:
 *
 * <pre>{@code
 * <configuration>
 *     <version>0.4.11</version>
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

  static final String NO_VERSION_MESSAGE =
      "Could not determine the HKJ plugin version. Add <configuration><version>X</version>"
          + "</configuration> to the hkj-maven-plugin, where X is the Higher-Kinded-J release to"
          + " use.";

  private static final Optional<String> BUNDLED_VERSION = bundledVersion();

  HKJConfiguration {
    Objects.requireNonNull(version, "version");
    if (version.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
  }

  /**
   * Reads configuration from the plugin declaration, applying defaults for missing values.
   *
   * @throws IllegalStateException with {@link #NO_VERSION_MESSAGE} when no version can be found
   */
  static HKJConfiguration fromPlugin(Plugin plugin) {
    Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();

    String version =
        readString(config, "version")
            .orElseGet(() -> pluginVersion(BUNDLED_VERSION, nonBlank(plugin.getVersion())));
    boolean preview = readBoolean(config, "preview", true);
    boolean spring = readBoolean(config, "spring", false);
    boolean skills = readBoolean(config, "skills", false);
    boolean pathTypeMismatch = readBoolean(config, "pathTypeMismatch", true);

    return new HKJConfiguration(version, preview, spring, skills, pathTypeMismatch);
  }

  /**
   * The defaults, for a build that runs a goal without declaring the plugin.
   *
   * @throws IllegalStateException with {@link #NO_VERSION_MESSAGE} when no version is bundled
   */
  static HKJConfiguration defaults() {
    return new HKJConfiguration(
        pluginVersion(BUNDLED_VERSION, Optional.empty()), true, false, false, true);
  }

  /** Whether {@code version} is the release this plugin was built with. */
  static boolean isPluginRelease(String version) {
    return BUNDLED_VERSION.map(version::equals).orElse(false);
  }

  // Package-private for tests.
  static String pluginVersion(Optional<String> bundled, Optional<String> declared) {
    return bundled
        .or(() -> declared)
        .orElseThrow(() -> new IllegalStateException(NO_VERSION_MESSAGE));
  }

  private static Optional<String> bundledVersion() {
    try (InputStream in = HKJConfiguration.class.getResourceAsStream("/hkj-version.properties")) {
      if (in == null) {
        return Optional.empty();
      }
      Properties props = new Properties();
      props.load(in);
      return nonBlank(props.getProperty("version"));
    } catch (IOException e) {
      return Optional.empty(); // unreadable: fall back to the declared version
    }
  }

  private static Optional<String> nonBlank(String value) {
    return Optional.ofNullable(value).map(String::trim).filter(v -> !v.isEmpty());
  }

  private static Optional<String> readString(Xpp3Dom config, String name) {
    return Optional.ofNullable(config)
        .map(c -> c.getChild(name))
        .flatMap(child -> nonBlank(child.getValue()));
  }

  private static boolean readBoolean(Xpp3Dom config, String name, boolean defaultValue) {
    return readString(config, name).map(Boolean::parseBoolean).orElse(defaultValue);
  }
}
