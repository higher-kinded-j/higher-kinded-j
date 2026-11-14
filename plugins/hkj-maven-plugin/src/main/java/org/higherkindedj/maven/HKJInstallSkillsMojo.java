// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Installs HKJ Claude Code skills into {@code .claude/skills/} at the root of the build, once per
 * build.
 *
 * <p>Skills are bundled as classpath resources in the plugin JAR under {@code
 * META-INF/hkj-skills/}. This goal reads the manifest listing all skill files and writes each one
 * there whose content differs, then a stamp, {@value InstalledSkills#STAMP_FILE}, naming the
 * release they describe. It logs a line only when it changes something. A reactor runs the goal in
 * each of its projects, and only the first installs, so a multi-module build holds one copy.
 *
 * <p>Usage: {@code mvn hkj:install-skills}
 */
@Mojo(name = "install-skills", requiresProject = true)
public class HKJInstallSkillsMojo extends AbstractHKJMojo {

  /** Creates a new HKJInstallSkillsMojo. */
  public HKJInstallSkillsMojo() {}

  private static final String MANIFEST_RESOURCE = "/META-INF/hkj-skills/manifest.txt";
  private static final String SKILLS_RESOURCE_PREFIX = "/META-INF/hkj-skills/";

  /** The key, in the session's data, under which a build records each directory it installed. */
  private static final String INSTALLED_KEY = HKJInstallSkillsMojo.class.getName() + ".installed:";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Override
  public void execute() throws MojoExecutionException {
    readConfiguration(project);
    String release =
        HKJConfiguration.bundledRelease()
            .orElseThrow(
                () ->
                    new MojoExecutionException(
                        "HKJ plugin release not found in plugin resources, so the skills cannot"
                            + " be stamped. The plugin may have been built without it."));
    Path targetDir = InstalledSkills.directory(session, project.getBasedir());
    if (!claim(targetDir)) {
      getLog().debug("HKJ Claude Code skills already installed into " + targetDir);
      return;
    }

    boolean installed = false;
    try (InputStream manifestStream =
        HKJInstallSkillsMojo.class.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (manifestStream == null) {
        throw new MojoExecutionException(
            "HKJ skills manifest not found in plugin resources. "
                + "The plugin may have been built without skills bundled.");
      }

      int changed = 0;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(manifestStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String relativePath = line.trim();
          if (relativePath.isEmpty()) {
            continue;
          }

          String resourcePath = SKILLS_RESOURCE_PREFIX + relativePath;
          try (InputStream skillStream =
              HKJInstallSkillsMojo.class.getResourceAsStream(resourcePath)) {
            if (skillStream == null) {
              getLog().warn("Skill resource not found: " + resourcePath + "; skipping");
              continue;
            }

            if (InstalledSkills.writeIfChanged(
                targetDir.resolve(relativePath), skillStream.readAllBytes())) {
              changed++;
            }
          }
        }
      }

      boolean stamped =
          InstalledSkills.writeIfChanged(
              targetDir.resolve(InstalledSkills.STAMP_FILE),
              InstalledSkills.stamp(release).getBytes(StandardCharsets.UTF_8));
      installed = true;
      if (changed > 0) {
        getLog()
            .info(
                "Installed %d HKJ Claude Code skill %s for Higher-Kinded-J %s into %s"
                    .formatted(changed, changed == 1 ? "file" : "files", release, targetDir));
      } else if (stamped) {
        getLog()
            .info(
                "Stamped the HKJ Claude Code skills in %s with Higher-Kinded-J %s"
                    .formatted(targetDir, release));
      } else {
        getLog()
            .debug(
                "The HKJ Claude Code skills in %s are up to date with Higher-Kinded-J %s"
                    .formatted(targetDir, release));
      }

    } catch (IOException e) {
      throw new MojoExecutionException("Failed to install HKJ skills: " + e.getMessage(), e);
    } finally {
      if (!installed) {
        unclaim(targetDir);
      }
    }
  }

  /**
   * Claims the build's install into {@code targetDir}, and says whether this project holds it. The
   * session's data is shared by every project of the reactor, and setting it is atomic, so of the
   * projects that run the goal, in turn or in parallel, one installs.
   */
  private boolean claim(Path targetDir) {
    return session.getRepositorySession().getData().set(INSTALLED_KEY + targetDir, null, true);
  }

  /** Gives up a failed install's claim, so the next project that runs the goal installs. */
  private void unclaim(Path targetDir) {
    session.getRepositorySession().getData().set(INSTALLED_KEY + targetDir, true, null);
  }
}
