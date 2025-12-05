// Copyright (c) 2025 Magnus Smith
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
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Demonstrates the integration of the Focus DSL with the Traverse type class.
 *
 * <p>This example shows how to use {@code traverseOver()} to navigate into Kind-wrapped
 * collections, enabling generic traversal over any type with a Traverse instance.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Using {@code traverseOver()} with ListTraverse for Kind-wrapped lists
 *   <li>Using {@code traverseOver()} with MaybeTraverse for optional values
 *   <li>Composing traversals across multiple levels
 *   <li>Working with sum types and Kind-wrapped collections together
 * </ul>
 */
public class TraverseIntegrationExample {

  // ============= Domain Model =============

  /** A role with a name and permission level. */
  record Role(String name, int level) {
    Role promote() {
      return new Role(name, level + 1);
    }
  }

  /** A team member with Kind-wrapped roles. */
  record Member(String name, Kind<ListKind.Witness, Role> roles) {}

  /** A team with Kind-wrapped members. */
  record Team(String name, Kind<ListKind.Witness, Member> members) {}

  /** A project with an optional lead team. */
  record Project(
      String name, Kind<MaybeKind.Witness, Team> leadTeam, Kind<ListKind.Witness, Team> allTeams) {}

  // ============= Lenses =============

  static final Lens<Team, Kind<ListKind.Witness, Member>> teamMembersLens =
      Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

  static final Lens<Member, Kind<ListKind.Witness, Role>> memberRolesLens =
      Lens.of(Member::roles, (m, r) -> new Member(m.name(), r));

  static final Lens<Role, Integer> roleLevelLens =
      Lens.of(Role::level, (r, l) -> new Role(r.name(), l));

  static final Lens<Project, Kind<MaybeKind.Witness, Team>> projectLeadTeamLens =
      Lens.of(Project::leadTeam, (p, t) -> new Project(p.name(), t, p.allTeams()));

  static final Lens<Project, Kind<ListKind.Witness, Team>> projectAllTeamsLens =
      Lens.of(Project::allTeams, (p, t) -> new Project(p.name(), p.leadTeam(), t));

  // ============= Sum Types for Combined Pattern =============

  /** Sealed interface for project variants. */
  sealed interface ProjectVariant permits ActiveProject, ArchivedProject {}

  /** An active project with full details. */
  record ActiveProject(Project project) implements ProjectVariant {}

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

  /** Demonstrates basic traverseOver usage with a single Kind-wrapped collection. */
  static void basicTraverseOverExample() {
    System.out.println("--- Basic traverseOver() ---");

    // Create a team with Kind-wrapped members
    Team team =
        new Team(
            "Platform",
            ListKindHelper.LIST.widen(
                List.of(
                    new Member(
                        "Alice",
                        ListKindHelper.LIST.widen(
                            List.of(new Role("Admin", 10), new Role("Developer", 5)))),
                    new Member(
                        "Bob", ListKindHelper.LIST.widen(List.of(new Role("Developer", 3)))))));

    // Create a path to the Kind<ListKind.Witness, Member> field
    FocusPath<Team, Kind<ListKind.Witness, Member>> membersKindPath = FocusPath.of(teamMembersLens);

    // Use traverseOver to traverse into the Kind-wrapped list
    // Note: Explicit type parameters are required for Java's type inference
    TraversalPath<Team, Member> allMembersPath =
        membersKindPath.<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);

    // Get all members
    List<Member> members = allMembersPath.getAll(team);
    System.out.println("Members: " + members.stream().map(Member::name).toList());

    // Count members
    System.out.println("Member count: " + allMembersPath.count(team));

