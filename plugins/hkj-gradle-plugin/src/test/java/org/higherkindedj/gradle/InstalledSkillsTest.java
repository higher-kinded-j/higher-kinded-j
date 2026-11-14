// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.higherkindedj.gradle.InstalledSkills.Notice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("InstalledSkills")
class InstalledSkillsTest {

  @Nested
  @DisplayName("the stamp")
  class Stamp {

    @Test
    @DisplayName("is the text both build plugins write and read")
    void isTheSharedFormat() {
      // The Maven plugin writes and reads the same file, so its test pins the same text
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

  @Test
  @DisplayName("a file is written only when its content differs")
  void writtenOnlyWhenItDiffers(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("hkj-guide/SKILL.md");
    byte[] content = "skill".getBytes(StandardCharsets.UTF_8);

    assertThat(InstalledSkills.writeIfChanged(file, content)).isTrue();
    assertThat(InstalledSkills.writeIfChanged(file, content)).isFalse();
    assertThat(InstalledSkills.writeIfChanged(file, "other".getBytes(StandardCharsets.UTF_8)))
        .isTrue();
    assertThat(file).hasContent("other");
    // The file is written beside its place and moved over it, and nothing of that is left behind
    try (var entries = Files.list(file.getParent())) {
      assertThat(entries).containsExactly(file);
    }
  }

  @Nested
  @DisplayName("an HKJ skill")
  class HkjSkill {

    @Test
    @DisplayName("is a directory named hkj-*")
    void isAnHkjDirectory(@TempDir Path dir) throws IOException {
      assertThat(InstalledSkills.holdsHkjSkills(dir.resolve("absent"))).isFalse();
      Files.createDirectory(dir.resolve("my-skill"));
      Files.createFile(dir.resolve("hkj-notes.md"));
      assertThat(InstalledSkills.holdsHkjSkills(dir)).isFalse();

      Files.createDirectory(dir.resolve("hkj-guide"));
      assertThat(InstalledSkills.holdsHkjSkills(dir)).isTrue();
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
  @DisplayName("each notice names the releases, the directory and the task to run")
  void messages() {
    Path dir = Path.of(".claude", "skills");

    assertThat(InstalledSkills.message(new Notice.OtherRelease("0.5.0"), "0.6.0", dir))
        .contains(dir + " describe Higher-Kinded-J 0.5.0")
        .contains("this build's HKJ plugin is 0.6.0")
        .contains("Run the hkjInstallSkills task to install the 0.6.0 skills")
        .contains("skills = true");
    assertThat(InstalledSkills.message(new Notice.Unstamped(), "0.6.0", dir))
        .contains(dir + " do not say which Higher-Kinded-J release they describe")
        .contains("Run the hkjInstallSkills task to install the 0.6.0 skills");
    Path module = Path.of("/repo", "app", ".claude", "skills");
    assertThat(InstalledSkills.strayCopy(module))
        .contains(module + " are a copy this build no longer updates")
        .contains("Run the hkjInstallSkills task to install them there if it holds none")
        .contains("then remove the hkj-* directories in " + module);
  }
}
