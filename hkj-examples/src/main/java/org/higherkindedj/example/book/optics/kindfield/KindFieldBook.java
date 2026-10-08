// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.kindfield;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/kind_field_support.html">Kind Field Support
 * in Focus DSL</a> page, in its {@code headOption()} section. The page {@code {{#include}}}s the
 * anchored region, and {@code KindFieldBookTest} holds the values its comments claim.
 */
public final class KindFieldBook {

  private KindFieldBook() {}

  /** What the page's read and write through {@code headOption()} return. */
  record HeadOption(Optional<Skill> first, Member flattened) {}

  static HeadOption narrow(Member alice) {
    // ANCHOR: head_option
    TraversalPath<Member, Skill> skills = MemberFocus.skills();

    AffinePath<Member, Skill> firstSkill = skills.headOption();
    Optional<Skill> first = firstSkill.getOptional(alice);
    // Optional[Skill[name=Java, proficiency=95]]

    Member flattened = firstSkill.set(new Skill("Go", 50), alice);
    // BOTH skills are now Skill[name=Go, proficiency=50]: the set is setAll underneath
    // ANCHOR_END: head_option
    return new HeadOption(first, flattened);
  }
}

/** A skill a member holds. */
@GenerateFocus
record Skill(String name, int proficiency) {}

/** A member whose skills are held in a {@code Kind}, which the processor traverses. */
@GenerateFocus
record Member(String name, Kind<ListKind.Witness, Skill> skills) {}
