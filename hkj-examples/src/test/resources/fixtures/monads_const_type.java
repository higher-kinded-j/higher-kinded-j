// Fixture for hkj-book/src/monads/const_type.md
//
// The page carries one `Const` through the bifunctor operations, so the bifunctor instance and the
// phantom-typed subject are declared once here rather than repeated in every snippet.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.constant.ConstKind2;

record Person(String name, int age, String city) {}

class Fixture {

  static final Bifunctor<ConstKind2.Witness> bifunctor = ConstBifunctor.INSTANCE;

  static final Const<String, Integer> const_ = new Const<>("value");
}
