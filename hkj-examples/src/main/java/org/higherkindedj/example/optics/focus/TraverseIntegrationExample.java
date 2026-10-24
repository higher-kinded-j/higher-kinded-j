// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Demonstrates the integration of the Focus DSL with the Traverse type class.
 *
 * <p>This example shows how to use {@code EachInstances.fromTraverse()} to navigate into
 * Kind-wrapped collections, enabling generic traversal over any type with a Traverse instance.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Using {@code EachInstances.fromTraverse()} with ListTraverse for Kind-wrapped lists
 *   <li>Using {@code EachInstances.fromTraverse()} with MaybeTraverse for optional values
 *   <li>Composing traversals across multiple levels
 *   <li>Working with sum types and Kind-wrapped collections together
 * </ul>
 */
public class TraverseIntegrationExample {

  // ============= Domain Model =============

  /** A skill with a name and proficiency level. */
  record Skill(String name, int proficiency) {
    /** Creates a skill with increased proficiency. */
    Skill improve() {
      return new Skill(name, Math.min(100, proficiency + 10));
    }
  }

  /** A team member with Kind-wrapped skills. */
  record Member(String name, Kind<ListKind.Witness, Skill> skills) {}

  /** A team with Kind-wrapped members. */
  record SkillsTeam(String name, Kind<ListKind.Witness, Member> members) {}

  /** A project plan with an optional lead team. */
  record ProjectPlan(
      String name,
      Kind<MaybeKind.Witness, SkillsTeam> leadTeam,
      Kind<ListKind.Witness, SkillsTeam> allTeams) {}

  // ============= Lenses =============

  static final Lens<SkillsTeam, Kind<ListKind.Witness, Member>> teamMembersLens =
      Lens.of(SkillsTeam::members, (t, m) -> new SkillsTeam(t.name(), m));

  static final Lens<Member, Kind<ListKind.Witness, Skill>> memberSkillsLens =
      Lens.of(Member::skills, (m, s) -> new Member(m.name(), s));

  static final Lens<Skill, Integer> skillProficiencyLens =
      Lens.of(Skill::proficiency, (s, p) -> new Skill(s.name(), p));

  static final Lens<ProjectPlan, Kind<MaybeKind.Witness, SkillsTeam>> projectLeadTeamLens =
      Lens.of(ProjectPlan::leadTeam, (p, t) -> new ProjectPlan(p.name(), t, p.allTeams()));

  static final Lens<ProjectPlan, Kind<ListKind.Witness, SkillsTeam>> projectAllTeamsLens =
      Lens.of(ProjectPlan::allTeams, (p, t) -> new ProjectPlan(p.name(), p.leadTeam(), t));

  // ============= Sum Types for Combined Pattern =============

  /** Sealed interface for project variants. */
  sealed interface ProjectVariant permits ActiveProject, ArchivedProject {}

  /** An active project with full details. */
  record ActiveProject(ProjectPlan project) implements ProjectVariant {}

  /** An archived project with limited information. */
  record ArchivedProject(String name, String archiveDate) implements ProjectVariant {}

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Traverse Integration Example ===\n");

