// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * A goal of the HKJ plugin, holding the plugin's shared {@code <configuration>} elements.
 *
 * <p>{@code META-INF/maven/plugin.xml} declares them for every goal, so Maven does not report them
 * as unknown, and injects them here. {@link HKJConfiguration} reads the values from the plugin's
 * own {@code <configuration>}, as the lifecycle participant does, so a value set on one execution
 * alone is not read; {@link #warnAboutExecutionSettings} says so.
 */
abstract class AbstractHKJMojo extends AbstractMojo {

  @Parameter private String version;

  @Parameter private Boolean preview;

  @Parameter private Boolean spring;

  @Parameter private Boolean skills;

  @Parameter private Boolean pathTypeMismatch;

  /** Warns about each setting this goal was given that differs from the one the plugin reads. */
  void warnAboutExecutionSettings(HKJConfiguration config) {
    warnIfDiffers("version", version, config.version());
    warnIfDiffers("preview", preview, config.preview());
    warnIfDiffers("spring", spring, config.spring());
    warnIfDiffers("skills", skills, config.skills());
    warnIfDiffers("pathTypeMismatch", pathTypeMismatch, config.pathTypeMismatch());
  }

  private void warnIfDiffers(String name, Object given, Object read) {
    if (given != null && !given.equals(read)) {
      String message =
          "<%s>%s</%s> on this execution is not read: the HKJ plugin reads its own"
              + " <configuration>, where %s is %s. Move the setting there.";
      getLog().warn(message.formatted(name, given, name, name, read));
    }
  }
}
