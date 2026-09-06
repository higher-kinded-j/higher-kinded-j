// Fixture for hkj-book/src/glossary/type-system.md
//
// The entries that quote the library's own declarations (Kind, WitnessArity, TypeArity, a Kind
// interface's nested Witness) are left unmarked: the real type cannot be declared beside itself.
// What is gated is each entry's worked half, and this supplies what it elides.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.optional.OptionalKind;

class Fixture {}
