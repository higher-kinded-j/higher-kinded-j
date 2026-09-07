// Fixture for hkj-book/src/tutorials/coretypes/advanced_journey.md
//
// The journey contrasts Coyoneda's map fusion with three traversals, and analyses a Free
// applicative program before running it. The DB algebra it analyses is declared here for real, so
// the processor generates its support and the page names the genuine article.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static bookverify.DbOpKindHelper.DB_OP;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

record Dashboard(String userId) {}

/** The reader's own algebra, declared for real so the processor generates its Kind and helper. */
@EffectAlgebra
sealed interface DbOp<A> permits DbOp.GetUser, DbOp.DeleteUser {

  <B> DbOp<B> mapK(Function<? super A, ? extends B> f);

  record GetUser<A>(String id, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new GetUser<>(id, k.andThen(f));
    }
  }

  record DeleteUser<A>(String id, Function<String, A> k) implements DbOp<A> {
    @Override
    public <B> DbOp<B> mapK(Function<? super A, ? extends B> f) {
      return new DeleteUser<>(id, k.andThen(f));
    }
  }
}

class Fixture {

  static final Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));

  static final List<Integer> numbers = List.of(1, 2, 3);

  static final Functor<ListKind.Witness> listFunctor = Instances.functor(list());

  static final Function<Integer, Integer> f = x -> x + 1;

  static final Function<Integer, Integer> g = x -> x * 2;

  static final Function<Integer, Integer> h = x -> x - 3;

  static final String userId = "u-1";

  static final Free<DbOpKind.Witness, Integer> freeA = sample();

  static final Free<DbOpKind.Witness, Integer> freeB = sample();

  static final FreeAp<DbOpKind.Witness, Integer> freeApA = sample();

  static final FreeAp<DbOpKind.Witness, Integer> freeApB = sample();

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Free<DbOpKind.Witness, Integer> computeB(Integer a) {
    return sample();
  }

  static Integer combine(Integer a, Integer b) {
    return a + b;
  }

  static FreeAp<DbOpKind.Witness, Dashboard> buildDashboard(String userId) {
    return sample();
  }

  static boolean userHasPermission(String action) {
    return true;
  }
}
