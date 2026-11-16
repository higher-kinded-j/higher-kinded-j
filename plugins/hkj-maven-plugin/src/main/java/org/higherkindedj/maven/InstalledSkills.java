// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Where a build's HKJ skills go, the stamp that says which Higher-Kinded-J release they describe,
 * and what a build says when the skills it finds carry another release, or none.
 */
final class InstalledSkills {

  /** The stamp's file name, in the directory the skills are installed into. */
  static final String STAMP_FILE = ".hkj-skills-version";

  private static final String STAMP_KEY = "version";

  /** A release: dotted numbers, then an optional qualifier. */
  private static final Pattern RELEASE = Pattern.compile("(\\d{1,18}(?:\\.\\d{1,18})*)(-.+)?");

  private InstalledSkills() {}

  /** What a build has to say about the HKJ skills it finds installed. */
  sealed interface Notice {

    /** The skills are stamped with another release than the plugin's. */
    record OtherRelease(String stamped) implements Notice {
      public OtherRelease {
        Objects.requireNonNull(stamped, "stamped");
      }
    }

    /** The skills carry no stamp, as skills installed by hand or by an older plugin do. */
    record Unstamped() implements Notice {}
  }

  /**
   * The directory a build installs the skills into: {@code .claude/skills} at the root of the
   * build. That is the directory Maven names the multi-module project directory: the one holding
   * {@code .mvn}, or else that of the POM Maven starts from. Failing that, it is the top-level
   * project's, and failing that {@code fallback}'s.
   */
  static Path directory(MavenSession session, File fallback) {
    File root =
        Optional.ofNullable(session.getRequest().getMultiModuleProjectDirectory())
            .or(
                () ->
                    Optional.ofNullable(session.getTopLevelProject()).map(MavenProject::getBasedir))
            .orElse(fallback);
    return skillsIn(root);
  }

  /** The {@code .claude/skills} directory in {@code dir}. */
  static Path skillsIn(File dir) {
    return dir.toPath().toAbsolutePath().normalize().resolve(".claude").resolve("skills");
  }

