// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Task that installs HKJ Claude Code skills into {@link #getOutputDir()}, the {@code
 * .claude/skills/} directory at the root of the build unless the build sets another.
 *
 * <p>Skills are bundled as classpath resources in the plugin JAR under {@code
 * META-INF/hkj-skills/}. This task reads the manifest listing all skill files and writes each one
 * there whose content differs, then a stamp, {@value InstalledSkills#STAMP_FILE}, naming the
 * release they describe. It logs a line only when it changes something.
 *
 * <p>The plugin registers one of these in every project that applies it, each writing to the same
 * directory. Gradle never runs two tasks with the same output at once, so in a multi-project build
 * the first brings the skills up to date and the others find nothing to do.
 *
 * <p>Gradle tracks no state for the task: it always runs, and compares each file itself. Another
 * project's install writes the same files, so state Gradle tracked for this task would miss an edit
 * made since, and the comparison costs little.
 */
@UntrackedTask(
    because =
        "Every project's install writes to .claude/skills at the root of the build, and each"
            + " compares the files itself")
public abstract class HKJInstallSkillsTask extends DefaultTask {

  private static final String MANIFEST_RESOURCE = "/META-INF/hkj-skills/manifest.txt";
  private static final String SKILLS_RESOURCE_PREFIX = "/META-INF/hkj-skills/";

  /** Creates a new install skills task. */
  public HKJInstallSkillsTask() {
    setGroup("hkj");
    setDescription("Installs HKJ Claude Code skills into .claude/skills/ at the root of the build");
    getOutputDir().convention(getLayout().getSettingsDirectory().dir(".claude/skills"));
  }

  /**
   * Returns the directory the skills are installed into. Unless the build sets it, this is {@code
   * .claude/skills} at the root of the build, the directory holding its settings file, whichever
   * project the task belongs to.
   *
   * @return the directory the skills are installed into
   */
  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  /**
   * Returns the layout of the project the task belongs to, which Gradle injects. The task reads the
   * build's root directory from it, since the configuration cache forbids reaching the project
   * while a task runs.
   *
   * @return the project's layout
   */
  @Inject
  protected abstract ProjectLayout getLayout();

  /**
   * Writes each skill file from the plugin's classpath resources that differs from the one in the
   * output directory, and stamps them with the plugin's release.
   */
  @TaskAction
  public void install() {
    Path targetDir = getOutputDir().get().getAsFile().toPath();
    String release = HKJPlugin.readPluginVersion();

    try (InputStream manifestStream =
        HKJInstallSkillsTask.class.getResourceAsStream(MANIFEST_RESOURCE)) {
      if (manifestStream == null) {
        throw new GradleException(
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
              HKJInstallSkillsTask.class.getResourceAsStream(resourcePath)) {
            if (skillStream == null) {
              getLogger().warn("Skill resource not found: {}; skipping", resourcePath);
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
      if (changed > 0) {
        getLogger()
            .lifecycle(
                "Installed {} HKJ Claude Code skill {} for Higher-Kinded-J {} into {}",
                changed,
                changed == 1 ? "file" : "files",
                release,
                targetDir);
      } else if (stamped) {
        getLogger()
            .lifecycle(
                "Stamped the HKJ Claude Code skills in {} with Higher-Kinded-J {}",
                targetDir,
                release);
      } else {
        getLogger()
            .info(
                "The HKJ Claude Code skills in {} are up to date with Higher-Kinded-J {}",
                targetDir,
                release);
        setDidWork(false);
      }

    } catch (IOException e) {
      throw new GradleException("Failed to install HKJ skills: " + e.getMessage(), e);
    }
  }
}
