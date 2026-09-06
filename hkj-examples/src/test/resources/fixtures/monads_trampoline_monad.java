// Fixture for hkj-book/src/monads/trampoline_monad.md
//
// The page turns one deeply recursive factorial into a trampolined one, so the trampolined
// `factorial` is what its later snippets pick up and transform.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trampoline.TrampolineUtils;

class Fixture {

  static Trampoline<BigInteger> factorial(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ZERO) <= 0) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(() -> factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
  }
}