    System.out.println();
  }

  /** Demonstrates nested traverseOver for deep navigation. */
  static void nestedTraverseOverExample() {
    System.out.println("--- Nested traverseOver() ---");

    Team team =
        new Team(
            "Engineering",
            ListKindHelper.LIST.widen(
                List.of(
                    new Member(
                        "Alice",
                        ListKindHelper.LIST.widen(
                            List.of(new Role("Lead", 10), new Role("Architect", 8)))),
                    new Member(
                        "Bob",
                        ListKindHelper.LIST.widen(
                            List.of(new Role("Senior", 6), new Role("Mentor", 4)))),
                    new Member(
                        "Charlie", ListKindHelper.LIST.widen(List.of(new Role("Junior", 2)))))));

    // Build path step by step (helps with type inference)
    FocusPath<Team, Kind<ListKind.Witness, Member>> step1 = FocusPath.of(teamMembersLens);
    TraversalPath<Team, Member> step2 =
        step1.<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
    TraversalPath<Team, Kind<ListKind.Witness, Role>> step3 = step2.via(memberRolesLens);
    TraversalPath<Team, Role> allRolesPath =
        step3.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // Get all roles across all members
    List<Role> allRoles = allRolesPath.getAll(team);
    System.out.println("All roles: " + allRoles.stream().map(Role::name).toList());
    System.out.println("Total roles: " + allRolesPath.count(team));

    // Navigate to role levels
    TraversalPath<Team, Integer> allLevelsPath = allRolesPath.via(roleLevelLens);
    List<Integer> levels = allLevelsPath.getAll(team);
    System.out.println("All levels: " + levels);

    // Promote all roles (increase level by 1)
    Team promoted = allRolesPath.modifyAll(Role::promote, team);
    List<Integer> newLevels = allLevelsPath.getAll(promoted);
    System.out.println("After promotion: " + newLevels);

    // Conditional promotion - only promote roles with level >= 5
    Team conditionalPromoted = allRolesPath.modifyWhen(r -> r.level() >= 5, Role::promote, team);
    List<Integer> conditionalLevels = allLevelsPath.getAll(conditionalPromoted);
    System.out.println("After conditional promotion (level >= 5): " + conditionalLevels);

    System.out.println();
  }

  /** Demonstrates traverseOver with Maybe for optional values. */
  static void optionalTraverseExample() {
    System.out.println("--- Optional traverseOver() with Maybe ---");

    // Project with a lead team
    Project projectWithLead =
        new Project(
            "Alpha",
            MaybeKindHelper.MAYBE.widen(
                Maybe.just(
                    new Team(
                        "Core",
                        ListKindHelper.LIST.widen(
                            List.of(
                                new Member(
                                    "Alice",
                                    ListKindHelper.LIST.widen(List.of(new Role("Lead", 10))))))))),
            ListKindHelper.LIST.widen(List.of()));

    // Project without a lead team
    Project projectWithoutLead =
        new Project(
            "Beta",
            MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
            ListKindHelper.LIST.widen(List.of()));

    // Path through the optional lead team
    FocusPath<Project, Kind<MaybeKind.Witness, Team>> leadTeamKindPath =
        FocusPath.of(projectLeadTeamLens);

    // Traverse the Maybe to get the Team (if present)
    TraversalPath<Project, Team> leadTeamPath =
        leadTeamKindPath.<MaybeKind.Witness, Team>traverseOver(MaybeTraverse.INSTANCE);

    // Get lead team from each project
    List<Team> leadsFromWithLead = leadTeamPath.getAll(projectWithLead);
    List<Team> leadsFromWithoutLead = leadTeamPath.getAll(projectWithoutLead);

    System.out.println("Lead teams (project with lead): " + leadsFromWithLead.size());
    System.out.println("Lead teams (project without lead): " + leadsFromWithoutLead.size());

    // Continue traversing through the lead team to members and roles
    TraversalPath<Project, Kind<ListKind.Witness, Member>> membersKindPath =
        leadTeamPath.via(teamMembersLens);
    TraversalPath<Project, Member> membersPath =
        membersKindPath.<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
    TraversalPath<Project, Kind<ListKind.Witness, Role>> rolesKindPath =
        membersPath.via(memberRolesLens);
    TraversalPath<Project, Role> rolesPath =
        rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

    // Get all roles from lead team (if exists)
    List<Role> rolesWithLead = rolesPath.getAll(projectWithLead);
    List<Role> rolesWithoutLead = rolesPath.getAll(projectWithoutLead);

    System.out.println("Roles from lead (with lead): " + rolesWithLead);
    System.out.println("Roles from lead (without lead): " + rolesWithoutLead);

    System.out.println();
  }

  /** Demonstrates combining sum types with traverseOver. */
  static void combinedPatternExample() {
    System.out.println("--- Combined Pattern: Sum Types + traverseOver ---");

    // Create project variants
    List<ProjectVariant> variants =
        List.of(
            new ActiveProject(
                new Project(
                    "Alpha",
                    MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                    ListKindHelper.LIST.widen(
                        List.of(
                            new Team(
                                "Team1",
                                ListKindHelper.LIST.widen(
                                    List.of(
                                        new Member(
                                            "Alice",
                                            ListKindHelper.LIST.widen(
                                                List.of(new Role("Dev", 5))))))))))),
            new ArchivedProject("Legacy", "2024-01-01"),
            new ActiveProject(
                new Project(
                    "Beta",
                    MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                    ListKindHelper.LIST.widen(
                        List.of(
                            new Team(
                                "Team2",
                                ListKindHelper.LIST.widen(
                                    List.of(
                                        new Member(
                                            "Bob",
                                            ListKindHelper.LIST.widen(
                                                List.of(
                                                    new Role("Lead", 8),
                                                    new Role("Arch", 7))))))))))));

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

    // For each active project, count roles
    for (ProjectVariant v : variants) {
      activeProjectPath
          .getOptional(v)
          .ifPresent(
              active -> {
                // Build path to all roles in the project
                Lens<ActiveProject, Kind<ListKind.Witness, Team>> allTeamsLens =
                    Lens.of(
                        ap -> ap.project().allTeams(),
                        (ap, teams) ->
                            new ActiveProject(
                                new Project(ap.project().name(), ap.project().leadTeam(), teams)));

                FocusPath<ActiveProject, Kind<ListKind.Witness, Team>> teamsKindPath =
                    FocusPath.of(allTeamsLens);
                TraversalPath<ActiveProject, Team> teamsPath =
                    teamsKindPath.<ListKind.Witness, Team>traverseOver(ListTraverse.INSTANCE);
                TraversalPath<ActiveProject, Kind<ListKind.Witness, Member>> membersKindPath =
                    teamsPath.via(teamMembersLens);
                TraversalPath<ActiveProject, Member> membersPath =
                    membersKindPath.<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
                TraversalPath<ActiveProject, Kind<ListKind.Witness, Role>> rolesKindPath =
                    membersPath.via(memberRolesLens);
                TraversalPath<ActiveProject, Role> rolesPath =
                    rolesKindPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

                int roleCount = rolesPath.count(active);
                System.out.println(
                    "Project '" + active.project().name() + "' has " + roleCount + " roles");
              });
    }

    System.out.println();
  }
}
