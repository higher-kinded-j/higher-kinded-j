// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("HKJInstallSkillsMojo")
class HKJInstallSkillsMojoTest {

  private static final String RELEASE = HKJConfiguration.bundledRelease().orElseThrow();

  private final List<String> infos = new ArrayList<>();

  private Log capturing() {
    return new SystemStreamLog() {
      @Override
      public void info(CharSequence content) {
        infos.add(content.toString());
      }

      @Override
      public boolean isDebugEnabled() {
        return false;
      }
    };
  }

  /** The goal as Maven runs it in {@code project}'s build. */
  static HKJInstallSkillsMojo goal(MavenProject project, MavenSession session, Log log)
      throws ReflectiveOperationException {
    HKJInstallSkillsMojo goal = new HKJInstallSkillsMojo();
    goal.setLog(log);
    inject(goal, "project", project);
    inject(goal, "session", session);
    return goal;
  }

  private static void inject(HKJInstallSkillsMojo goal, String parameter, Object value)
      throws ReflectiveOperationException {
    Field field = HKJInstallSkillsMojo.class.getDeclaredField(parameter);
    field.setAccessible(true);
    field.set(goal, value);
  }

  @Test
  @DisplayName("a reactor installs the skills once, at the root of the build, with the stamp")
  void reactorInstallsOnce_atTheRoot(@TempDir Path repo) throws Exception {
    MavenProject root = Sessions.project(repo.toFile());
    root.setExecutionRoot(true);
    MavenProject module = Sessions.project(repo.resolve("module").toFile());
    MavenSession session = Sessions.session(repo.toFile(), root, module);
    // A copy an earlier release installed into the module is left as it is
    Path moduleCopy = repo.resolve("module/.claude/skills/hkj-guide/SKILL.md");
    Files.createDirectories(moduleCopy.getParent());
    Files.writeString(moduleCopy, "old");

    // In a reactor the goal runs in every project, the module here first
    goal(module, session, capturing()).execute();
    goal(root, session, capturing()).execute();

    Path skills = repo.resolve(".claude/skills");
    assertThat(skills.resolve("hkj-guide/SKILL.md")).isRegularFile();
    assertThat(skills.resolve(InstalledSkills.STAMP_FILE)).content().contains("version=" + RELEASE);
    assertThat(infos)
        .singleElement()
        .asString()
        .contains("skill files for Higher-Kinded-J " + RELEASE + " into " + skills);
    assertThat(moduleCopy).hasContent("old");
    assertThat(repo.resolve("module/.claude/skills").resolve(InstalledSkills.STAMP_FILE))
        .doesNotExist();
  }

  @Test
  @DisplayName("each build writes only what differs, and says nothing when nothing does")
  void eachBuildWritesOnlyWhatDiffers(@TempDir Path repo) throws Exception {
    MavenProject root = Sessions.project(repo.toFile());
    Path skills = repo.resolve(".claude/skills");
    Path guide = skills.resolve("hkj-guide/SKILL.md");
    Path stamp = skills.resolve(InstalledSkills.STAMP_FILE);

    goal(root, Sessions.session(repo.toFile(), root), capturing()).execute();
    assertThat(infos).singleElement().asString().startsWith("Installed ");

    // Only the stamp differs: the build writes it alone
    infos.clear();
    Files.writeString(stamp, "version=0.0.1\n");
    goal(root, Sessions.session(repo.toFile(), root), capturing()).execute();
    assertThat(infos)
        .containsExactly(
            "Stamped the HKJ Claude Code skills in %s with Higher-Kinded-J %s"
                .formatted(skills, RELEASE));
    assertThat(stamp).content().contains("version=" + RELEASE);

    // One skill differs: the build writes that one back
    infos.clear();
    String bundled = Files.readString(guide);
    Files.writeString(guide, "edited");
    goal(root, Sessions.session(repo.toFile(), root), capturing()).execute();
    assertThat(infos).singleElement().asString().startsWith("Installed 1 HKJ Claude Code skill");
    assertThat(guide).hasContent(bundled);

    // Nothing differs: the build says nothing
    infos.clear();
    goal(root, Sessions.session(repo.toFile(), root), capturing()).execute();
    assertThat(infos).isEmpty();
  }

  @Test
  @DisplayName("a failed install leaves the directory to the next project that runs the goal")
  void failedInstall_leavesTheDirectoryToTheNextProject(@TempDir Path repo) throws Exception {
    MavenProject root = Sessions.project(repo.toFile());
    MavenProject module = Sessions.project(repo.resolve("module").toFile());
    MavenSession session = Sessions.session(repo.toFile(), root, module);
    // A file where the skills directory belongs makes the first install fail
    Path skills = repo.resolve(".claude/skills");
    Files.createDirectories(skills.getParent());
    Files.createFile(skills);

    assertThatThrownBy(() -> goal(root, session, capturing()).execute())
        .isInstanceOf(MojoExecutionException.class)
        .hasMessageContaining("Failed to install HKJ skills");

    Files.delete(skills);
    goal(module, session, capturing()).execute();
    assertThat(skills.resolve("hkj-guide/SKILL.md")).isRegularFile();
  }

  @Test
  @DisplayName("installs into the top-level project's directory when Maven names no root")
  void installsIntoTheTopLevelProject(@TempDir Path repo) throws Exception {
    MavenProject top = Sessions.project(repo.toFile());
    top.setExecutionRoot(true);
    MavenProject module = Sessions.project(repo.resolve("module").toFile());

    goal(module, Sessions.session(null, module, top), capturing()).execute();

    assertThat(repo.resolve(".claude/skills/hkj-guide/SKILL.md")).isRegularFile();
    assertThat(repo.resolve("module/.claude")).doesNotExist();
  }

  @Test
  @DisplayName("the stamp the goal writes is the one the build reads back")
  void stampReadsBack(@TempDir Path repo) throws Exception {
    MavenProject root = Sessions.project(repo.toFile());
    goal(root, Sessions.session(repo.toFile(), root), capturing()).execute();

    assertThat(InstalledSkills.inspect(repo.resolve(".claude/skills"), RELEASE)).isEmpty();
  }
}
