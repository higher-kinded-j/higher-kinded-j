// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link AddWidenFieldsForNavigatedContainersRecipe}. */
class AddWidenFieldsForNavigatedContainersRecipeTest implements RewriteTest {

  // @GenerateFocus as 0.4.11 declares it, with no widenFields: the recipe runs before the project
  // moves to 0.5.0.
  private static final String GENERATE_FOCUS =
      "package org.higherkindedj.optics.annotations;"
          + " import java.lang.annotation.*;"
          + " @Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS)"
          + " public @interface GenerateFocus {"
          + "   String targetPackage() default \"\";"
          + "   boolean generateNavigators() default false;"
          + "   int maxNavigatorDepth() default 3;"
          + "   boolean widenCollections() default false;"
          + "   String[] includeFields() default {};"
          + "   String[] excludeFields() default {}; }";

  private static final String IMMUTABLE_LIST =
      "package org.eclipse.collections.api.list; public interface ImmutableList<T> {}";

  private static final String[] STUBS = {
    GENERATE_FOCUS,
    IMMUTABLE_LIST,
    "package com.google.common.collect; public abstract class ImmutableSet<E> {}",
    "package org.pcollections; public interface PMap<K, V> extends java.util.Map<K, V> {}",
  };

  // A widenFields already written compiles only against 0.5.0's @GenerateFocus, which declares it.
  private static final String[] STUBS_WITH_WIDEN_FIELDS =
      Stream.of(STUBS)
          .map(
              stub ->
                  stub.replace(
                      "String[] excludeFields() default {};",
                      "String[] excludeFields() default {}; String[] widenFields() default {};"))
          .toArray(String[]::new);

