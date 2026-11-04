// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ReplaceParCollectWithToListRecipe}, against the real hkj-core and hkj-api. */
@DisplayName("ReplaceParCollectWithToListRecipe")
class ReplaceParCollectWithToListRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ReplaceParCollectWithToListRecipe())
        .parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify"));
  }

  /** A class in com.example with the given imports and body, as the tests write it. */
  private static String usage(String imports, String body) {
    return "package com.example;\n\n" + imports + "\nclass Usage {\n" + body + "}\n";
  }

  private static final String STREAM_IMPORTS =
      """
      import java.util.List;
      import org.higherkindedj.hkt.vstream.VStream;
      import org.higherkindedj.hkt.vstream.VStreamPar;
      import org.higherkindedj.hkt.vtask.VTask;
      """;

  private static final String STREAM_IMPORTS_WITHOUT_PAR =
      """
      import java.util.List;
      import org.higherkindedj.hkt.vstream.VStream;
      import org.higherkindedj.hkt.vtask.VTask;
      """;

  @Nested
  @DisplayName("Rewritten")
  class Rewritten {

    @Test
    @DisplayName("VStreamPar.parCollect(stream, n) becomes stream.toList(), and its import goes")
    void vstreamPar() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return VStreamPar.parCollect(orders, 4);
                      }
                  """),
              usage(
                  STREAM_IMPORTS_WITHOUT_PAR,
                  """
                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return orders.toList();
                      }
                  """)));
    }

    @Test
    @DisplayName("a static import of parCollect goes, and an import still in use stays")
    void staticImport() {
      rewriteRun(
          java(
              """
              package com.example;

              import static org.higherkindedj.hkt.vstream.VStreamPar.parCollect;

              import java.util.List;
              import org.higherkindedj.hkt.vstream.VStream;
              import org.higherkindedj.hkt.vstream.VStreamPar;
              import org.higherkindedj.hkt.vtask.VTask;

              class Usage {
                  static final int BATCH = 4;

                  VTask<List<Integer>> all(VStream<Integer> orders) {
                      return parCollect(orders, BATCH);
                  }

                  VStream<Integer> doubled(VStream<Integer> orders) {
                      return VStreamPar.parEvalMap(orders, 4, n -> VTask.succeed(n * 2));
                  }
              }
              """,
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.vstream.VStream;
              import org.higherkindedj.hkt.vstream.VStreamPar;
              import org.higherkindedj.hkt.vtask.VTask;

              class Usage {
                  static final int BATCH = 4;

                  VTask<List<Integer>> all(VStream<Integer> orders) {
                      return orders.toList();
                  }

                  VStream<Integer> doubled(VStream<Integer> orders) {
                      return VStreamPar.parEvalMap(orders, 4, n -> VTask.succeed(n * 2));
                  }
              }
              """));
    }

    @Test
    @DisplayName("path.parCollect(n) becomes path.toList(), on a VStreamPath or its implementation")
    void vstreamPath() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.effect.DefaultVStreamPath;
              import org.higherkindedj.hkt.effect.VStreamPath;
              import org.higherkindedj.hkt.effect.VTaskPath;

              class Usage {
                  VTaskPath<List<String>> all(VStreamPath<String> names) {
                      return names.map(String::trim).parCollect(8);
                  }

                  VTaskPath<List<String>> each(DefaultVStreamPath<String> names) {
                      return names.parCollect(8);
                  }
              }
              """,
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.effect.DefaultVStreamPath;
              import org.higherkindedj.hkt.effect.VStreamPath;
              import org.higherkindedj.hkt.effect.VTaskPath;

              class Usage {
                  VTaskPath<List<String>> all(VStreamPath<String> names) {
                      return names.map(String::trim).toList();
                  }

                  VTaskPath<List<String>> each(DefaultVStreamPath<String> names) {
                      return names.toList();
                  }
              }
              """));
    }

    @Test
    @DisplayName(
        "PathOps.parCollectVStream(path, n) becomes path.toList(), by name or static import")
    void pathOps() {
      rewriteRun(
          java(
              """
              package com.example;

              import static org.higherkindedj.hkt.effect.PathOps.parCollectVStream;

              import java.util.List;
              import org.higherkindedj.hkt.effect.PathOps;
              import org.higherkindedj.hkt.effect.VStreamPath;
              import org.higherkindedj.hkt.effect.VTaskPath;

              class Usage {
                  VTaskPath<List<String>> all(VStreamPath<String> names) {
                      return PathOps.parCollectVStream(names, 2);
                  }

                  VTaskPath<List<String>> again(VStreamPath<String> names) {
                      return parCollectVStream(names, 2);
                  }
              }
              """,
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.effect.VStreamPath;
              import org.higherkindedj.hkt.effect.VTaskPath;

              class Usage {
                  VTaskPath<List<String>> all(VStreamPath<String> names) {
                      return names.toList();
                  }

                  VTaskPath<List<String>> again(VStreamPath<String> names) {
                      return names.toList();
                  }
              }
              """));
    }

    @Test
    @DisplayName("a conditional or a cast stream is put in parentheses")
    void parenthesesWhereNeeded() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> either(boolean first, VStream<Integer> a, VStream<Integer> b) {
                          return VStreamPar.parCollect(first ? a : b, 4);
                      }

                      @SuppressWarnings("unchecked")
                      VTask<List<Integer>> cast(Object raw) {
                          return VStreamPar.parCollect((VStream<Integer>) raw, 4);
                      }
                  """),
              usage(
                  STREAM_IMPORTS_WITHOUT_PAR,
                  """
                      VTask<List<Integer>> either(boolean first, VStream<Integer> a, VStream<Integer> b) {
                          return (first ? a : b).toList();
                      }

                      @SuppressWarnings("unchecked")
                      VTask<List<Integer>> cast(Object raw) {
                          return ((VStream<Integer>) raw).toList();
                      }
                  """)));
    }

    @Test
    @DisplayName("a type witness and the space before the stream go, and a comment stays")
    void witnessAndComment() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return VStreamPar.<Integer>parCollect( /* paid */ orders, 4);
                      }
                  """),
              usage(
                  STREAM_IMPORTS_WITHOUT_PAR,
                  """
                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return /* paid */ orders.toList();
                      }
                  """)));
    }

    @Test
    @DisplayName(
        "a stream with its type arguments written, and a constant batch size, are rewritten")
    void writtenTypeArguments() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      static final class Config {
                          static final int BATCH = 4;
                      }

                      VTask<List<Number>> all() {
                          return VStreamPar.parCollect(VStream.<Number>of(1, 2), Config.BATCH);
                      }
                  """),
              usage(
                  STREAM_IMPORTS_WITHOUT_PAR,
                  """
                      static final class Config {
                          static final int BATCH = 4;
                      }

                      VTask<List<Number>> all() {
                          return VStream.<Number>of(1, 2).toList();
                      }
                  """)));
    }
  }

  @Nested
  @DisplayName("Marked, not rewritten")
  class Marked {

    @Test
    @DisplayName("a computed batch size")
    void computedBatchSize() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      int size() {
                          return 4;
                      }

                      Usage config() {
                          return this;
                      }

                      int batch = 4;

                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return VStreamPar.parCollect(orders, size());
                      }

                      VTask<List<Integer>> again(VStream<Integer> orders) {
                          return VStreamPar.parCollect(orders, config().batch);
                      }
                  """),
              usage(
                  STREAM_IMPORTS,
                  """
                      int size() {
                          return 4;
                      }

                      Usage config() {
                          return this;
                      }

                      int batch = 4;

                      VTask<List<Integer>> all(VStream<Integer> orders) {
                          return /*~~(Not rewritten: the batch size is computed, and toList() takes none; keep what computing it does, then call toList() on the stream)~~>*/VStreamPar.parCollect(orders, size());
                      }

                      VTask<List<Integer>> again(VStream<Integer> orders) {
                          return /*~~(Not rewritten: the batch size is computed, and toList() takes none; keep what computing it does, then call toList() on the stream)~~>*/VStreamPar.parCollect(orders, config().batch);
                      }
                  """)));
    }

    @Test
    @DisplayName("a batch size that is not positive, for which parCollect throws")
    void notPositive() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> none(VStream<Integer> orders) {
                          return VStreamPar.parCollect(orders, 0);
                      }

                      VTask<List<Integer>> negative(VStream<Integer> orders) {
                          return VStreamPar.parCollect(orders, -1);
                      }
                  """),
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> none(VStream<Integer> orders) {
                          return /*~~(Not rewritten: parCollect throws IllegalArgumentException for a batch size that is not positive, and toList() does not; call toList() on the stream if the exception is not wanted)~~>*/VStreamPar.parCollect(orders, 0);
                      }

                      VTask<List<Integer>> negative(VStream<Integer> orders) {
                          return /*~~(Not rewritten: parCollect throws IllegalArgumentException for a batch size that is not positive, and toList() does not; call toList() on the stream if the exception is not wanted)~~>*/VStreamPar.parCollect(orders, -1);
                      }
                  """)));
    }

    @Test
    @DisplayName("a stream whose type the call infers, which toList() would infer differently")
    void inferredStream() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Number>> all() {
                          return VStreamPar.parCollect(VStream.of(1, 2), 4);
                      }
                  """),
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Number>> all() {
                          return /*~~(Not rewritten: the stream's type is inferred from this call; write its type arguments, as in VStream.<Number>of(1, 2), then call toList() on it)~~>*/VStreamPar.parCollect(VStream.of(1, 2), 4);
                      }
                  """)));
    }

    @Test
    @DisplayName("a null or lambda stream, which cannot be the receiver of toList()")
    void noReceiver() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> none() {
                          return VStreamPar.parCollect(null, 4);
                      }

                      VTask<List<Integer>> lambda() {
                          return VStreamPar.parCollect(() -> VTask.succeed(new VStream.Step.Done<>()), 4);
                      }
                  """),
              usage(
                  STREAM_IMPORTS,
                  """
                      VTask<List<Integer>> none() {
                          return /*~~(Not rewritten: a null, a lambda or a method reference cannot be the receiver of toList())~~>*/VStreamPar.parCollect(null, 4);
                      }

                      VTask<List<Integer>> lambda() {
                          return /*~~(Not rewritten: a null, a lambda or a method reference cannot be the receiver of toList())~~>*/VStreamPar.parCollect(() -> VTask.succeed(new VStream.Step.Done<>()), 4);
                      }
                  """)));
    }

    @Test
    @DisplayName("a method reference to parCollect")
    void methodReference() {
      rewriteRun(
          java(
              usage(
                  STREAM_IMPORTS + "import java.util.function.BiFunction;\n",
                  """
                      BiFunction<VStream<Integer>, Integer, VTask<List<Integer>>> collect =
                              VStreamPar::parCollect;
                  """),
              usage(
                  STREAM_IMPORTS + "import java.util.function.BiFunction;\n",
                  """
                      BiFunction<VStream<Integer>, Integer, VTask<List<Integer>>> collect =
                              /*~~(Not rewritten: a method reference to a deprecated parCollect; call toList() on the stream in a lambda instead)~~>*/VStreamPar::parCollect;
                  """)));
    }
  }

  @Test
  @DisplayName("a toList() call and other VStreamPar methods are left alone")
  void othersLeftAlone() {
    rewriteRun(
        java(
            usage(
                STREAM_IMPORTS,
                """
                    VTask<List<Integer>> all(VStream<Integer> orders) {
                        return VStreamPar.parEvalMap(orders, 4, VTask::succeed).toList();
                    }
                """)));
  }
}
