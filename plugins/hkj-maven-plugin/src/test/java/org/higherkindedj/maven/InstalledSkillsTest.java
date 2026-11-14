// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.higherkindedj.maven.InstalledSkills.Notice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("InstalledSkills")
class InstalledSkillsTest {

  @Nested
  @DisplayName("the directory")
  class Directory {

    private final File fallback = new File("/repo/module");

    /**
     * {@code first} and {@code more} as the plugin names them: on Windows, on the current drive.
     */
    private static Path absolute(String first, String... more) {
      return Path.of(first, more).toAbsolutePath();
    }

    @Test
    @DisplayName("is .claude/skills in the directory Maven names the root of the build")
    void inTheMultiModuleProjectDirectory() {
      MavenSession session = Sessions.session(new File("/repo"));

      assertThat(InstalledSkills.directory(session, fallback))
          .isEqualTo(absolute("/repo", ".claude", "skills"));
    }

    @Test
    @DisplayName("is in the top-level project's directory when Maven names no root")
    void inTheTopLevelProjectsDirectory() {
      MavenProject top = Sessions.project(new File("/repo/top"));
      top.setExecutionRoot(true);
      MavenSession session = Sessions.session(null, Sessions.project(fallback), top);

      assertThat(InstalledSkills.directory(session, fallback))
          .isEqualTo(absolute("/repo", "top", ".claude", "skills"));
    }

    @Test
    @DisplayName("is in the fallback's directory when there is neither")
    void inTheFallback() {
      MavenSession session = Sessions.session(null);

      assertThat(InstalledSkills.directory(session, fallback))
          .isEqualTo(absolute("/repo", "module", ".claude", "skills"));
    }
  }

  @Nested
  @DisplayName("the stamp")
  class Stamp {

    @Test
    @DisplayName("is the text both build plugins write and read")
    void isTheSharedFormat() {
      // The Gradle plugin writes and reads the same file, so its test pins the same text
      assertThat(InstalledSkills.stamp("0.6.0"))
          .isEqualTo(
              """
              # The Higher-Kinded-J release the hkj-* skills in this directory describe.
              # The HKJ build plugin writes this file each time it installs them.
              version=0.6.0
              """);
    }

    @Test
    @DisplayName("names the release it was written with")
    void namesItsRelease() {
      assertThat(InstalledSkills.stampedRelease(InstalledSkills.stamp("0.6.0"))).contains("0.6.0");
    }

    @Test
    @DisplayName("is read past comments and spaces around the separator")
    void readPastCommentsAndSpaces() {
      assertThat(InstalledSkills.stampedRelease("# version=0.1.0\n  version = 0.6.0 \n"))
          .contains("0.6.0");
    }

    @Test
    @DisplayName("names no release when it has no version, or an empty one")
    void namesNoRelease() {
      assertThat(InstalledSkills.stampedRelease("")).isEmpty();
      assertThat(InstalledSkills.stampedRelease("release=0.6.0\nversion\n")).isEmpty();
      assertThat(InstalledSkills.stampedRelease("version=  \n")).isEmpty();
    }
  }

  @Nested
  @DisplayName("the check")
  class Check {

    private static final Optional<String> CURRENT = Optional.of(InstalledSkills.stamp("0.6.0"));

    @Test
    @DisplayName("says nothing when there are no HKJ skills, whatever the stamp")
    void quietWithoutSkills() {
      assertThat(InstalledSkills.check(Optional.empty(), false, "0.6.0")).isEmpty();
      assertThat(InstalledSkills.check(Optional.of("version=0.5.0"), false, "0.6.0")).isEmpty();
    }

    @Test
    @DisplayName("says nothing when the skills are the plugin's release")
    void quietWhenCurrent() {
      assertThat(InstalledSkills.check(CURRENT, true, "0.6.0")).isEmpty();
    }

    @Test
    @DisplayName("names a release older or newer than the plugin's")
    void namesAnotherRelease() {
      assertThat(InstalledSkills.check(Optional.of("version=0.5.0"), true, "0.6.0"))
          .contains(new Notice.OtherRelease("0.5.0"));
      assertThat(InstalledSkills.check(CURRENT, true, "0.5.1"))
          .contains(new Notice.OtherRelease("0.6.0"));
    }

