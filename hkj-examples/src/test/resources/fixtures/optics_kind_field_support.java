// Fixture for hkj-book/src/optics/kind_field_support.md
//
// The page's records hold Kind<F, A> fields; the annotation processor reads
// the witness type and generates the traversal, so the snippets assert the
// generated path types by assigning them.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Skill(String name, int proficiency) {}

@GenerateLenses
@GenerateFocus
record Member(String name, Kind<ListKind.Witness, Skill> skills) {}

@GenerateLenses
@GenerateFocus
record Team(String name, Kind<ListKind.Witness, Member> members) {}

@GenerateLenses
@GenerateFocus
record ApiResponse(
    String requestId,
    Kind<MaybeKind.Witness, Member> lead,
    Kind<ListKind.Witness, String> warnings,
    Kind<EitherKind.Witness<String>, String> result) {}

class Fixture {
  static final Member alice =
      new Member("Alice", LIST.widen(List.of(new Skill("Java", 95), new Skill("SQL", 40))));

  static final Member bob = new Member("Bob", LIST.widen(List.of(new Skill("Java", 30))));

  static final Team team = new Team("Platform", LIST.widen(List.of(alice, bob)));

  static final ApiResponse response =
      new ApiResponse(
          "req-1",
          MAYBE.widen(Maybe.just(alice)),
          LIST.widen(List.of("deprecated field")),
          EitherKindHelper.EITHER.widen(Either.right("ok")));

  static Skill improve(Skill skill) {
    return new Skill(skill.name(), Math.min(100, skill.proficiency() + 10));
  }
}
