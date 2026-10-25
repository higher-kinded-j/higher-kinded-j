// Fixture for hkj-book/src/optics/compiler_errors.md
//
// Almost every entry on that page carries a minimal declaration that provokes the message the
// heading quotes, under `<!-- verify:rejects -->` or `<!-- verify:reports -->`. Those reproducers
// declare whatever is peculiar to them; what several of them share lives here, along with the
// imports they all elide.
//
// The Focus DSL and Free monad entries are the exception: they quote *working* code, so they are
// ordinary `<!-- verify -->` snippets over the org chart and the saver program below.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.Audited;
import org.higherkindedj.example.book.optics.BaseEndpoint;
import org.higherkindedj.example.book.optics.Endpoint;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.InstanceOf;
import org.higherkindedj.optics.annotations.MatchWhen;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ThroughField;
import org.higherkindedj.optics.annotations.TraverseField;
import org.higherkindedj.optics.annotations.ViaConstructor;
import org.higherkindedj.optics.annotations.ViaBuilder;
import org.higherkindedj.optics.annotations.ViaCopyAndSet;
import org.higherkindedj.optics.annotations.Wither;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;

/** The org chart the Focus DSL entries navigate. */
@GenerateLenses
@GenerateFocus
record OrgMember(String name, int age, Optional<String> email, Integer salary) {}

@GenerateLenses
@GenerateFocus
record OrgUnit(String name, List<OrgMember> members) {}

@GenerateLenses
@GenerateFocus
record OrgChart(String name, List<OrgUnit> units) {}

/** The record the Free monad entries build a program over. */
record Saver(String name, int age) {}

/** A `Kind`-typed component, which is what `fromTraverse` is for. */
record Role(String name) {}

record Operator(String name, Kind<ListKind.Witness, Role> roles) {}

/** A single-component record: no two-argument constructor for `Lens.of` to reach. */
record Barcode(String value) {}

record ShelfLabel(Barcode barcode) {}

class Fixture {

  /**
   * Written by hand rather than generated: `@GenerateFocus` recognises `ListKind.Witness` and would
   * hand back a `TraversalPath` already traversed, which is the very step the entry is about.
   */
  static final FocusPath<Operator, Kind<ListKind.Witness, Role>> rolesPath =
      FocusPath.of(Lens.of(Operator::roles, (user, roles) -> new Operator(user.name(), roles)));

  static final Saver person = new Saver("Ada", 36);

  /**
   * Written against a hand-made lens rather than a generated one on purpose: a snippet under
   * `verify:rejects` stops annotation processing, so a fixture that named `SaverLenses` would add
   * a cascading "cannot find symbol" to every refusal on the page.
   */
  static final Free<OpticOpKind.Witness, Saver> program =
      OpticPrograms.set(
          person, Lens.of(Saver::age, (subject, age) -> new Saver(subject.name(), age)), 37);
}