    basicTraverseOverExample();
    nestedTraverseOverExample();
    optionalTraverseExample();
    combinedPatternExample();
  }

  /** Demonstrates basic fromTraverse usage with a single Kind-wrapped collection. */
  static void basicTraverseOverExample() {
    System.out.println("--- Basic fromTraverse ---");

    // Create a team with Kind-wrapped members
    SkillsTeam team =
        new SkillsTeam(
            "Platform",
            ListKindHelper.LIST.widen(
                List.of(
                    new Member(
                        "Alice",
                        ListKindHelper.LIST.widen(
                            List.of(new Skill("Java", 90), new Skill("SQL", 50)))),
                    new Member("Bob", ListKindHelper.LIST.widen(List.of(new Skill("Java", 30)))))));

    // Create a path to the Kind<ListKind.Witness, Member> field
    FocusPath<SkillsTeam, Kind<ListKind.Witness, Member>> membersKindPath =
        FocusPath.of(teamMembersLens);

    // Use fromTraverse to traverse into the Kind-wrapped list
    // Note: Explicit type parameters are required for Java's type inference
    TraversalPath<SkillsTeam, Member> allMembersPath =
        membersKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));

    // Get all members
    List<Member> members = allMembersPath.getAll(team);
    System.out.println("Members: " + members.stream().map(Member::name).toList());

    // Count members
    System.out.println("Member count: " + allMembersPath.count(team));

    System.out.println();
  }

  /** Demonstrates nested fromTraverse for deep navigation. */
  static void nestedTraverseOverExample() {
    System.out.println("--- Nested fromTraverse ---");

    SkillsTeam team =
        new SkillsTeam(
            "Engineering",
            ListKindHelper.LIST.widen(
                List.of(
                    new Member(
                        "Alice",
                        ListKindHelper.LIST.widen(
                            List.of(new Skill("Java", 90), new Skill("Design", 80)))),
                    new Member(
                        "Bob",
                        ListKindHelper.LIST.widen(
                            List.of(new Skill("Kotlin", 60), new Skill("Mentoring", 40)))),
                    new Member(
                        "Charlie", ListKindHelper.LIST.widen(List.of(new Skill("SQL", 20)))))));

    // Build path step by step (helps with type inference)
    FocusPath<SkillsTeam, Kind<ListKind.Witness, Member>> step1 = FocusPath.of(teamMembersLens);
    TraversalPath<SkillsTeam, Member> step2 =
        step1.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
    TraversalPath<SkillsTeam, Kind<ListKind.Witness, Skill>> step3 = step2.via(memberSkillsLens);
    TraversalPath<SkillsTeam, Skill> allSkillsPath =
        step3.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));

    // Get all skills across all members
    List<Skill> allSkills = allSkillsPath.getAll(team);
    System.out.println("All skills: " + allSkills.stream().map(Skill::name).toList());
    System.out.println("Total skills: " + allSkillsPath.count(team));

    // Navigate to skill proficiencies
    TraversalPath<SkillsTeam, Integer> allProficienciesPath =
        allSkillsPath.via(skillProficiencyLens);
    List<Integer> proficiencies = allProficienciesPath.getAll(team);
    System.out.println("All proficiencies: " + proficiencies);

    // Improve all skills (raise proficiency by 10)
    SkillsTeam improved = allSkillsPath.modifyAll(Skill::improve, team);
    List<Integer> newProficiencies = allProficienciesPath.getAll(improved);
    System.out.println("After improvement: " + newProficiencies);

    // Conditional improvement - only improve skills with proficiency >= 50
    SkillsTeam conditionalImproved =
        allSkillsPath.modifyWhen(s -> s.proficiency() >= 50, Skill::improve, team);
    List<Integer> conditionalProficiencies = allProficienciesPath.getAll(conditionalImproved);
    System.out.println(
        "After conditional improvement (proficiency >= 50): " + conditionalProficiencies);

    System.out.println();
  }

  /** Demonstrates fromTraverse with Maybe for optional values. */
  static void optionalTraverseExample() {
    System.out.println("--- Optional fromTraverse with Maybe ---");

    // Project with a lead team
    ProjectPlan projectWithLead =
        new ProjectPlan(
            "Alpha",
            MaybeKindHelper.MAYBE.widen(
                Maybe.just(
                    new SkillsTeam(
                        "Core",
                        ListKindHelper.LIST.widen(
                            List.of(
                                new Member(
                                    "Alice",
                                    ListKindHelper.LIST.widen(
                                        List.of(new Skill("Leadership", 90))))))))),
            ListKindHelper.LIST.widen(List.of()));

    // Project without a lead team
    ProjectPlan projectWithoutLead =
        new ProjectPlan(
            "Beta",
            MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
            ListKindHelper.LIST.widen(List.of()));

    // Path through the optional lead team
    FocusPath<ProjectPlan, Kind<MaybeKind.Witness, SkillsTeam>> leadTeamKindPath =
        FocusPath.of(projectLeadTeamLens);

    // Traverse the Maybe to get the team (if present)
    TraversalPath<ProjectPlan, SkillsTeam> leadTeamPath =
        leadTeamKindPath.each(EachInstances.fromTraverse(MaybeTraverse.INSTANCE));

    // Get lead team from each project
    List<SkillsTeam> leadsFromWithLead = leadTeamPath.getAll(projectWithLead);
    List<SkillsTeam> leadsFromWithoutLead = leadTeamPath.getAll(projectWithoutLead);

    System.out.println("Lead teams (project with lead): " + leadsFromWithLead.size());
    System.out.println("Lead teams (project without lead): " + leadsFromWithoutLead.size());

    // Continue traversing through the lead team to members and skills
    TraversalPath<ProjectPlan, Kind<ListKind.Witness, Member>> membersKindPath =
        leadTeamPath.via(teamMembersLens);
    TraversalPath<ProjectPlan, Member> membersPath =
        membersKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
    TraversalPath<ProjectPlan, Kind<ListKind.Witness, Skill>> skillsKindPath =
        membersPath.via(memberSkillsLens);
    TraversalPath<ProjectPlan, Skill> skillsPath =
        skillsKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));

    // Get all skills from lead team (if exists)
    List<Skill> skillsWithLead = skillsPath.getAll(projectWithLead);
    List<Skill> skillsWithoutLead = skillsPath.getAll(projectWithoutLead);

    System.out.println("Skills from lead (with lead): " + skillsWithLead);
    System.out.println("Skills from lead (without lead): " + skillsWithoutLead);

    System.out.println();
  }

  /** Demonstrates combining sum types with fromTraverse. */
  static void combinedPatternExample() {
    System.out.println("--- Combined Pattern: Sum Types + fromTraverse ---");

    // Create project variants
    List<ProjectVariant> variants =
        List.of(
            new ActiveProject(
                new ProjectPlan(
                    "Alpha",
                    MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                    ListKindHelper.LIST.widen(
                        List.of(
                            new SkillsTeam(
                                "Team1",
                                ListKindHelper.LIST.widen(
                                    List.of(
                                        new Member(
                                            "Alice",
                                            ListKindHelper.LIST.widen(
                                                List.of(new Skill("Java", 50))))))))))),
            new ArchivedProject("Legacy", "2024-01-01"),
            new ActiveProject(
                new ProjectPlan(
                    "Beta",
                    MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                    ListKindHelper.LIST.widen(
                        List.of(
                            new SkillsTeam(
                                "Team2",
                                ListKindHelper.LIST.widen(
                                    List.of(
                                        new Member(
                                            "Bob",
                                            ListKindHelper.LIST.widen(
                                                List.of(
                                                    new Skill("Leadership", 80),
                                                    new Skill("Design", 70))))))))))));

    // Use instanceOf to focus on ActiveProject only
    AffinePath<ProjectVariant, ActiveProject> activeProjectPath =
        AffinePath.instanceOf(ActiveProject.class);

    // Count active projects
    int activeCount = 0;
    for (ProjectVariant v : variants) {
      if (activeProjectPath.matches(v)) {
        activeCount++;
      }
    }
    System.out.println("Active projects: " + activeCount);

    // For each active project, count skills
    for (ProjectVariant v : variants) {
      activeProjectPath
          .getOptional(v)
          .ifPresent(
              active -> {
                // Build path to all skills in the project
                Lens<ActiveProject, Kind<ListKind.Witness, SkillsTeam>> allTeamsLens =
                    Lens.of(
                        ap -> ap.project().allTeams(),
                        (ap, teams) ->
                            new ActiveProject(
                                new ProjectPlan(
                                    ap.project().name(), ap.project().leadTeam(), teams)));

                FocusPath<ActiveProject, Kind<ListKind.Witness, SkillsTeam>> teamsKindPath =
                    FocusPath.of(allTeamsLens);
                TraversalPath<ActiveProject, SkillsTeam> teamsPath =
                    teamsKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
                TraversalPath<ActiveProject, Kind<ListKind.Witness, Member>> membersKindPath =
                    teamsPath.via(teamMembersLens);
                TraversalPath<ActiveProject, Member> membersPath =
                    membersKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
                TraversalPath<ActiveProject, Kind<ListKind.Witness, Skill>> skillsKindPath =
                    membersPath.via(memberSkillsLens);
                TraversalPath<ActiveProject, Skill> skillsPath =
                    skillsKindPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));

                int skillCount = skillsPath.count(active);
                System.out.println(
                    "Project '" + active.project().name() + "' has " + skillCount + " skills");
              });
    }

    System.out.println();
  }
}
