// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.optics.laws.MappingLaws;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answers on the book's Check Your Understanding page. Each answer that predicts a result
 * includes one of the anchored regions below, so what the page says comes back is what this green
 * test asserts.
 */
@DisplayName("the book's self-check answers hold")
class SelfCheckBookTest {

  @Test
  @DisplayName("a null and a bad email in a nested record are both reported, each by its path")
  void nullAndBadLeafAccumulateThroughNesting() {
    // ANCHOR: null_and_leaf
    assertThatValidated(
            InvoiceMappingImpl.INSTANCE.parse(
                new InvoiceDto("INV-2", new CustomerDto(null, "not-an-email"))))
        .isInvalid()
        .hasFieldErrors("customer.name: must not be null", "customer.email: not an email address");
    // ANCHOR_END: null_and_leaf
  }

  @Test
  @DisplayName("a record's constructor runs only once every component has parsed")
  void constructorRunsLast() {
    // ANCHOR: constructor_last
    ReservationDto request =
        new ReservationDto(
            null, // no guest
            List.of(
                new StayDto("2026-03-01", "2026-03-04"),
                new StayDto("2026-03-09", "07/03/2026"))); // meant to leave before it arrives

    assertThatValidated(ReservationMappingImpl.INSTANCE.parse(request))
        .isInvalid()
        .hasFieldErrors(
            "guest: must not be null",
            "stays.1.checkOut: not an ISO-8601 date (expected e.g. 2026-07-28)");
    // ANCHOR_END: constructor_last
  }

  @Test
  @DisplayName(
      "an instance bound on the spec reads null or not, by which class a program uses first")
  void instanceBoundOnTheSpecDependsOnWhatRunsFirst() throws Exception {
    // ANCHOR: trap_proof
    // Two programs, each loading this package afresh, so neither sees what the other ran:
    assertThat(mapperAfterFirstUsing("VisitorMapping")).isNotNull(); // the spec first
    assertThat(mapperAfterFirstUsing("VisitorMappingImpl")).isNull(); // the Impl first
    // ANCHOR_END: trap_proof
  }

  @Test
  @DisplayName("the shipment spec renames, locates a bad parcel by index, and reads a null note")
  void shipmentSpecLocatesAndBridges() {
    // ANCHOR: shipment_proof
    ShipmentMappingImpl shipments = ShipmentMappingImpl.INSTANCE;

    assertThatValidated(
            shipments.parse(
                new ShipmentDto(
                    "not-a-uuid",
                    List.of(new ParcelDto("A-1", 250), new ParcelDto(null, 90)),
                    null)))
        .isInvalid()
        .hasFieldErrors(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "parcels.1.sku: must not be null"); // the domain's name, not the wire's `items`

    UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertThatValidated(
            shipments.parse(
                new ShipmentDto(id.toString(), List.of(new ParcelDto("A-1", 250)), null)))
        .hasValue(new Shipment(id, List.of(new Parcel("A-1", 250)), Optional.empty()));
    // ANCHOR_END: shipment_proof
  }

  @Test
  @DisplayName("the shipment spec maps both ways: its build and parse obey the mapping laws")
  void shipmentSpecObeysTheLaws() {
    MappingLaws.assertMappingLaws(
        ShipmentMappingImpl.INSTANCE.asValidatedPrism(),
        new ShipmentDto(
            "123e4567-e89b-12d3-a456-426614174000", List.of(new ParcelDto("A-1", 250)), null),
        new ShipmentDto("not-a-uuid", List.of(new ParcelDto(null, 90)), null));
  }

  /**
   * Runs one program's first use of {@code first}, in a class loader that defines this package
   * afresh, and reads the spec's constant afterwards. A fresh loader gives each order its own class
   * initialisation, which in the shared test JVM happens once, to whichever test gets there first.
   */
  private static @Nullable Object mapperAfterFirstUsing(String first) throws Exception {
    String pkg = VisitorMapping.class.getPackageName() + ".";
    ClassLoader parent = SelfCheckBookTest.class.getClassLoader();
    ClassLoader fresh =
        new ClassLoader(parent) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(pkg)) {
              return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
              Class<?> loaded = findLoadedClass(name);
              if (loaded != null) {
                return loaded;
              }
              try (InputStream in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                  throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
              } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
              }
            }
          }
        };
    Class.forName(pkg + first, true, fresh);
    Field mapper = Class.forName(pkg + "VisitorMapping", false, fresh).getField("MAPPER");
    mapper.setAccessible(true);
    return mapper.get(null);
  }
}
