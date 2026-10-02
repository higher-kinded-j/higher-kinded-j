// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * A goal of the HKJ plugin. It accepts the plugin's shared {@code <configuration>} elements, so
 * that Maven does not report them as unknown when the goal runs; {@link HKJConfiguration} reads
 * their values from the POM.
 */
abstract class AbstractHKJMojo extends AbstractMojo {

  @Parameter private String version;

  @Parameter private boolean preview;

  @Parameter private boolean spring;

  @Parameter private boolean skills;

  @Parameter private boolean pathTypeMismatch;
}
