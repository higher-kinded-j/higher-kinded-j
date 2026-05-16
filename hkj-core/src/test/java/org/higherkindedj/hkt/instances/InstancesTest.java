// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.instances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.ContextMonad;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderMonad;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Instances facade")
class InstancesTest {

  @Nested
  @DisplayName("Zero-argument lookups delegate to the canonical instance")
  class ZeroArg {

    @Test
    @DisplayName("monad(maybe()) returns the MaybeMonad singleton")
    void maybeMonadIsSingleton() {
      Monad<MaybeKind.Witness> m = Instances.monad(maybe());
      assertThat(m).isSameAs(MaybeMonad.INSTANCE);
    }

    @Test
    @DisplayName("applicative/functor view the same canonical object")
    void applicativeAndFunctorViews() {
      Applicative<MaybeKind.Witness> a = Instances.applicative(maybe());
      Functor<MaybeKind.Witness> f = Instances.functor(maybe());
      assertThat(a).isSameAs(MaybeMonad.INSTANCE);
      assertThat(f).isSameAs(MaybeMonad.INSTANCE);
    }

    @Test
    @DisplayName("stateless singletons resolve to their INSTANCE field")
    void statelessSingletons() {
      assertThat(Instances.monad(io())).isSameAs(IOMonad.INSTANCE);
      assertThat(Instances.monad(list())).isSameAs(ListMonad.INSTANCE);
      assertThat(Instances.monad(optional())).isSameAs(OptionalMonad.INSTANCE);
    }

    @Test
    @DisplayName("phantom-typed nullary types infer their type parameter")
    void phantomTypedInference() {
      // <String> flows from the assignment target, matching EitherMonad.<String>instance()
      Monad<EitherKind.Witness<String>> e = Instances.monad(either());
      assertThat(e).isSameAs(EitherMonad.<String>instance());
      assertThat(Instances.monad(id())).isSameAs(IdMonad.instance());
    }

    @Test
    @DisplayName("reader/context/state tokens resolve to the reused phantom singletons")
    void phantomSingletonsAreReused() {
      Monad<ReaderKind.Witness<String>> r = Instances.monad(reader());
      Monad<ContextKind.Witness<String>> c = Instances.monad(context());
      Monad<StateKind.Witness<Integer>> s = Instances.monad(state());

      assertThat(r).isSameAs(ReaderMonad.<String>instance());
      assertThat(c).isSameAs(ContextMonad.<String>instance());
      assertThat(s).isSameAs(StateMonad.<Integer>instance());
      // the token is a thin re-export: repeated calls yield the same canonical object
      assertThat(Instances.monad(state())).isSameAs(s);
    }

    @Test
    @DisplayName("monadError/monadZero/alternative view the same canonical object")
    void richerCapabilityViews() {
      MonadError<MaybeKind.Witness, Unit> me = Instances.monadError(maybe());
      MonadZero<MaybeKind.Witness> mz = Instances.monadZero(maybe());
      Alternative<MaybeKind.Witness> alt = Instances.alternative(maybe());
      assertThat(me).isSameAs(MaybeMonad.INSTANCE);
      assertThat(mz).isSameAs(MaybeMonad.INSTANCE);
      assertThat(alt).isSameAs(MaybeMonad.INSTANCE);

      // error type inferred from the assignment target, like the phantom story
      MonadError<EitherKind.Witness<String>, String> ee = Instances.monadError(either());
      assertThat(ee).isSameAs(EitherMonad.<String>instance());
    }

    @Test
    @DisplayName("monadError on a non-MonadError canonical instance fails fast")
    void monadErrorIsNotTotal() {
      // IO is a Monad but not a MonadError: asking for the wrong capability is a
      // programming error and fails fast, exactly like calling a missing method.
      assertThatThrownBy(() -> Instances.<IOKind.Witness, Object>monadError(io()).raiseError(null))
          .isInstanceOf(ClassCastException.class);
    }
  }

  @Nested
  @DisplayName("Argument-carrying re-exports")
  class ArgumentCarrying {

    @Test
    @DisplayName("validated requires and uses the supplied Semigroup")
    void validated() {
      MonadError<ValidatedKind.Witness<String>, String> v =
          Instances.validated(Semigroups.string());
      assertThat(v).isInstanceOf(MonadError.class);
    }

    @Test
    @DisplayName("writer requires and uses the supplied Monoid")
    void writer() {
      Monad<WriterKind.Witness<String>> w = Instances.writer(Monoids.string());
      assertThat(w).isInstanceOf(Monad.class);
    }

    @Test
    @DisplayName("monad transformers wrap the supplied outer monad")
    void transformers() {
      MonadError<EitherTKind.Witness<MaybeKind.Witness, String>, String> et =
          Instances.eitherT(MaybeMonad.INSTANCE);
      MonadError<MaybeTKind.Witness<MaybeKind.Witness>, Unit> mt =
          Instances.maybeT(MaybeMonad.INSTANCE);
      MonadError<OptionalTKind.Witness<MaybeKind.Witness>, Unit> ot =
          Instances.optionalT(MaybeMonad.INSTANCE);
      Monad<ReaderTKind.Witness<MaybeKind.Witness, Integer>> rt =
          Instances.readerT(MaybeMonad.INSTANCE);
      Monad<StateTKind.Witness<Integer, MaybeKind.Witness>> st =
          Instances.stateT(MaybeMonad.INSTANCE);
      MonadWriter<WriterTKind.Witness<MaybeKind.Witness, String>, String> wt =
          Instances.writerT(MaybeMonad.INSTANCE, Monoids.string());

      assertThat(et).isInstanceOf(MonadError.class);
      assertThat(mt).isInstanceOf(MonadError.class);
      assertThat(ot).isInstanceOf(MonadError.class);
      assertThat(rt).isInstanceOf(Monad.class);
      assertThat(st).isInstanceOf(Monad.class);
      assertThat(wt).isInstanceOf(MonadWriter.class);
    }
  }
}
