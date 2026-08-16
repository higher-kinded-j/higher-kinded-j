// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.WithStatePath;

/**
 * A capability interface marking paths whose work is <em>deferred</em>: composing one describes a
 * computation, and nothing happens until it is run.
 *
 * <p>The distinction this draws is behavioural, and it is the difference between a dropped value
 * and a dropped computation:
 *
 * <pre>{@code
 * Path.just(1).map(v -> log(v));      // MaybePath: eager. log() has already run.
 * Path.io(() -> 1).map(v -> log(v));  // IOPath: deferred. log() never runs.
 * }</pre>
 *
 * <p>Both statements discard their result, but only the second is a silent no-op. That is why the
 * capability is a type-level fact rather than a convention: {@code discarded-effect}, the
 * compile-time check that flags a built-then-dropped effect, is sound only over deferred paths.
 *
 * <p>Eager paths — {@link org.higherkindedj.hkt.effect.MaybePath}, {@link
 * org.higherkindedj.hkt.effect.EitherPath}, {@link org.higherkindedj.hkt.effect.TryPath}, {@link
 * org.higherkindedj.hkt.effect.ValidationPath}, {@link
 * org.higherkindedj.hkt.effect.EitherOrBothPath}, {@link org.higherkindedj.hkt.effect.IdPath},
 * {@link org.higherkindedj.hkt.effect.OptionalPath}, {@link org.higherkindedj.hkt.effect.ListPath},
 * {@link org.higherkindedj.hkt.effect.NonDetPath}, {@link org.higherkindedj.hkt.effect.WriterPath}
 * — hold a value that already exists, so {@code map} and friends apply immediately.
 *
 * <p>{@link org.higherkindedj.hkt.effect.CompletableFuturePath} is deliberately absent: its future
 * is already in flight, so its work happens whether or not the path is kept.
 *
 * <p>{@link org.higherkindedj.hkt.effect.GenericPath} is deliberately absent too. It wraps an
 * arbitrary {@code Kind<F, A>}, so whether it defers depends on {@code F} and cannot be known from
 * the type alone.
 *
 * <p>A new path type must decide which side it falls on; the sealed permits clause makes that an
 * explicit choice rather than an omission.
 *
 * @param <A> the type of the value the deferred computation produces
 */
public sealed interface Deferred<A> extends Composable<A>
    permits FreePath,
        IOPath,
        LazyPath,
        ReaderPath,
        StreamPath,
        TrampolinePath,
        VResultPath,
        VStreamPath,
        VTaskPath,
        WithStatePath {}
