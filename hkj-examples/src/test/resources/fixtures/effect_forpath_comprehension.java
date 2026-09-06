// Fixture for hkj-book/src/effect/forpath_comprehension.md
//
// The page contrasts the standard For class with ForPath, then walks each comprehension
// operation. Two MaybePaths stand in for the steps being composed; the optics sections bring
// their own user and their own sum type.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

record Address(String city, String postcode) {}

record User(String name, Address address) {}

class Fixture {

  static final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());

  static final MaybePath<Integer> path1 = Path.just(1);

  static final MaybePath<Integer> path2 = Path.just(2);

  static final User user = new User("Ada", new Address("London", "N1"));

  static final Lens<User, Address> addressLens =
      Lens.of(User::address, (u, a) -> new User(u.name(), a));

  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (a, c) -> new Address(c, a.postcode()));
}
