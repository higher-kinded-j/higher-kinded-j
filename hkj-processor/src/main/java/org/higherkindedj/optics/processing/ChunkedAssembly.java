// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *
 * <p>Chunking through {@code TupleN} ladders, rather than emitting one exact-arity curried {@code
 * ap} chain (the {@link AssemblyProcessor} shape), is deliberate: javac's target-typing of a wide
 * curried lambda grows superquadratically with arity and overflows the compiler's default stack
 * near 200 components, while the chunked shape compiles in time linear in the component count. The
 * chunks rely on the {@code TupleN} family reaching exactly {@link ArityCeilings#ASSEMBLY}, the
 * invariant {@link ArityCeilings} curates.
 */
final class ChunkedAssembly {

  private ChunkedAssembly() {}

  private static final String TUPLE_PACKAGE = "org.higherkindedj.hkt.tuple";

  /**
   * Emits the chunk locals and the combining {@code return} statement for {@code legs.size() >
   * ArityCeilings.ASSEMBLY}. Each leg is a {@code \n.field(...)} fragment in declaration order;
   * {@code construct} receives one value expression per leg, in the same order, and returns the
   * constructor call they assemble into. {@code reserved} carries the enclosing method's parameter
   * names: the emitted locals and lambda parameters ({@code c1..}, {@code t1..}, the singleton
   * ladder's {@code v}) take underscore suffixes until free of them, since a generated local may
   * not redeclare, nor a lambda parameter shadow, a method parameter (JLS 6.4) — and merge methods
   * carry the spec author's own parameter names.
   */
  static CodeBlock emit(
      List<CodeBlock> legs,
      ClassName validated,
      ClassName nel,
      Set<String> reserved,
      Function<List<CodeBlock>, CodeBlock> construct) {
    Set<String> taken = new HashSet<>(reserved);
    CodeBlock.Builder body =
        CodeBlock.builder()
            .add("// Wider than one fields() ladder: chunks combine applicatively; error order\n")
            .add("// and located labels are exactly those of a single ladder.\n");
    List<CodeBlock> values = new ArrayList<>(legs.size());
    List<String> chunkNames = new ArrayList<>();
    List<String> tupleNames = new ArrayList<>();
    for (int leg = 0; leg < legs.size(); ) {
      int chunk = chunkNames.size() + 1;
      String chunkName = free("c" + chunk, taken);
      String tupleName = free("t" + chunk, taken);
      chunkNames.add(chunkName);
      tupleNames.add(tupleName);
      int size = Math.min(ArityCeilings.ASSEMBLY, legs.size() - leg);
      CodeBlock.Builder ladder =
          CodeBlock.builder().add("var $L = $T.fields()", chunkName, validated);
      legs.subList(leg, leg + size).forEach(ladder::add);
      leg += size;
      if (size == 1) {
        String identity = free("v", taken);
        ladder.add("\n.apply($L -> $L)", identity, identity);
        values.add(CodeBlock.of("$L", tupleName));
      } else {
        ladder.add("\n.apply($T::new)", ClassName.get(TUPLE_PACKAGE, "Tuple" + size));
        for (int i = 1; i <= size; i++) {
          values.add(CodeBlock.of("$L._$L()", tupleName, i));
        }
      }
      body.addStatement("$L", ladder.build());
    }
    CodeBlock.Builder curried = CodeBlock.builder();
    for (String tupleName : tupleNames) {
      curried.add("$L -> ", tupleName);
    }
    curried.add("$L", construct.apply(values));
    CodeBlock combined = CodeBlock.of("$L.map($L)", chunkNames.getFirst(), curried.build());
    for (int chunk = 1; chunk < chunkNames.size(); chunk++) {
      combined = CodeBlock.of("$L.ap(\n$L,\n$T.semigroup())", chunkNames.get(chunk), combined, nel);
    }
    return body.addStatement("return $L", combined).build();
  }

  /** The candidate name, underscore-suffixed until free of {@code taken}, then claimed. */
  private static String free(String candidate, Set<String> taken) {
    StringBuilder name = new StringBuilder(candidate);
    while (taken.contains(name.toString())) {
      name.append('_');
    }
    taken.add(name.toString());
    return name.toString();
  }
}