  /**
   * Whether {@code a} and {@code b} name the same directory, through any symbolic link. Maven does
   * not resolve links in a project's path, so a build started through one names its root by another
   * path than the one the multi-module project directory gives.
   */
  static boolean sameDirectory(Path a, Path b) {
    try {
      return a.equals(b) || (Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b));
    } catch (IOException _) {
      return false; // unreadable: treated as another directory
    }
  }

  /** The stamp's text, naming {@code release}. */
  static String stamp(String release) {
    return """
        # The Higher-Kinded-J release the hkj-* skills in this directory describe.
        # The HKJ build plugin writes this file each time it installs them.
        %s=%s
        """
        .formatted(STAMP_KEY, release);
  }

  /** The release a stamp's text names, or empty when it names none. */
  static Optional<String> stampedRelease(String stamp) {
    return stamp
        .lines()
        .map(String::strip)
        .filter(line -> !line.startsWith("#"))
        .map(line -> line.split("=", 2))
        .filter(pair -> pair.length == 2 && pair[0].strip().equals(STAMP_KEY))
        .map(pair -> pair[1].strip())
        .filter(release -> !release.isEmpty())
        .findFirst();
  }

  /**
   * Writes {@code content} to {@code file} unless it holds exactly that already, and says whether
   * it wrote. An install that writes only what differs leaves an up-to-date copy untouched, so any
   * number of installs into one directory converge on the same files.
   */
  static boolean writeIfChanged(Path file, byte[] content) throws IOException {
    if (Files.isRegularFile(file) && Arrays.equals(Files.readAllBytes(file), content)) {
      return false;
    }
    Files.createDirectories(file.getParent());
    // Written beside the file and moved over it, so a reader such as Claude Code sees the old
    // file or the new one, never part of one
    // A name of its own, so another process installing at the same time writes another file
    Path partial =
        file.resolveSibling("." + file.getFileName() + "." + UUID.randomUUID() + ".partial");
    try {
      Files.write(partial, content);
      try {
        Files.move(
            partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException _) {
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(partial);
    }
    return true;
  }

  /** Whether {@code dir} holds an HKJ skill: a directory named {@code hkj-*}. */
  static boolean holdsHkjSkills(Path dir) {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    try (Stream<Path> entries = Files.list(dir)) {
      return entries.anyMatch(
          entry -> Files.isDirectory(entry) && entry.getFileName().toString().startsWith("hkj-"));
    } catch (IOException _) {
      return false; // unreadable: there is nothing to say about it
    }
  }

  /**
   * What to say about installed skills, given the stamp's text, or empty when there is no stamp:
   * nothing when they are {@code release}'s, or when there are none.
   */
  static Optional<Notice> check(Optional<String> stamp, boolean holdsHkjSkills, String release) {
    if (!holdsHkjSkills) {
      return Optional.empty();
    }
    Optional<String> stamped = stamp.flatMap(InstalledSkills::stampedRelease);
    if (stamped.isEmpty()) {
      return Optional.of(new Notice.Unstamped());
    }
    return stamped.filter(other -> !other.equals(release)).map(Notice.OtherRelease::new);
  }

  /** What to say about the skills installed in {@code dir}, for a plugin of {@code release}. */
  static Optional<Notice> inspect(Path dir, String release) {
    Path stampFile = dir.resolve(STAMP_FILE);
    Optional<String> stamp;
    try {
      stamp =
          Files.isRegularFile(stampFile)
              ? Optional.of(Files.readString(stampFile, StandardCharsets.UTF_8))
              : Optional.empty();
    } catch (IOException _) {
      stamp = Optional.empty(); // unreadable: it says no more than a missing stamp
    }
    return check(stamp, holdsHkjSkills(dir), release);
  }

  /** The notice's text, for skills in {@code dir} and a plugin of {@code release}. */
  static String message(Notice notice, String release, Path dir) {
    return switch (notice) {
      case Notice.OtherRelease(String stamped) ->
          ("The HKJ Claude Code skills in %s describe Higher-Kinded-J %s, but this build's HKJ"
                  + " plugin is %s. Run mvn hkj:install-skills from the top of the build to install"
                  + " the %s skills%s. With <skills>true</skills> in the plugin's configuration, a"
                  + " build installs them before it compiles.")
              .formatted(dir, stamped, release, release, unlessMovingTo(stamped, release));
      case Notice.Unstamped() ->
          ("The HKJ Claude Code skills in %s do not say which Higher-Kinded-J release they"
                  + " describe, so they may not match this build's HKJ plugin, %s. Run mvn"
                  + " hkj:install-skills from the top of the build to install the %s skills.")
              .formatted(dir, release, release);
    };
  }

  /**
   * The clause offering to move the build to {@code stamped} instead, when that is a later release
   * than {@code release}: skills newer than the plugin are usually a copy a team has moved on to.
   */
  private static String unlessMovingTo(String stamped, String release) {
    return isLater(stamped, release)
        ? ", unless the build should move to " + stamped + " instead"
        : "";
  }

  /**
   * Whether {@code a} is a later release than {@code b}. Each must be dotted numbers with an
   * optional qualifier, such as {@code 0.6.0-SNAPSHOT}, which comes before its plain release; any
   * other form is not later.
   */
  static boolean isLater(String a, String b) {
    Matcher first = RELEASE.matcher(a);
    Matcher second = RELEASE.matcher(b);
    if (!first.matches() || !second.matches()) {
      return false;
    }
    int order = Arrays.compare(numbers(first.group(1)), numbers(second.group(1)));
    return order != 0 ? order > 0 : first.group(2) == null && second.group(2) != null;
  }

  private static long[] numbers(String dotted) {
    return Arrays.stream(dotted.split("\\.")).mapToLong(Long::parseLong).toArray();
  }

  /**
   * The text telling a project that the HKJ skills in its own {@code dir}, stamped with another
   * release or none, are a copy the build no longer updates, since the plugin installs them at the
   * root of the build.
   */
  static String strayCopy(Path dir) {
    return ("The HKJ Claude Code skills in %s are a copy this build no longer updates: the HKJ"
            + " plugin installs them at the root of the build. Run mvn hkj:install-skills from the"
            + " top of the build to install them there if it holds none, then remove the hkj-*"
            + " directories in %s.")
        .formatted(dir, dir);
  }
}
