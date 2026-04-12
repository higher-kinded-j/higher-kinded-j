// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Installs HKJ Claude Code skills into the project's {@code .claude/skills/} directory.
 *
 * <p>Skills are bundled as classpath resources in the plugin JAR under {@code
 * META-INF/hkj-skills/}. This goal reads the manifest listing all skill files and copies them to
 * the project directory.
 *
 * <p>Usage: {@code mvn hkj:install-skills}
 */
@Mojo(name = "install-skills", requiresProject = true)
public class HKJInstallSkillsMojo extends AbstractMojo {

  /** Creates a new HKJInstallSkillsMojo. */
  public HKJInstallSkillsMojo() {}

  private static final String MANIFEST_RESOURCE = "/META-INF/hkj-skills/manifest.txt";
  private static final String SKILLS_RESOURCE_PREFIX = "/META-INF/hkj-skills/";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException {
    Path targetDir = project.getBasedir().toPath().resolve(".claude/skills");

    try (InputStream manifestStream =
        HKJInstallSkillsMojo.class.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (manifestStream == null) {
        throw new MojoExecutionException(
            "HKJ skills manifest not found in plugin resources. "
                + "The plugin may have been built without skills bundled.");
      }

      int installed = 0;
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
              getLog().warn("Skill resource not found: " + resourcePath + " — skipping");
              continue;
            }

            Path targetFile = targetDir.resolve(relativePath);
            Files.createDirectories(targetFile.getParent());
            Files.copy(skillStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            installed++;
          }
        }
      }

      getLog().info("Installed " + installed + " HKJ Claude Code skill files into .claude/skills/");

    } catch (IOException e) {
      throw new MojoExecutionException("Failed to install HKJ skills: " + e.getMessage(), e);
    }
  }
}