  private static final String POSITION =
      """
      package com.example;

      import org.higherkindedj.optics.annotations.GenerateFocus;

      @GenerateFocus
      public record Position(String ticker) {}
      """;

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new AddWidenFieldsForNavigatedContainersRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(STUBS));
  }

  @Test
  void addsAMapOfANavigableRecord() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Book(String name, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "positions")
            public record Book(String name, Map<String, Position> positions) {}
            """));
  }

  @Test
  void addsAThirdPartyCollectionOfANavigableRecord() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record AssetClass(String name, ImmutableList<Position> positions) {}
            """,
            """
            package com.example;

            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "positions")
            public record AssetClass(String name, ImmutableList<Position> positions) {}
            """));
  }

  @Test
  void addsSeveralFieldsAsAnArrayInDeclarationOrder() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import com.google.common.collect.ImmutableSet;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            import org.pcollections.PMap;

            @GenerateFocus(generateNavigators = true)
            public record Fund(PMap<String, Position> byTicker, String name, ImmutableSet<Position> held) {}
            """,
            """
            package com.example;

            import com.google.common.collect.ImmutableSet;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            import org.pcollections.PMap;

            @GenerateFocus(generateNavigators = true, widenFields = {"byTicker", "held"})
            public record Fund(PMap<String, Position> byTicker, String name, ImmutableSet<Position> held) {}
            """));
  }

  @Test
  void keepsTheLayoutOfAnAnnotationWrittenOverSeveralLines() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(
                generateNavigators = true,
                maxNavigatorDepth = 2
            )
            public record Book(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(
                generateNavigators = true,
                maxNavigatorDepth = 2,
                widenFields = "positions"
            )
            public record Book(Map<String, Position> positions) {}
            """));
  }

  @Test
  void appendsToAWrittenWidenFieldsArray() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots"})
            public record Book(Map<String, Integer> lots, ImmutableList<Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots", "positions"})
            public record Book(Map<String, Integer> lots, ImmutableList<Position> positions) {}
            """));
  }

  @Test
  void fillsAnEmptyWidenFieldsArray() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {})
            public record Book(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"positions"})
            public record Book(Map<String, Position> positions) {}
            """));
  }

  @Test
  void turnsAWrittenSingleNameIntoAnArrayAndSkipsTheFieldItNames() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "byTicker")
            public record Book(Map<String, Position> byTicker, ImmutableList<Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"byTicker", "positions"})
            public record Book(Map<String, Position> byTicker, ImmutableList<Position> positions) {}
            """));
  }

  @Test
  void addsAFieldThatIncludeFieldsNames() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, includeFields = {"positions"})
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, includeFields = {"positions"}, widenFields = "positions")
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """));
  }

  @Test
  void leavesAFieldThatExcludeFieldsNames() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, excludeFields = "archive")
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, excludeFields = "archive", widenFields = "positions")
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """));
  }

  @Test
  void leavesAContainerWithAWildcardArgument() {
    // 0.4.x refused each of these fields, with an error, rather than step into the container.
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Book(
                Map<String, ? extends Position> positions,
                Map<? extends CharSequence, Position> byName,
                ImmutableList<? extends Position> held) {}
            """));
  }

  @Test
  void leavesARecordThatWidensCollections() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenCollections = true)
            public record Book(Map<String, Position> positions) {}
            """));
  }

  @Test
  void addsWhereWidenCollectionsIsWrittenFalse() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(widenCollections = false, generateNavigators = true)
            public record Book(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(widenCollections = false, generateNavigators = true, widenFields = "positions")
            public record Book(Map<String, Position> positions) {}
            """));
  }

  @Test
  void leavesARecordOnTheDefaultNavigators() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Book(Map<String, Position> positions) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = false)
            public record Ledger(Map<String, Position> positions) {}
            """));
  }

  @Test
  void leavesFieldsNoNavigatorSteppedInto() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            public record Plain(String ticker) {}
            """),
        java(
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Box<T>(T value) {}
            """),
        java(
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public final class Account {}
            """),
        java(
            """
            package com.example;

            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Book(
                Map<String, Plain> plain,
                Map<String, Box<String>> boxes,
                HashMap<String, Position> hashed,
                Map<String, Box> rawBoxes,
                Map<String, Account> accounts,
                Map<String, String> names,
                Map<String, List<Position>> nested,
                Position[] array,
                List<Position> list,
                Map raw) {}
            """));
  }

  @Test
  void leavesARecordWhoseFieldListsAreNotLiterals() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, excludeFields = Book.HIDDEN)
            public record Book(Map<String, Position> positions) {
                static final String HIDDEN = "positions";
            }
            """));
  }

  @Test
  void widensARecordNestedInAClassBesideAnotherAnnotation() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            class Model {
                @Deprecated
                @GenerateFocus(generateNavigators = true)
                record Book(Map<String, Position> positions) {}
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            class Model {
                @Deprecated
                @GenerateFocus(generateNavigators = true, widenFields = "positions")
                record Book(Map<String, Position> positions) {}
            }
            """));
  }

  @Test
  void movesATrailingCommaToTheNewLastName() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots",})
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots", "positions",})
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """));
  }

  @Test
  void keepsACommentAfterATrailingCommaWithItsName() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {
                "lots", // the lots
            })
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {
                "lots", // the lots
                "positions",
            })
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots" /* the lots */})
            public record Ledger(Map<String, Integer> lots, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lots" /* the lots */, "positions"})
            public record Ledger(Map<String, Integer> lots, Map<String, Position> positions) {}
            """));
  }

  @Test
  void keepsWindowsLineEndings() {
    String before =
        """
        package com.example;

        import java.util.Map;
        import org.higherkindedj.optics.annotations.GenerateFocus;

        @GenerateFocus(
            generateNavigators = true,
            maxNavigatorDepth = 2)
        public record Book(Map<String, Position> positions) {}
        """;
    String after =
        """
        package com.example;

        import java.util.Map;
        import org.higherkindedj.optics.annotations.GenerateFocus;

        @GenerateFocus(
            generateNavigators = true,
            maxNavigatorDepth = 2,
            widenFields = "positions")
        public record Book(Map<String, Position> positions) {}
        """;
    rewriteRun(java(POSITION), java(before.replace("\n", "\r\n"), after.replace("\n", "\r\n")));
  }

  @Test
  void keepsCommentsWithWhatTheyDescribe() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(
                generateNavigators /* asked for */ = true, // navigators
                maxNavigatorDepth = 2)
            public record Book(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(
                generateNavigators /* asked for */ = true, // navigators
                maxNavigatorDepth = 2,
                widenFields = "positions")
            public record Book(Map<String, Position> positions) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {
                "lots" // the lots
            })
            public record Ledger(Map<String, Integer> lots, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {
                "lots", // the lots
                "positions"
            })
            public record Ledger(Map<String, Integer> lots, Map<String, Position> positions) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {/* none yet */})
            public record Desk(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {/* none yet */"positions"})
            public record Desk(Map<String, Position> positions) {}
            """));
  }

  @Test
  void keepsAWrittenNameAsItWasWritten() {
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(STUBS_WITH_WIDEN_FIELDS)),
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "lo\\u0074s")
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = {"lo\\u0074s", "positions"})
            public record Book(Map<String, Integer> lots, Map<String, Position> positions) {}
            """));
  }

  @Test
  void findsTheAnnotationAfterAModifier() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            public @GenerateFocus(generateNavigators = true) record Book(Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            public @GenerateFocus(generateNavigators = true, widenFields = "positions") record Book(Map<String, Position> positions) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            class Model {
                public @GenerateFocus(generateNavigators = true) static record Desk(Map<String, Position> positions) {}
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            class Model {
                public @GenerateFocus(generateNavigators = true, widenFields = "positions") static record Desk(Map<String, Position> positions) {}
            }
            """));
  }

  @Test
  void leavesAnElementTheNavigatorsPackageCannotName() {
    // 0.4.x gave no navigator into a record whose navigator would name a type its package cannot
    // reach, so widening the field would change what its method returns.
    rewriteRun(
        java(
            """
            package com.example.model;

            record Secret(String code) {}
            """),
        java(
            """
            package com.example.model;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Vault(String name, Secret secret) {}
            """),
        java(
            """
            package com.example.model;

            class Cellar {
                public static final class Rack {}
            }
            """),
        java(
            """
            package com.example.model;

            import java.util.List;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Ledger(List<Secret> secrets) {}
            """),
        java(
            """
            package com.example.model;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Archive(Secret[] secrets) {}
            """),
        java(
            """
            package com.example.model;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Register(Map<String, ? extends Secret> secrets) {}
            """),
        java(
            """
            package com.example.model;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Wine(Cellar.Rack rack) {}
            """),
        java(
            """
            package com.example.model;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            record Teller(String name) {}
            """),
        java(
            """
            package com.example;

            import com.example.model.Vault;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Bank(Map<String, Vault> vaults) {}
            """),
        java(
            """
            package com.example;

            import com.example.model.Archive;
            import com.example.model.Ledger;
            import com.example.model.Register;
            import com.example.model.Wine;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Head(
                Map<String, Ledger> ledgers,
                Map<String, Archive> archives,
                Map<String, Register> registers,
                Map<String, Wine> wines) {}
            """),
        java(
            """
            package com.example.model;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Branch(Map<String, Teller> tellers) {}
            """,
            """
            package com.example.model;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "tellers")
            public record Branch(Map<String, Teller> tellers) {}
            """),
        java(
            """
            package com.example.model;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, targetPackage = "com.example.optics")
            public record Office(Map<String, Vault> vaults) {}
            """),
        java(
            """
            package com.example;

            import com.example.model.Vault;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, targetPackage = "com.example.model")
            public record Strongroom(Map<String, Vault> vaults) {}
            """,
            """
            package com.example;

            import com.example.model.Vault;
            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, targetPackage = "com.example.model", widenFields = "vaults")
            public record Strongroom(Map<String, Vault> vaults) {}
            """),
        java(
            """
            package com.example.model;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, targetPackage = Kiosk.OPTICS)
            public record Kiosk(Map<String, Teller> tellers) {
                static final String OPTICS = "com.example.model";
            }
            """));
  }

  @Test
  void marksAFieldWhoseElementsHaveAFieldNamedLikeAPathMethod() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Line(String head, int qty) {}
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Book(ImmutableList<Line> lines, Map<String, Position> positions) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "positions")
            public record Book(/*~~(0.5.0 gives 'lines' no navigator while Line.head is named like a path method, and lines().head() would call the path's own head(): rename it while on 0.4.x, where the compiler finds each call through the old name, then add 'lines' to widenFields)~~>*/ImmutableList<Line> lines, Map<String, Position> positions) {}
            """));
  }

  @Test
  void leavesARecordWhoseSettingsAreNotLiterals() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = Book.NAVIGATE)
            public record Book(Map<String, Position> positions) {
                static final boolean NAVIGATE = true;
            }
            """),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenCollections = Ledger.WIDEN)
            public record Ledger(Map<String, Position> positions) {
                static final boolean WIDEN = false;
            }
            """));
  }

  @Test
  void readsIncludeFieldsBeforeExcludeFields() {
    rewriteRun(
        java(POSITION),
        java(
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, includeFields = "positions", excludeFields = "positions")
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """,
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, includeFields = "positions", excludeFields = "positions", widenFields = "positions")
            public record Book(Map<String, Position> positions, Map<String, Position> archive) {}
            """));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "java.util.Map, 2",
    "org.eclipse.collections.api.list.ImmutableList, 1",
    "org.eclipse.collections.api.list.MutableList, 1",
    "org.eclipse.collections.api.set.ImmutableSet, 1",
    "org.eclipse.collections.api.set.MutableSet, 1",
    "org.eclipse.collections.api.bag.ImmutableBag, 1",
    "org.eclipse.collections.api.bag.MutableBag, 1",
    "org.eclipse.collections.api.set.sorted.ImmutableSortedSet, 1",
    "org.eclipse.collections.api.set.sorted.MutableSortedSet, 1",
    "com.google.common.collect.ImmutableList, 1",
    "com.google.common.collect.ImmutableSet, 1",
    "io.vavr.collection.List, 1",
    "io.vavr.collection.Set, 1",
    "org.apache.commons.collections4.bag.HashBag, 1",
    "org.apache.commons.collections4.list.UnmodifiableList, 1",
    "org.pcollections.PVector, 1",
    "org.pcollections.PStack, 1",
    "org.pcollections.PSet, 1",
    "org.pcollections.PSortedSet, 1",
    "org.pcollections.PBag, 1",
    "org.pcollections.PMap, 2",
    "org.pcollections.PSortedMap, 2",
  })
  void addsEachContainerTheTableLists(String container, int arity) {
    String simpleName = container.substring(container.lastIndexOf('.') + 1);
    String packageName = container.substring(0, container.lastIndexOf('.'));
    String parameters = arity == 1 ? "<E>" : "<K, V>";
    String arguments = arity == 1 ? "<Position>" : "<String, Position>";
    // java.util.Map is the JDK's own; every other container is stubbed with its own type
    // parameters.
    String[] stubs =
        container.equals("java.util.Map")
            ? new String[] {GENERATE_FOCUS}
            : new String[] {
              GENERATE_FOCUS,
              "package " + packageName + "; public interface " + simpleName + parameters + " {}"
            };
    rewriteRun(
        spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(stubs)),
        java(POSITION),
        java(
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Holder(%s%s held) {}
            """
                .formatted(container, arguments),
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "held")
            public record Holder(%s%s held) {}
            """
                .formatted(container, arguments)));
  }

  @Test
  void recognisesARecordReadFromAnotherModulesClasses(@TempDir Path classes) {
    // The element and the annotation are compiled ahead, so Position reaches the recipe as a class
    // file carrying @GenerateFocus with CLASS retention, as a record from another module does.
    compile(
        classes,
        Map.of(
            "GenerateFocus",
            GENERATE_FOCUS,
            "Position",
            "package com.example.model;"
                + " import org.higherkindedj.optics.annotations.GenerateFocus;"
                + " @GenerateFocus public record Position(String ticker) {}"));
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion().classpath(List.of(classes)).dependsOn(IMMUTABLE_LIST)),
        java(
            """
            package com.example;

            import com.example.model.Position;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record AssetClass(String name, ImmutableList<Position> positions) {}
            """,
            """
            package com.example;

            import com.example.model.Position;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "positions")
            public record AssetClass(String name, ImmutableList<Position> positions) {}
            """));
  }

  @Test
  void leavesARecordReadFromClassesWithoutTheAnnotation(@TempDir Path classes) {
    compile(
        classes,
        Map.of(
            "GenerateFocus",
            GENERATE_FOCUS,
            "Position",
            "package com.example.model; public record Position(String ticker) {}"));
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion().classpath(List.of(classes)).dependsOn(IMMUTABLE_LIST)),
        java(
            """
            package com.example;

            import com.example.model.Position;
            import org.eclipse.collections.api.list.ImmutableList;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record AssetClass(String name, ImmutableList<Position> positions) {}
            """));
  }

  @Test
  void pathMethodsAreThoseATraversalNavigatorInherits() throws IOException {
    // Read as the processor reads them: each member of NavigatorTraversalPath taking no arguments,
    // apart from those named as Object's are, which JLS 8.10.1 forbids a record component to be.
    JavacTask javac =
        (JavacTask)
            ToolProvider.getSystemJavaCompiler()
                .getTask(
                    null,
                    null,
                    null,
                    List.of(
                        "--release",
                        String.valueOf(Runtime.version().feature()),
                        "--enable-preview",
                        "-proc:none",
                        "-cp",
                        System.getProperty("java.class.path")),
                    null,
                    List.of(new Source("Probe", "class Probe {}")));
    javac.analyze();
    Elements elements = javac.getElements();
    Set<String> objectMethods =
        noArgumentMethods(elements, elements.getTypeElement("java.lang.Object"));
    Set<String> inherited =
        noArgumentMethods(
            elements,
            elements.getTypeElement("org.higherkindedj.optics.focus.NavigatorTraversalPath"));
    inherited.removeAll(objectMethods);
    assertThat(AddWidenFieldsForNavigatedContainersRecipe.TRAVERSAL_PATH_METHODS)
        .containsExactlyInAnyOrderElementsOf(inherited);
  }

  /** The names of the methods taking no arguments that a type declares or inherits. */
  private static Set<String> noArgumentMethods(Elements elements, TypeElement type) {
    assertThat(type).as("the type is on the test classpath").isNotNull();
    return ElementFilter.methodsIn(elements.getAllMembers(type)).stream()
        .filter(method -> method.getParameters().isEmpty())
        .map(method -> method.getSimpleName().toString())
        .collect(Collectors.toCollection(HashSet::new));
  }

  /**
   * Compiles the sources, each keyed by the public type it declares, into the directory, with javac
   * from the running JDK.
   */
  private static void compile(Path classes, Map<String, String> sources) {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertThat(javac).as("the running JDK has a compiler").isNotNull();
    List<JavaFileObject> units =
        sources.entrySet().stream()
            .<JavaFileObject>map(source -> new Source(source.getKey(), source.getValue()))
            .toList();
    boolean compiled =
        javac
            .getTask(null, null, null, List.of("-proc:none", "-d", classes.toString()), null, units)
            .call();
    assertThat(compiled).as("the classpath fixture compiles").isTrue();
  }

  /** A source held in memory, named for the public type it declares. */
  private static final class Source extends SimpleJavaFileObject {
    private final String code;

    Source(String publicType, String code) {
      super(URI.create("string:///" + publicType + ".java"), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }
}
