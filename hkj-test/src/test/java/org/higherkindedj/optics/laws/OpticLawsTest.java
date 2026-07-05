// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Optic-law helpers - every family, pass and fail paths")
class OpticLawsTest {

  record Person(String name, Optional<String> nickname) {}

  private static final Lens<Person, String> NAME =
      Lens.of(Person::name, (p, n) -> new Person(n, p.nickname()));
  private static final Person ADA = new Person("Ada", Optional.of("Countess"));
  private static final Person ANON = new Person("Anon", Optional.empty());

  private static final Affine<Person, String> NICKNAME =
      Affine.of(
          Person::nickname,
          (p, n) -> p.nickname().isPresent() ? new Person(p.name(), Optional.of(n)) : p);

  private static final Iso<Integer, String> INT_STRING = Iso.of(String::valueOf, Integer::parseInt);

  private static final Prism<String, Integer> PARSE_INT =
      Prism.of(
          s -> {
            try {
              return Optional.of(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Optional.empty();
            }
          },
          String::valueOf);

  @Nested
  @DisplayName("Lawful optics pass every law")
  class LawfulOptics {

    @Test
    @DisplayName("iso, lens, prism, affine and traversal fixtures are lawful")
    void lawfulFixturesPass() {
      IsoLaws.assertIsoLaws(INT_STRING, 42, "42");
      LensLaws.assertLensLaws(NAME, ADA, "Grace", "Alan");
      PrismLaws.assertPrismLaws(PARSE_INT, "42", "not-a-number");
      AffineLaws.assertAffineLaws(NICKNAME, ADA, ANON, "Lady", "Countess of Lovelace");
      TraversalLaws.assertTraversalLaws(
          Traversals.forList(), List.of("a", "bb"), s -> s + "!", String::toUpperCase);
    }
  }

  @Nested
  @DisplayName("Unlawful optics fail with the law named in the message")
  class UnlawfulOptics {

    @Test
    @DisplayName("a set-ignoring lens fails set-get with a counterexample message")
    void brokenLensFailsSetGet() {
      Lens<Person, String> ignoresSet = Lens.of(Person::name, (p, n) -> p);

      assertThatThrownBy(() -> LensLaws.assertSetGet(ignoresSet, ADA, "Grace"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("set-get")
          .hasMessageContaining("Grace");
    }

    @Test
    @DisplayName("a normalising iso fails the source round trip")
    void normalisingIsoFailsRoundTrip() {
      Iso<String, String> trimming = Iso.of(String::trim, s -> s);

      assertThatThrownBy(() -> IsoLaws.assertGetReverseGet(trimming, "  padded  "))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("get-reverseGet");
    }

    @Test
    @DisplayName("a prism that normalises on build fails match-build")
    void normalisingPrismFailsMatchBuild() {
      Prism<String, Integer> zeroPadded =
          Prism.of(
              s -> {
                try {
                  return Optional.of(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Optional.empty();
                }
              },
              i -> String.format("%03d", i));

      assertThatThrownBy(() -> PrismLaws.assertMatchBuild(zeroPadded, "42"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("match-build");
    }

    @Test
    @DisplayName("an affine that writes into an absent target fails the absence law")
    void writingAffineFailsAbsenceLaw() {
      Affine<Person, String> forcesNickname =
          Affine.of(Person::nickname, (p, n) -> new Person(p.name(), Optional.of(n)));

      assertThatThrownBy(() -> AffineLaws.assertSetNoOpWhenAbsent(forcesNickname, ANON, "Ghost"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("absence");
    }

    @Test
    @DisplayName("guard rails reject vacuous fixtures")
    void guardRailsRejectVacuousFixtures() {
      assertThatThrownBy(() -> LensLaws.assertLensLaws(NAME, ADA, "Same", "Same"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("DISTINCT");
      assertThatThrownBy(() -> PrismLaws.assertMatchBuild(PARSE_INT, "nope"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("MATCHING");
    }
  }
}
