// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Optic-path segments - labelled paths and composition")
class PathSegmentsTest {

  record Address(String zip, List<String> lines) {}

  record Customer(
      String name, Address address, Optional<String> nickname, List<Address> branches) {}

  private static final Lens<Customer, Address> ADDRESS_LENS =
      Lens.of(Customer::address, (c, a) -> new Customer(c.name(), a, c.nickname(), c.branches()));
  private static final Lens<Address, String> ZIP_LENS =
      Lens.of(Address::zip, (a, z) -> new Address(z, a.lines()));

  private static final FocusPath<Customer, Address> ADDRESS = FocusPath.of(ADDRESS_LENS, "address");
  private static final FocusPath<Address, String> ZIP = FocusPath.of(ZIP_LENS, "zip");
  private static final AffinePath<Address, String> ZIP_AFFINE = ZIP.nullable();
  private static final TraversalPath<Address, String> LINES =
      FocusPath.of(Lens.of(Address::lines, (a, l) -> new Address(a.zip(), l)), "lines").each();
  private static final AffinePath<Customer, Address> ADDRESS_AFFINE = ADDRESS.nullable();
  private static final TraversalPath<Customer, Address> BRANCHES =
      FocusPath.of(
              Lens.of(
                  Customer::branches,
                  (c, b) -> new Customer(c.name(), c.address(), c.nickname(), b)),
              "branches")
          .each();

  private static final Customer CUSTOMER =
      new Customer(
          "Ada",
          new Address("EH1", List.of("a", "b")),
          Optional.of("ace"),
          List.of(new Address("M1", List.of())));

  @Nested
  @DisplayName("Leaves and rendering")
  class Leaves {

    @Test
    @DisplayName("of(lens) is unlabelled; of(lens, segment) carries one segment")
    void leafSegments() {
      assertThat(FocusPath.of(ADDRESS_LENS).segments()).isEmpty();
      assertThat(FocusPath.of(ADDRESS_LENS).pathString()).isEmpty();
      assertThat(ADDRESS.segments()).containsExactly("address");
      assertThat(ADDRESS.pathString()).isEqualTo("address");
    }

    @Test
    @DisplayName("of(lens, segment) rejects a null segment")
    void ofRejectsNullSegment() {
      assertThatNullPointerException()
          .isThrownBy(() -> FocusPath.of(ADDRESS_LENS, null))
          .withMessage("segment must not be null");
    }

    @Test
    @DisplayName("pathString joins segments with dots, matching FieldError.pathString")
    void pathStringJoins() {
      assertThat(ADDRESS.via(ZIP).pathString()).isEqualTo("address.zip");
      assertThat(ADDRESS_AFFINE.via(ZIP).pathString()).isEqualTo("address.zip");
      assertThat(BRANCHES.via(ZIP).pathString()).isEqualTo("branches.zip");
    }
  }

  @Nested
  @DisplayName("Path-with-path composition concatenates segments (3x3 matrix)")
  class CompositionMatrix {

    @Test
    @DisplayName("FocusPath via FocusPath / AffinePath / TraversalPath")
    void focusPathCompositions() {
      assertThat(ADDRESS.via(ZIP).segments()).containsExactly("address", "zip");
      assertThat(ADDRESS.via(ZIP_AFFINE).segments()).containsExactly("address", "zip");
      assertThat(ADDRESS.via(LINES).segments()).containsExactly("address", "lines");
    }

    @Test
    @DisplayName("AffinePath via FocusPath / AffinePath / TraversalPath")
    void affinePathCompositions() {
      assertThat(ADDRESS_AFFINE.via(ZIP).segments()).containsExactly("address", "zip");
      assertThat(ADDRESS_AFFINE.via(ZIP_AFFINE).segments()).containsExactly("address", "zip");
      assertThat(ADDRESS_AFFINE.via(LINES).segments()).containsExactly("address", "lines");
    }

    @Test
    @DisplayName("TraversalPath via FocusPath / AffinePath / TraversalPath")
    void traversalPathCompositions() {
      assertThat(BRANCHES.via(ZIP).segments()).containsExactly("branches", "zip");
      assertThat(BRANCHES.via(ZIP_AFFINE).segments()).containsExactly("branches", "zip");
      assertThat(BRANCHES.via(LINES).segments()).containsExactly("branches", "lines");
    }

    @Test
    @DisplayName("then(path) delegates to via(path), concatenating too")
    void thenDelegates() {
      assertThat(ADDRESS.then(ZIP).segments()).containsExactly("address", "zip");
    }

    @Test
    @DisplayName("unlabelled links contribute nothing on either side")
    void unlabelledLinks() {
      FocusPath<Customer, Address> plain = FocusPath.of(ADDRESS_LENS);
      FocusPath<Address, String> plainZip = FocusPath.of(ZIP_LENS);

      assertThat(plain.via(ZIP).segments()).containsExactly("zip");
      assertThat(ADDRESS.via(plainZip).segments()).containsExactly("address");
      assertThat(plain.via(plainZip).segments()).isEmpty();
    }

    @Test
    @DisplayName("composed labelled paths still read and write correctly")
    void composedPathStillWorks() {
      FocusPath<Customer, String> zip = ADDRESS.via(ZIP);

      assertThat(zip.get(CUSTOMER)).isEqualTo("EH1");
      assertThat(zip.set("EH2", CUSTOMER).address().zip()).isEqualTo("EH2");
    }
  }

  @Nested
  @DisplayName("Raw-optic links and wrappers preserve segments")
  class Preservation {

    @Test
    @DisplayName("composing with a raw optic keeps the left segments unchanged")
    void rawOpticPreserves() {
      assertThat(ADDRESS.via(ZIP_LENS).segments()).containsExactly("address");
    }

    @Test
    @DisplayName("widening links (some / each / nullable) preserve segments")
    void wideningPreserves() {
      FocusPath<Customer, Optional<String>> nickname =
          FocusPath.of(
              Lens.of(
                  Customer::nickname,
                  (c, n) -> new Customer(c.name(), c.address(), n, c.branches())),
              "nickname");

      assertThat(nickname.some().segments()).containsExactly("nickname");
      assertThat(LINES.segments()).containsExactly("lines");
      assertThat(ZIP_AFFINE.segments()).containsExactly("zip");
    }

    @Test
    @DisplayName("a traced traversal delegates segments to its underlying path")
    void tracedDelegates() {
      assertThat(BRANCHES.traced((source, focused) -> {}).segments()).containsExactly("branches");
    }

    @Test
    @DisplayName("filter keeps the path's own segments")
    void filterPreserves() {
      assertThat(BRANCHES.filter(a -> true).segments()).containsExactly("branches");
    }
  }
}
