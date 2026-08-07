// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.optics.annotations.ArityCeilings;

/**
 * Emits an accumulating {@code Validated} assembly wider than one {@code Validated.fields()}
 * ladder: the legs split into ladders of at most {@link ArityCeilings#ASSEMBLY} fields, each
 * applied to a {@code TupleN} (a singleton trailing ladder applies the identity), and the chunk
 * results combine with {@code ap} and {@code NonEmptyList.semigroup()} in ladder order. The
 * ladder's {@code and()} is internally that same merge, so labels, located paths and
 * declaration-order accumulation are exactly those of a single ladder. Shared by {@link
 * MappingProcessor} ({@code parse} and {@code patch}) and {@link MergeProcessor} (the fallible
 * merge). The only remaining width bound is the JVM's 255-parameter-slot constructor limit on the
 * assembled record itself, which javac enforces at the record declaration.
 */
final class ChunkedAssembly {

  private ChunkedAssembly() {}

  private static final String TUPLE_PACKAGE = "org.higherkindedj.hkt.tuple";

  /**
   * Emits the chunk locals and the combining {@code return} statement for {@code legs.size() >
   * ArityCeilings.ASSEMBLY}. Each leg is a {@code \n.field(...)} fragment in declaration order;
   * {@code construct} receives one value expression per leg, in the same order, and returns the
   * constructor call they assemble into.
   */
  static CodeBlock emit(
      List<CodeBlock> legs,
      ClassName validated,
      ClassName nel,
      Function<List<CodeBlock>, CodeBlock> construct) {
    CodeBlock.Builder body = CodeBlock.builder();
    List<CodeBlock> values = new ArrayList<>(legs.size());
    int chunkCount = 0;
    for (int leg = 0; leg < legs.size(); ) {
      int chunk = ++chunkCount;
      int size = Math.min(ArityCeilings.ASSEMBLY, legs.size() - leg);
      CodeBlock.Builder ladder = CodeBlock.builder().add("var c$L = $T.fields()", chunk, validated);
      for (int i = 0; i < size; i++, leg++) {
        ladder.add(legs.get(leg));
      }
      if (size == 1) {
        ladder.add("\n.apply(v -> v)");
        values.add(CodeBlock.of("t$L", chunk));
      } else {
        ladder.add("\n.apply($T::new)", ClassName.get(TUPLE_PACKAGE, "Tuple" + size));
        for (int i = 1; i <= size; i++) {
          values.add(CodeBlock.of("t$L._$L()", chunk, i));
        }
      }
      body.addStatement("$L", ladder.build());
    }
    CodeBlock.Builder curried = CodeBlock.builder();
    for (int chunk = 1; chunk <= chunkCount; chunk++) {
      curried.add("t$L -> ", chunk);
    }
    curried.add("$L", construct.apply(values));
    CodeBlock combined = CodeBlock.of("c1.map($L)", curried.build());
    for (int chunk = 2; chunk <= chunkCount; chunk++) {
      combined = CodeBlock.of("c$L.ap(\n$L,\n$T.semigroup())", chunk, combined, nel);
    }
    return body.addStatement("return $L", combined).build();
  }
}
