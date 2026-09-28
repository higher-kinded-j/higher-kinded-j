// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.optics.validated.StandardCodecsScreenTest.Twin;

/**
 * The screened stock codecs against their unscreened twins, as {@link StandardCodecsScreenTest}
 * holds them, on near misses generated from each codec's samples.
 */
class StandardCodecsScreenPropertyTest {

  /**
   * One edit to a sample: a character replaced, removed or inserted. A random string would rarely
   * come near a codec's shape; an edited sample is a near miss by construction.
   */
  @Provide
  Arbitrary<String> nearMisses() {
    Arbitrary<String> sample =
        Arbitraries.of(
            StandardCodecsScreenTest.TWINS.stream()
                .flatMap(twin -> twin.samples().stream())
                .toList());
    Arbitrary<Character> character = Arbitraries.of("0123456789abefxAEZT-+.:/ ".toCharArray());
    return Combinators.combine(sample, Arbitraries.integers().between(0, 40), character)
        .flatAs(
            (text, position, inserted) -> {
              int at = Math.min(position, text.length());
              return Arbitraries.of(
                  text.substring(0, at) + inserted + text.substring(at),
                  at < text.length()
                      ? text.substring(0, at) + inserted + text.substring(at + 1)
                      : text,
                  at < text.length() ? text.substring(0, at) + text.substring(at + 1) : text);
            });
  }

  @Property(tries = 50)
  @Label("every codec agrees with its twin on an edited sample")
  void everyCodecAgreesOnNearMisses(@ForAll("nearMisses") String source) {
    for (Twin twin : StandardCodecsScreenTest.TWINS) {
      if (twin.differs().test(source)) {
        continue;
      }
      assertThat((Object) twin.screened().parse(source))
          .as("%s on \"%s\"", twin.name(), source)
          .isEqualTo(twin.unscreened().parse(source));
    }
  }
}
