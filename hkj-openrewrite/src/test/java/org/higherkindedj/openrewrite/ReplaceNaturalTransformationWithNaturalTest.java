// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Tests for the declarative {@code ReplaceNaturalTransformationWithNatural} recipe, part of {@code
 * MigrateDeprecationsTo0_7_0}, against the real hkj-core and hkj-api.
 */
@DisplayName("ReplaceNaturalTransformationWithNatural")
class ReplaceNaturalTransformationWithNaturalTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
            "org.higherkindedj.openrewrite.ReplaceNaturalTransformationWithNatural")
        .parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify"));
  }

  @Test
  @DisplayName("an interpreter passed to FreePath.foldMapWith becomes a Natural passed to foldMap")
  void freePathInterpreter() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.effect.FreePath;
            import org.higherkindedj.hkt.effect.GenericPath;
            import org.higherkindedj.hkt.effect.NaturalTransformation;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                GenericPath<IdKind.Witness, Integer> run(
                        FreePath<IdKind.Witness, Integer> program, Monad<IdKind.Witness> monad) {
                    NaturalTransformation<IdKind.Witness, IdKind.Witness> same =
                            new NaturalTransformation<>() {
                                @Override
                                public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
                                    return fa;
                                }
                            };
                    return program.foldMapWith(same, monad);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Monad;
            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.effect.FreePath;
            import org.higherkindedj.hkt.effect.GenericPath;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                GenericPath<IdKind.Witness, Integer> run(
                        FreePath<IdKind.Witness, Integer> program, Monad<IdKind.Witness> monad) {
                    Natural<IdKind.Witness, IdKind.Witness> same =
                            new Natural<>() {
                                @Override
                                public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
                                    return fa;
                                }
                            };
                    return program.foldMap(same, monad);
                }
            }
            """));
  }

  @Test
  @DisplayName("FreeApPath.foldMapWith becomes foldMapKind")
  void freeApPathInterpreter() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Applicative;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.effect.FreeApPath;
            import org.higherkindedj.hkt.effect.NaturalTransformation;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Kind<IdKind.Witness, Integer> run(
                        FreeApPath<IdKind.Witness, Integer> program,
                        NaturalTransformation<IdKind.Witness, IdKind.Witness> interpreter,
                        Applicative<IdKind.Witness> applicative) {
                    return program.foldMapWith(interpreter, applicative);
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Applicative;
            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.effect.FreeApPath;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Kind<IdKind.Witness, Integer> run(
                        FreeApPath<IdKind.Witness, Integer> program,
                        Natural<IdKind.Witness, IdKind.Witness> interpreter,
                        Applicative<IdKind.Witness> applicative) {
                    return program.foldMapKind(interpreter, applicative);
                }
            }
            """));
  }

  @Test
  @DisplayName(
      "a VStreamTransformations result kept as a NaturalTransformation is kept as a Natural")
  void vstreamTransformation() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.effect.NaturalTransformation;
            import org.higherkindedj.hkt.effect.VStreamTransformations;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.vstream.VStreamKind;

            class Usage {
                NaturalTransformation<ListKind.Witness, VStreamKind.Witness> toStream() {
                    NaturalTransformation<ListKind.Witness, VStreamKind.Witness> nt =
                            VStreamTransformations.listToVStream();
                    return nt;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.effect.VStreamTransformations;
            import org.higherkindedj.hkt.list.ListKind;
            import org.higherkindedj.hkt.vstream.VStreamKind;

            class Usage {
                Natural<ListKind.Witness, VStreamKind.Witness> toStream() {
                    Natural<ListKind.Witness, VStreamKind.Witness> nt =
                            VStreamTransformations.listToVStream();
                    return nt;
                }
            }
            """));
  }

  @Test
  @DisplayName("a class importing both types keeps one import of Natural")
  void bothImports() {
    rewriteRun(
        java(
            """
            package com.example;

            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.effect.NaturalTransformation;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Natural<IdKind.Witness, IdKind.Witness> widen(
                        NaturalTransformation<IdKind.Witness, IdKind.Witness> nt) {
                    return nt;
                }
            }
            """,
            """
            package com.example;

            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Natural<IdKind.Witness, IdKind.Witness> widen(
                        Natural<IdKind.Witness, IdKind.Witness> nt) {
                    return nt;
                }
            }
            """));
  }

  @Test
  @DisplayName("a static import of identity moves to Natural")
  void staticImportOfIdentity() {
    rewriteRun(
        java(
            """
            package com.example;

            import static org.higherkindedj.hkt.effect.NaturalTransformation.identity;

            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Natural<IdKind.Witness, IdKind.Witness> same() {
                    return identity();
                }
            }
            """,
            """
            package com.example;

            import static org.higherkindedj.hkt.Natural.identity;

            import org.higherkindedj.hkt.Natural;
            import org.higherkindedj.hkt.id.IdKind;

            class Usage {
                Natural<IdKind.Witness, IdKind.Witness> same() {
                    return identity();
                }
            }
            """));
  }
}
