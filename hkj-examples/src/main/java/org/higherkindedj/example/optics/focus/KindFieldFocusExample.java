// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.optics.annotations.GenerateFocus;

/**
 * Demonstrates automatic Kind field support in the Focus DSL annotation processor.
 *
 * <p>This example shows how the {@code @GenerateFocus} annotation automatically generates
 * appropriate traversal code for {@code Kind<F, A>} fields, eliminating the need for manual {@code
 * traverseOver()} calls.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Automatic detection of {@code Kind<ListKind.Witness, A>} → {@code TraversalPath}
 *   <li>Automatic detection of {@code Kind<MaybeKind.Witness, A>} → {@code AffinePath}
 *   <li>Seamless composition with other fields via the Focus DSL
 *   <li>Type-safe navigation through HKT-wrapped collections
 * </ul>
 *
 * <h2>Generated Code</h2>
 *
 * <p>For the {@code Team} record below, the processor generates:
 *
 * <pre>{@code
 * public final class TeamFocus {
 *     public static FocusPath<Team, String> name() { ... }
 *
 *     public static TraversalPath<Team, Member> members() {
 *         return FocusPath.of(...)
 *             .<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
 *     }
 * }
 * }</pre>
 *
 * @see org.higherkindedj.optics.annotations.GenerateFocus
 * @see org.higherkindedj.optics.annotations.TraverseField
 */
public class KindFieldFocusExample {

  // ============= Domain Model with Kind Fields =============

  /** A skill with a name and proficiency level. */
  public record Skill(String name, int proficiency) {
    /** Creates a skill with increased proficiency. */
    public Skill improve() {
      return new Skill(name, Math.min(100, proficiency + 10));
    }
  }

  /**
   * A team member with Kind-wrapped skills.
   *
   * <p>The {@code skills} field uses {@code Kind<ListKind.Witness, Skill>} to demonstrate
   * HKT-wrapped collection support. The generated Focus class will automatically include {@code
   * traverseOver()} for this field.
   */
  @GenerateFocus
  public record Member(String name, Kind<ListKind.Witness, Skill> skills) {}

  /**
   * A team with Kind-wrapped members and an optional lead member.
   *
   * <p>This record demonstrates both:
   *
   * <ul>
   *   <li>{@code Kind<ListKind.Witness, Member>} → TraversalPath (zero-or-more)
   *   <li>{@code Kind<MaybeKind.Witness, Member>} → AffinePath (zero-or-one)
   * </ul>
   */
  @GenerateFocus
  public record Team(
      String name, Kind<MaybeKind.Witness, Member> lead, Kind<ListKind.Witness, Member> members) {}

  /**
   * A project with Kind-wrapped teams.
   *
   * <p>Demonstrates nested Kind field navigation.
   */
  @GenerateFocus
  public record Project(String name, Kind<ListKind.Witness, Team> teams) {}

  // ============= Example Usage =============

  public static void main(String[] args) {
    System.out.println("=== Kind Field Focus DSL Example ===\n");

    basicKindFieldExample();
    optionalKindFieldExample();
    nestedKindFieldExample();
  }

  /** Demonstrates basic Kind<ListKind.Witness, A> field navigation. */
  static void basicKindFieldExample() {
    System.out.println("--- Basic Kind Field Navigation ---");

    // Create a member with Kind-wrapped skills
    Member alice =
        new Member(
            "Alice",
            ListKindHelper.LIST.widen(
                List.of(new Skill("Java", 90), new Skill("Kotlin", 80), new Skill("Scala", 70))));

    // The generated MemberFocus.skills() returns TraversalPath<Member, Skill>
    // This is automatically generated with traverseOver(ListTraverse.INSTANCE)
    System.out.println("Member: " + alice.name());

    // Get all skills using the generated TraversalPath
    List<Skill> skills = MemberFocus.skills().getAll(alice);
    System.out.println("Skills: " + skills);

    // Improve all skills
    Member improved = MemberFocus.skills().modifyAll(Skill::improve, alice);
    System.out.println("After improvement: " + MemberFocus.skills().getAll(improved));

    // Count skills
    System.out.println("Skill count: " + MemberFocus.skills().count(alice));

    System.out.println();
  }

  /** Demonstrates Kind<MaybeKind.Witness, A> field navigation. */
  static void optionalKindFieldExample() {
    System.out.println("--- Optional Kind Field Navigation ---");

    // Create teams with and without leads
    Team teamWithLead =
        new Team(
            "Alpha",
            MaybeKindHelper.MAYBE.widen(
                Maybe.just(
                    new Member(
                        "Alice", ListKindHelper.LIST.widen(List.of(new Skill("Leadership", 95)))))),
            ListKindHelper.LIST.widen(List.of()));

    Team teamWithoutLead =
        new Team(
            "Beta",
            MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
            ListKindHelper.LIST.widen(List.of()));

    // The generated TeamFocus.lead() returns AffinePath<Team, Member>
    // This is automatically generated with traverseOver(MaybeTraverse.INSTANCE).headOption()
    System.out.println("Team with lead - has lead: " + TeamFocus.lead().matches(teamWithLead));
    System.out.println(
        "Team without lead - has lead: " + TeamFocus.lead().matches(teamWithoutLead));

    // Get lead name if present
    TeamFocus.lead()
        .getOptional(teamWithLead)
        .ifPresent(lead -> System.out.println("Lead name: " + lead.name()));

    System.out.println();
  }

  /** Demonstrates nested Kind field navigation. */
  static void nestedKindFieldExample() {
    System.out.println("--- Nested Kind Field Navigation ---");

    // Create a project with teams and members
    Project project =
        new Project(
            "HKJ",
            ListKindHelper.LIST.widen(
                List.of(
                    new Team(
                        "Core",
                        MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                        ListKindHelper.LIST.widen(
                            List.of(
                                new Member(
                                    "Alice",
                                    ListKindHelper.LIST.widen(
                                        List.of(new Skill("Java", 95), new Skill("FP", 90)))),
                                new Member(
                                    "Bob",
                                    ListKindHelper.LIST.widen(List.of(new Skill("Java", 85))))))),
                    new Team(
                        "Docs",
                        MaybeKindHelper.MAYBE.widen(Maybe.nothing()),
                        ListKindHelper.LIST.widen(
                            List.of(
                                new Member(
                                    "Charlie",
                                    ListKindHelper.LIST.widen(
                                        List.of(new Skill("Writing", 92))))))))));

    // Navigate through nested structures
    System.out.println("Project: " + project.name());

    // Get all teams
    List<Team> teams = ProjectFocus.teams().getAll(project);
    System.out.println("Team count: " + teams.size());

    // Navigate to all members across all teams
    // ProjectFocus.teams() returns TraversalPath<Project, Team>
    // We can chain with TeamFocus.members() to get all members
    for (Team team : teams) {
      List<Member> members = TeamFocus.members().getAll(team);
      System.out.println("Team " + team.name() + " has " + members.size() + " members");

      // Get all skills for each member
      for (Member member : members) {
        List<Skill> memberSkills = MemberFocus.skills().getAll(member);
        System.out.println("  - " + member.name() + ": " + memberSkills.size() + " skills");
      }
    }

    System.out.println();
  }
}
