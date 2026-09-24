// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/generics.html">Generic Specs</a> page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API, and {@code BookExampleOutputTest} runs {@code main} and
 * holds each output comment to what it prints.
 *
 * <p>The specs are top-level, not nested in the class: a nested spec joins its enclosing simple
 * names, so {@code Shop.CustomerMapping} would generate {@code ShopCustomerMappingImpl}, where the
 * page teaches {@code CustomerMappingImpl}.
 *
 * <p>Types several pages share, such as {@code Customer} and {@code EmailAddress}, are declared in
 * {@link BasicsBook}, beside the page that introduces them.
 */
public final class GenericsBook {

  private GenericsBook() {}

  public static void main(String[] args) {
    // ANCHOR: generic_usage
    Validated<NonEmptyList<FieldError>, Page<Customer>> page =
        CustomerPageMappingImpl.INSTANCE.parse(
            new PageDto<>(
                List.of(new CustomerDto("Ada", "ada@corp.example"), new CustomerDto("Bob", "nope")),
                2));
    // Invalid(NonEmptyList[items.1.email: not an email address])
    // ANCHOR_END: generic_usage
    System.out.println(page);

    // ANCHOR: threaded_usage
    // One generic Impl serves every instantiation; identity elements copy through.
    Page<String> tags = new Page<>(List.of("fp", "hkt"), 2);
    PageDto<String> tagsDto = PageMappingImpl.<String>instance().build(tags);
    Validated<NonEmptyList<FieldError>, Page<Integer>> counts =
        PageMappingImpl.<Integer>instance().parse(new PageDto<>(List.of(1, 2, 3), 3));
    // ANCHOR_END: threaded_usage
    System.out.println(tagsDto + " / " + counts);

    // ANCHOR: threaded_inferred
    PageMapping<String> inferredWitness = PageMappingImpl.instance(); // witness inferred
    // ANCHOR_END: threaded_inferred
    System.out.println(inferredWitness);

    // ANCHOR: element_usage
    // One spec, any element codec: each abstract leaf arrives as a prism through of(...).
    Validated<NonEmptyList<FieldError>, Page<EmailAddress>> mail =
        CodecPageMappingImpl.of(EmailCodecs.EMAIL)
            .parse(new PageDto<>(List.of("ada@example.org", "nope"), 2));
    // Invalid(NonEmptyList[items.1: not an email address])
    // ANCHOR_END: element_usage
    System.out.println(mail);
  }
}

// ANCHOR: generic_mixin_spec
// Vocabulary parameterised by the type it speaks about: one interface, reused wherever the
// domain component happens to land.
interface Renames<T> {
  @MapField(to = "fullName")
  T name();
}

// The interface below it says what T is. The spec never repeats it, and never sees a T.
interface TextRenames extends Renames<String> {}

record Contractor(String name, EmailAddress email) {}

record ContractorDto(String fullName, String email) {}

@GenerateMapping
interface ContractorMapping extends TextRenames, MappingSpec<Contractor, ContractorDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: generic_mixin_spec

// ANCHOR: generic_spec
record Page<T>(List<T> items, int total) {}

record PageDto<T>(List<T> items, int total) {}

@GenerateMapping
interface CustomerPageMapping extends MappingSpec<Page<Customer>, PageDto<CustomerDto>> {}

// ANCHOR_END: generic_spec

// ANCHOR: threaded_spec
@GenerateMapping
interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}

// one Impl for every T: PageMappingImpl.<String>instance(), .<Integer>instance(), ...

// ANCHOR_END: threaded_spec

// ANCHOR: element_spec
// The element mapping is deliberately open: an abstract leaf, supplied at of(...) time.
@GenerateMapping
interface CodecPageMapping<T, TDto> extends MappingSpec<Page<T>, PageDto<TDto>> {
  ValidatedPrism<TDto, T> items();
}

// ANCHOR_END: element_spec
