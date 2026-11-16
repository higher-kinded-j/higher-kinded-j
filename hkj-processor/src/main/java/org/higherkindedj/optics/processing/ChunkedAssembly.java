// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Emits an accumulating {@code Validated} assembly wider than one {@code Validated.fields()}
 * ladder: the legs split into ladders of at most {@link ArityCeilings#ASSEMBLY} fields, each
 * applied to a {@code TupleN} (a singleton trailing ladder applies the identity), and the chunk
 * results combine with {@code ap} and {@code NonEmptyList.semigroup()} in ladder order. The
 * ladder's {@code and()} is internally that same merge, so labels, located paths and
 * declaration-order accumulation are exactly those of a single ladder, and the combined chunks end
 * in the same {@link GuardedConstruction} a single ladder does. Shared by {@link MappingProcessor}
 * ({@code parse} and {@code patch}) and {@link MergeProcessor} (the fallible merge). The only
 * remaining width bound is the JVM's 255-parameter-slot constructor limit on the assembled record
 * itself, which javac enforces at the record declaration.
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
   * {@code type} constructor thunk they assemble into ({@link GuardedConstruction#thunk} or {@link
   * GuardedConstruction#boundThunk}), whose call is guarded as a single ladder's is. {@code
   * reserved} carries every name the emitted locals and lambda parameters ({@code c1..}, {@code
   * t1..}, the singleton ladder's {@code v}) must stay clear of, and they take underscore suffixes
   * until free of them: the enclosing method's parameters, since a generated local may not
   * redeclare, nor a lambda parameter shadow, a method parameter (JLS 6.4), and merge methods carry
   * the spec author's own parameter names; and the names a leg or the thunk declares itself, which
   * sit where the chunk locals are in scope.
   *
   * <p>Where {@code declared} names the legs' types ({@link GuardedConstruction#parameterTypes}),
   * every lambda declares its parameters with them, as a single ladder's terminal does: each chunk
   * builds its tuple from parameters it declares, as a tuple of those types written out, {@code
   * (List<@Nullable String> v1, ...) -> new Tuple16<List<@Nullable String>, ...>(v1, ...)}. Its
   * type arguments are then exactly those types, never the capture a wildcard-typed parameter reads
   * as, so the combining lambda can name the tuple it takes.
   */
  static CodeBlock emit(
      List<CodeBlock> legs,
      ClassName validated,
      ClassName nel,
      Set<String> reserved,
      TypeName type,
      List<TypeName> declared,
      Function<List<CodeBlock>, CodeBlock> construct) {
    Set<String> taken = new HashSet<>(reserved);
    boolean typed = !declared.isEmpty();
    CodeBlock.Builder body =
        CodeBlock.builder()
            .add("// Wider than one fields() ladder: chunks combine applicatively; error order\n")
            .add("// and located labels are exactly those of a single ladder.\n");
    List<CodeBlock> values = new ArrayList<>(legs.size());
    List<String> chunkNames = new ArrayList<>();
    List<String> tupleNames = new ArrayList<>();
    List<TypeName> tupleTypes = new ArrayList<>();
    for (int leg = 0; leg < legs.size(); ) {
      int chunk = chunkNames.size() + 1;
      String chunkName = ProcessorUtils.freeName("c" + chunk, taken);
      String tupleName = ProcessorUtils.freeName("t" + chunk, taken);
      chunkNames.add(chunkName);
      tupleNames.add(tupleName);
      int size = Math.min(ArityCeilings.ASSEMBLY, legs.size() - leg);
      CodeBlock.Builder ladder =
          CodeBlock.builder().add("var $L = $T.fields()", chunkName, validated);
      legs.subList(leg, leg + size).forEach(ladder::add);
      List<TypeName> chunkTypes = typed ? declared.subList(leg, leg + size) : List.of();
      leg += size;
      if (size == 1) {
        String identity = ProcessorUtils.freeName("v", taken);
        TypeName only = typed ? chunkTypes.getFirst() : null;
        ladder.add("\n.apply($L -> $L)", head(identity, only), identity);
        values.add(CodeBlock.of("$L", tupleName));
        tupleTypes.add(only);
      } else {
        ClassName tuple = ClassName.get(TUPLE_PACKAGE, "Tuple" + size);
        if (!typed) {
          ladder.add("\n.apply($T::new)", tuple);
          tupleTypes.add(null);
        } else {
          List<String> params = new ArrayList<>(size);
          for (int i = 0; i < size; i++) {
            params.add(ProcessorUtils.freeName("v" + (i + 1), taken));
          }
          TypeName tupleType =
              ParameterizedTypeName.get(tuple, chunkTypes.toArray(TypeName[]::new));
          ladder.add(
              "\n.apply(($L) -> new $T($L))",
              GuardedConstruction.parameters(params, chunkTypes),
              tupleType,
              String.join(", ", params));
          tupleTypes.add(tupleType);
        }
        for (int i = 1; i <= size; i++) {
          values.add(CodeBlock.of("$L._$L()", tupleName, i));
        }
      }
      body.addStatement("$L", ladder.build());
    }
    CodeBlock.Builder curried = CodeBlock.builder();
    for (int chunk = 0; chunk < tupleNames.size(); chunk++) {
      curried.add("$L -> ", head(tupleNames.get(chunk), tupleTypes.get(chunk)));
    }
    curried.add("$L", construct.apply(values));
    CodeBlock combined = CodeBlock.of("$L.map($L)", chunkNames.getFirst(), curried.build());
    for (int chunk = 1; chunk < chunkNames.size(); chunk++) {
      combined =
          CodeBlock.of(
              "$L.ap(\n$>$>$L,\n$T.semigroup()$<$<)", chunkNames.get(chunk), combined, nel);
    }
    return body.add(GuardedConstruction.returning(combined, type)).build();
  }

  /**
   * A one-parameter lambda's head: the name, {@code t1}, or, where {@code type} is given, the
   * declared parameter, {@code (Tuple2<A, B> t1)}.
   */
  private static CodeBlock head(String name, TypeName type) {
    return type == null
        ? CodeBlock.of("$L", name)
        : CodeBlock.of("($L)", GuardedConstruction.parameters(List.of(name), List.of(type)));
  }
}
