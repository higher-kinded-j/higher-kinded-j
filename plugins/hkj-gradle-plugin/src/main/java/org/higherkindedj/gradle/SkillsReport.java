// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;

/**
 * The report a build gives, once its tasks have run, on a directory of HKJ skills.
 *
 * <p>For the skills at the root of the build, it warns when they describe another release than the
 * plugin's, and logs a line when they do not say. Checked after the tasks, it says nothing once any
 * install has brought them up to date. For a project's own {@code .claude/skills}, which no install
 * of the build writes to unless the build points one there, it logs a line for skills of another
 * release or none: a copy the build no longer updates.
 *
 * <p>Each report is a shared build service that listens for the build's tasks, so Gradle keeps it
 * for every build, one that reuses a configuration cache entry included, and closes it when the
 * build ends. The build registers one report on the root, whichever projects apply the plugin and
 * through whichever classloaders, and one on each other project's own copy.
 */
abstract class SkillsReport
    implements BuildService<SkillsReport.Parameters>, OperationCompletionListener, AutoCloseable {

  /**
   * The root report's name, the same in every classloader the plugin loads in. Gradle registers a
   * service by its name alone, so a project whose classloader did not register the report gets the
   * one that did, and the build gives one report.
   */
  private static final String ROOT_REPORT = "hkjSkillsReport";

  private static final Logger LOGGER = Logging.getLogger(SkillsReport.class);

  /** What a report checks. */
  interface Parameters extends BuildServiceParameters {

    /** The directory of skills the report checks. */
    DirectoryProperty getSkills();

    /** Whether the directory is a project's own copy, rather than the root's. */
    Property<Boolean> getProjectCopy();

    /** The plugin's release, which is the skills' release. */
    Property<String> getRelease();
  }

  /** Creates the report, as Gradle does once per build. */
  public SkillsReport() {}

  /**
   * Registers the build's report on the root's skills, unless a project has already, through this
   * classloader or another, and the report on {@code project}'s own, unless it is the root project.
   */
  static void register(Project project) {
    Directory root = project.getLayout().getSettingsDirectory();
    Directory own = project.getLayout().getProjectDirectory();
    register(project, ROOT_REPORT, root.dir(".claude/skills"), false);
    if (!own.getAsFile().equals(root.getAsFile())) {
      register(
          project, "hkjProjectSkillsReport" + project.getPath(), own.dir(".claude/skills"), true);
    }
  }

  private static void register(Project project, String name, Directory skills, boolean own) {
    // A report another classloader registered is another class, so it is held only as a
    // listener, whose interface every classloader shares
    Provider<? extends OperationCompletionListener> report =
        project
            .getGradle()
            .getSharedServices()
            .registerIfAbsent(
                name,
                SkillsReport.class,
                spec -> {
                  Parameters parameters = spec.getParameters();
                  parameters.getSkills().set(skills);
                  parameters.getProjectCopy().set(own);
                  parameters.getRelease().set(HKJPlugin.readPluginVersion());
                });
    project.getObjects().newInstance(Listeners.class).getRegistry().onTaskCompletion(report);
  }

  /** Reaches the build's listener registry, which Gradle injects into the objects it creates. */
  abstract static class Listeners {

    /** Creates the helper, as Gradle does when the plugin asks for one. */
    public Listeners() {}

    /**
     * Returns the build's listener registry.
     *
     * @return the registry
     */
    @Inject
    public abstract BuildEventsListenerRegistry getRegistry();
  }

  @Override
  public void onFinish(FinishEvent event) {
    // The report waits for the build to end
  }

  @Override
  public void close() {
    Parameters parameters = getParameters();
    Path dir = parameters.getSkills().get().getAsFile().toPath();
    String release = parameters.getRelease().get();
    InstalledSkills.check(stamp(dir), InstalledSkills.holdsHkjSkills(dir), release)
        .ifPresent(
            notice -> {
              if (parameters.getProjectCopy().get()) {
                LOGGER.lifecycle(InstalledSkills.strayCopy(dir));
                return;
              }
              String message = InstalledSkills.message(notice, release, dir);
              switch (notice) {
                case InstalledSkills.Notice.OtherRelease _ -> LOGGER.warn(message);
                case InstalledSkills.Notice.Unstamped _ -> LOGGER.lifecycle(message);
              }
            });
  }

  private static Optional<String> stamp(Path dir) {
    Path file = dir.resolve(InstalledSkills.STAMP_FILE);
    try {
      return Files.isRegularFile(file)
          ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
          : Optional.empty();
    } catch (IOException _) {
      return Optional.empty(); // unreadable: it says no more than a missing stamp
    }
  }
}
