// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.File;
import java.util.List;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;

/** Maven sessions and projects for tests, as a reactor build sees them. */
final class Sessions {

  private Sessions() {}

  /**
   * A session over {@code projects}, whose multi-module project directory is {@code root}, or which
   * names none when it is null. The top-level project is the one marked as the execution root, or
   * else the first.
   */
  static MavenSession session(File root, MavenProject... projects) {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setMultiModuleProjectDirectory(root);
    MavenSession session =
        new MavenSession(
            null, new DefaultRepositorySystemSession(), request, new DefaultMavenExecutionResult());
    session.setProjects(List.of(projects));
    return session;
  }

  /** A project whose POM is in {@code dir}. */
  static MavenProject project(File dir) {
    MavenProject project = new MavenProject();
    project.setFile(new File(dir, "pom.xml"));
    project.setBuild(new Build());
    return project;
  }
}