    @Test
    @DisplayName("finds skills unstamped when there is no stamp, or it names no release")
    void findsUnstamped() {
      assertThat(InstalledSkills.check(Optional.empty(), true, "0.6.0"))
          .contains(new Notice.Unstamped());
      assertThat(InstalledSkills.check(Optional.of("# empty\n"), true, "0.6.0"))
          .contains(new Notice.Unstamped());
    }
  }

  @Nested
  @DisplayName("inspecting a directory")
  class Inspect {

    @Test
    @DisplayName("finds HKJ skills only in directories named hkj-*")
    void findsHkjDirectories(@TempDir Path dir) throws IOException {
      assertThat(InstalledSkills.holdsHkjSkills(dir.resolve("absent"))).isFalse();
      Files.createDirectory(dir.resolve("my-skill"));
      Files.createFile(dir.resolve("hkj-notes.md"));
      assertThat(InstalledSkills.holdsHkjSkills(dir)).isFalse();

      Files.createDirectory(dir.resolve("hkj-guide"));
      assertThat(InstalledSkills.holdsHkjSkills(dir)).isTrue();
    }

    @Test
    @DisplayName("reads the stamp beside the skills")
    void readsTheStamp(@TempDir Path dir) throws IOException {
      Files.createDirectory(dir.resolve("hkj-guide"));
      assertThat(InstalledSkills.inspect(dir, "0.6.0")).contains(new Notice.Unstamped());

      Files.writeString(dir.resolve(InstalledSkills.STAMP_FILE), InstalledSkills.stamp("0.5.0"));
      assertThat(InstalledSkills.inspect(dir, "0.6.0")).contains(new Notice.OtherRelease("0.5.0"));
      assertThat(InstalledSkills.inspect(dir, "0.5.0")).isEmpty();
    }
  }

  @Test
  @DisplayName("a release is later by its numbers, and a plain release after its qualified ones")
  void laterReleases() {
    assertThat(InstalledSkills.isLater("0.7.0", "0.6.0")).isTrue();
    assertThat(InstalledSkills.isLater("0.10.0", "0.9.1")).isTrue();
    assertThat(InstalledSkills.isLater("0.6.0", "0.6.0-SNAPSHOT")).isTrue();
    assertThat(InstalledSkills.isLater("0.6.0-SNAPSHOT", "0.6.0")).isFalse();
    assertThat(InstalledSkills.isLater("0.6.0", "0.6.0")).isFalse();
    assertThat(InstalledSkills.isLater("0.5.0", "0.6.0")).isFalse();
    assertThat(InstalledSkills.isLater("next", "0.6.0")).isFalse();
  }

  @Test
  @DisplayName("a notice offers to move the build only to a later release than the plugin's")
  void movesOnlyToALaterRelease() {
    Path dir = Path.of(".claude", "skills");

    assertThat(InstalledSkills.message(new Notice.OtherRelease("0.7.0"), "0.6.0", dir))
        .contains("install the 0.6.0 skills, unless the build should move to 0.7.0 instead.");
    assertThat(InstalledSkills.message(new Notice.OtherRelease("0.5.0"), "0.6.0", dir))
        .contains("install the 0.6.0 skills.")
        .doesNotContain("move to");
  }

  @Test
  @DisplayName("each notice names the releases, the directory and the goal to run")
  void messages() {
    Path dir = Path.of("/repo", ".claude", "skills");

    assertThat(InstalledSkills.message(new Notice.OtherRelease("0.5.0"), "0.6.0", dir))
        .contains(dir + " describe Higher-Kinded-J 0.5.0")
        .contains("this build's HKJ plugin is 0.6.0")
        .contains("Run mvn hkj:install-skills from the top of the build to install the 0.6.0")
        .contains("<skills>true</skills>");
    assertThat(InstalledSkills.message(new Notice.Unstamped(), "0.6.0", dir))
        .contains(dir + " do not say which Higher-Kinded-J release they describe")
        .contains("Run mvn hkj:install-skills from the top of the build to install the 0.6.0");
    Path module = Path.of("/repo", "app", ".claude", "skills");
    assertThat(InstalledSkills.strayCopy(module))
        .contains(module + " are a copy this build no longer updates")
        .contains("Run mvn hkj:install-skills from the top of the build to install them there")
        .contains("then remove the hkj-* directories in " + module);
  }
}
