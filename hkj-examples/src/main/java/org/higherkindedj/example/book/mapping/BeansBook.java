// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.example.book.mapping.proto.CustomerMessage;
import org.higherkindedj.example.book.mapping.proto.DispatchRequest;
import org.higherkindedj.example.book.mapping.proto.Priority;
import org.higherkindedj.example.book.mapping.proto.UpdateDispatchRequest;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.ReadOnly;
import org.higherkindedj.optics.annotations.Unmapped;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans.html">Bean-Shaped Wires</a> page.
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
public final class BeansBook {

  private BeansBook() {}

  public static void main(String[] args) {
    // ANCHOR: bean_projection_usage
    Employee researcher = new Employee("Ada", "Research", 36);
    TransferBean transfer = new TransferBean();
    transfer.setDepartment("Platform");
    TransferMappingImpl transferMapping = TransferMappingImpl.INSTANCE;

    // The bean's property can be unset, so the projection validates: patch, never a lens.
    Validated<NonEmptyList<FieldError>, Employee> transferred =
        transferMapping.patch(researcher, transfer);
    // Valid(Employee[name=Ada, department=Platform, age=36])

    // Dense, as on a record wire: an unset property is a located error, never "keep the current
    // value".
    Validated<NonEmptyList<FieldError>, Employee> unset =
        transferMapping.patch(researcher, new TransferBean());
    // Invalid(NonEmptyList[department: must not be null])
    // ANCHOR_END: bean_projection_usage
    System.out.println(transferred);
    System.out.println(unset);

    // ANCHOR: bean_usage
    Customer ada = new Customer("Ada", new EmailAddress("ada@corp.example"));
    ContactMappingImpl contactMapping = ContactMappingImpl.INSTANCE;

    ContactBean bean = contactMapping.build(ada); // new ContactBean(); setName; setEmail
    Validated<NonEmptyList<FieldError>, Customer> fromBean = contactMapping.parse(bean);
    // Valid(Customer[name=Ada, email=EmailAddress[value=ada@corp.example]])

    ContactBean noEmail = new ContactBean(); // a bean can exist with a property never set
    noEmail.setName("Bob");
    Validated<NonEmptyList<FieldError>, Customer> fromUnset = contactMapping.parse(noEmail);
    // Invalid(NonEmptyList[email: must not be null])
    // ANCHOR_END: bean_usage
    System.out.println(fromBean);
    System.out.println(fromUnset);

    // ANCHOR: one_way_usage
    // Parse-only: the Impl has parse and asValidatedParse(), and no build.
    Validated<NonEmptyList<FieldError>, Customer> read =
        CustomerViewMappingImpl.INSTANCE.parse(new CustomerView("Ada", "ada@corp.example"));
    // Valid(Customer[name=Ada, email=EmailAddress[value=ada@corp.example]])

    // Build-only: the Impl has build and asValidatedBuild(), and no parse.
    CustomerRequest request =
        CustomerRequestMappingImpl.INSTANCE.build(
            new Customer("Ada", new EmailAddress("ada@corp.example")));
    // new CustomerRequest(); setName("Ada"); setEmail("ada@corp.example")
    // ANCHOR_END: one_way_usage
    System.out.println(read);
    System.out.println(request.describe());

    // ANCHOR: unmapped_usage
    MerchantPatchBean merchantPatch = new MerchantPatchBean();
    merchantPatch.setName("Brightside Homeware");

    Validated<NonEmptyList<FieldError>, Merchant> merchantPatched =
        MerchantPatchMappingImpl.INSTANCE
            .updateFrom(merchantPatch)
            .apply(new Merchant("m-1", "Brightside"));
    // Valid(Merchant[id=m-1, name=Brightside Homeware]) - the m-9 the bean reads is never applied
    // ANCHOR_END: unmapped_usage
    System.out.println(merchantPatched);

    // ANCHOR: read_only_usage
    MerchantModel fetched = new MerchantModel("m-1"); // as Jackson reads a GET response
    fetched.setName("Brightside");
    MerchantModelMappingImpl merchantMapping = MerchantModelMappingImpl.INSTANCE;

    Validated<NonEmptyList<FieldError>, Merchant> merchant = merchantMapping.parse(fetched);
    // Valid(Merchant[id=m-1, name=Brightside])

    MerchantModel sent = merchantMapping.build(new Merchant("m-1", "Brightside Homeware"));
    boolean idSent = sent.getId() != null; // build never writes a read-only property
    // false
    // ANCHOR_END: read_only_usage
    System.out.println(merchant);
    System.out.println(idSent);

    // ANCHOR: protobuf_usage
    DispatchRequest dispatchRequest =
        DispatchRequest.newBuilder()
            .setCustomer(CustomerMessage.newBuilder().setName("Ada").setEmail("ada@corp.example"))
            .addSkus("SKU-1")
            .setPriority(Priority.PRIORITY_EXPRESS)
            .setLocker("LK-4")
            .build(); // no note: hasNote() is false
    DispatchMappingImpl dispatchMapping = DispatchMappingImpl.INSTANCE;

    Validated<NonEmptyList<FieldError>, Dispatch> dispatch = dispatchMapping.parse(dispatchRequest);
    // Valid(Dispatch[customer=Customer[name=Ada, email=EmailAddress[value=ada@corp.example]],
    // skus=[SKU-1], note=Optional.empty, priority=EXPRESS,
    // destination=Optional[Locker[id=LK-4]]])

    // The customer is a message field, which tracks whether it is set: unset, it reads as null.
    Validated<NonEmptyList<FieldError>, Dispatch> noCustomer =
        dispatchMapping.parse(dispatchRequest.toBuilder().clearCustomer().build());
    // Invalid(NonEmptyList[customer: must not be null])

    // An empty Optional leaves the field unset, so the message built has no note either.
    Customer grace = new Customer("Grace", new EmailAddress("grace@corp.example"));
    DispatchRequest built =
        dispatchMapping.build(
            new Dispatch(
                grace,
                List.of("SKU-2"),
                Optional.empty(),
                DispatchPriority.STANDARD,
                Optional.of(new Destination.PickupPoint("PP-9"))));
    boolean noteSent = built.hasNote();
    // false
    // ANCHOR_END: protobuf_usage
    System.out.println(dispatch);
    System.out.println(noCustomer);
    System.out.println(noteSent);

    // ANCHOR: protobuf_patch_usage
    Dispatch stored =
        new Dispatch(
            new Customer("Lin", new EmailAddress("lin@corp.example")),
            List.of("SKU-3"),
            Optional.empty(),
            DispatchPriority.STANDARD,
            Optional.of(new Destination.Locker("LK-7")));
    UpdateDispatchRequest update =
        UpdateDispatchRequest.newBuilder()
            .setDispatch(
                DispatchRequest.newBuilder().setNote("leave at the door").setPickupPoint("PP-2"))
            .setUpdateMask(FieldMask.newBuilder().addPaths("note").addPaths("pickup_point"))
            .build();

    Validated<NonEmptyList<FieldError>, Dispatch> updated =
        DispatchPatchImpl.INSTANCE.updateFrom(update.getDispatch(), maskOf(update)).apply(stored);
    // Valid(Dispatch[customer=Customer[name=Lin, email=EmailAddress[value=lin@corp.example]],
    // skus=[SKU-3], note=Optional[leave at the door], priority=STANDARD,
    // destination=Optional[PickupPoint[code=PP-2]]])
    // ANCHOR_END: protobuf_patch_usage
    System.out.println(updated);
  }

  // ANCHOR: protobuf_implied_mask
  // A request that omits its mask asks for every field its message sets. An empty mask names none.
  static FieldMask maskOf(UpdateDispatchRequest update) {
    return update.hasUpdateMask()
        ? update.getUpdateMask()
        : FieldMask.newBuilder()
            .addAllPaths(
                update.getDispatch().getAllFields().keySet().stream()
                    .map(Descriptors.FieldDescriptor::getName)
                    .toList())
            .build();
  }
  // ANCHOR_END: protobuf_implied_mask
}

// ANCHOR: bean_spec
// A generated, mutable getter/setter DTO - generated, so not yours to annotate. The spec still sits
// on your interface, never on the bean, so a third-party bean maps without being touched.
class ContactBean {
  private String name;
  private String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

@GenerateMapping
interface ContactMapping extends MappingSpec<Customer, ContactBean> {
  // build() writes through setters; parse() reads through getters, null-guarded and located.
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: bean_spec

// ANCHOR: bean_projection_spec
// A bean carrying one of Employee's three components: a projection. A record wire of that shape
// copies by identity and keeps asLens(); a bean's reference property can be unset, which a lens's
// set could not refuse, so the Impl emits the validated patch instead.
class TransferBean {
  private String department;

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }
}

@GenerateMapping
interface TransferMapping extends MappingSpec<Employee, TransferBean> {}

// ANCHOR_END: bean_projection_spec

// ANCHOR: one_way_spec
// A vendor's read model: built once by its own client, then only ever read. It has getters and
// nothing that writes it, so the mapping is parse-only.
class CustomerView {
  private final String name;
  private final String email;

  CustomerView(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }
}

@GenerateMapping
interface CustomerViewMapping extends MappingSpec<Customer, CustomerView> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// An outbound request: filled and sent, never read back. It has setters and no getters, so the
// mapping is build-only.
class CustomerRequest {
  private String name;
  private String email;

  public void setName(String name) {
    this.name = name;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  String describe() {
    return name + " <" + email + ">";
  }
}

@GenerateMapping
interface CustomerRequestMapping extends MappingSpec<Customer, CustomerRequest> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: one_way_spec

// ANCHOR: unmapped_spec
// A marketplace merchant whose id the server assigns, and a PATCH body shared with the GET
// response: it reads the id and has no setter for it, so the client cannot change it.
record Merchant(String id, String name) {}

class MerchantPatchBean {
  private String name;

  public String getId() {
    return "m-9"; // whatever the body carries, the update never applies it
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

@GenerateMapping
interface MerchantPatchMapping extends UpdateSpec<Merchant, MerchantPatchBean> {
  // getId() has no setter, so it is no property of the mapping, and 'id' names a component of
  // Merchant: without the marker the mapping is refused, in case the accessor is a misspelt pair.
  // The marker says the omission is deliberate. It withholds the refusal and nothing else: the
  // update folds 'name' and never reads getId().
  @Unmapped
  String id();
}

// ANCHOR_END: unmapped_spec

// ANCHOR: read_only_spec
// The client model openapi-generator writes for the same merchant, whose schema marks the id
// readOnly: a response carries it, and a request never sends it, so it has a getter and no setter.
class MerchantModel {
  private final String id;
  private String name;

  public MerchantModel() {
    this(null);
  }

  MerchantModel(String id) { // stands in for the generated @JsonCreator constructor
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

@GenerateMapping
interface MerchantModelMapping extends MappingSpec<Merchant, MerchantModel> {
  // parse reads getId() into Merchant.id, and build leaves it unwritten. The Impl then carries
  // parse and build as two halves, asValidatedParse() and asValidatedBuild(), and no
  // asValidatedPrism(): parse cannot read back an id build never wrote.
  @ReadOnly
  String id();
}

// ANCHOR_END: read_only_spec

// ANCHOR: default_trap
// A product listing whose subtitle is optional, and two generated beans for it.
record Listing(String title, Optional<String> subtitle) {}

class DraftListingBean {
  private String title;
  private @Nullable String subtitle = ""; // starts as "" in the field

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public @Nullable String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(@Nullable String subtitle) {
    this.subtitle = subtitle;
  }
}

class ListingBean {
  private String title;
  private @Nullable String subtitle;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSubtitle() {
    return subtitle == null ? "" : subtitle; // answers "" in the getter
  }

  public void setSubtitle(@Nullable String subtitle) { // the bridge writes null for empty
    this.subtitle = subtitle;
  }
}

@GenerateMapping
interface DraftListingMapping extends MappingSpec<Listing, DraftListingBean> {}

@GenerateMapping
interface ListingMapping extends MappingSpec<Listing, ListingBean> {} // subtitle bridges itself

// ANCHOR_END: default_trap

// A Listing as openapi-generator's java client writes its model by default (openApiNullable=true),
// its doc comments, fluent setters and toString left out: the nullable subtitle is held in a
// JsonNullable, and exposed both through getSubtitle()/setSubtitle(String) and through a companion
// pair, which Jackson binds. BeansBookTest proves what Rules and Limits' "An openapi-generator
// JsonNullable companion" and this page's null warning say of it.
@JsonPropertyOrder({ListingModel.JSON_PROPERTY_TITLE, ListingModel.JSON_PROPERTY_SUBTITLE})
class ListingModel {
  public static final String JSON_PROPERTY_TITLE = "title";
  private @Nullable String title;

  public static final String JSON_PROPERTY_SUBTITLE = "subtitle";
  private JsonNullable<String> subtitle = JsonNullable.<String>undefined();

  public ListingModel() {}

  @JsonProperty(value = JSON_PROPERTY_TITLE, required = true)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public @Nullable String getTitle() {
    return title;
  }

  @JsonProperty(value = JSON_PROPERTY_TITLE, required = true)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setTitle(@Nullable String title) {
    this.title = title;
  }

  @JsonIgnore
  public @Nullable String getSubtitle() {
    return subtitle.orElse(null);
  }

  @JsonProperty(value = JSON_PROPERTY_SUBTITLE, required = false)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public JsonNullable<String> getSubtitle_JsonNullable() {
    return subtitle;
  }

  @JsonProperty(JSON_PROPERTY_SUBTITLE)
  public void setSubtitle_JsonNullable(JsonNullable<String> subtitle) {
    this.subtitle = subtitle;
  }

  public void setSubtitle(@Nullable String subtitle) {
    this.subtitle = JsonNullable.<String>of(subtitle);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListingModel listingModel = (ListingModel) o;
    return Objects.equals(this.title, listingModel.title)
        && equalsNullable(this.subtitle, listingModel.subtitle);
  }

  private static <T> boolean equalsNullable(
      @Nullable JsonNullable<T> a, @Nullable JsonNullable<T> b) {
    return a == b
        || (a != null
            && b != null
            && a.isPresent()
            && b.isPresent()
            && Objects.deepEquals(a.get(), b.get()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, hashCodeNullable(subtitle));
  }

  private static <T> int hashCodeNullable(@Nullable JsonNullable<T> a) {
    if (a == null) {
      return 1;
    }
    return a.isPresent() ? Arrays.deepHashCode(new @Nullable Object[] {a.get()}) : 31;
  }
}

@GenerateMapping
interface ListingModelMapping extends MappingSpec<Listing, ListingModel> {}

// ANCHOR: protobuf_spec
// A dispatch as the order service keeps it. DispatchRequest and CustomerMessage are the messages
// protoc generates from dispatch.proto.
enum DispatchPriority {
  STANDARD,
  EXPRESS
}

// The oneof 'destination': a record named after each member.
sealed interface Destination {
  record Locker(String id) implements Destination {}

  record PickupPoint(String code) implements Destination {}
}

record Dispatch(
    Customer customer,
    List<String> skus,
    Optional<String> note,
    DispatchPriority priority,
    Optional<Destination> destination) {}

@GenerateMapping
interface CustomerMessageMapping extends MappingSpec<Customer, CustomerMessage> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// The customer nests through CustomerMessageMapping, the skus copy as a List, the note reads as
// empty when unset, and so does the oneof, whose set member becomes its record.
@GenerateMapping
interface DispatchMapping extends MappingSpec<Dispatch, DispatchRequest>, DispatchVocabulary {}

// The leaves DispatchMapping shares with DispatchPatch, an update over the same pair.
interface DispatchVocabulary {
  // The priority converts through this leaf, which refuses the unset PRIORITY_UNSPECIFIED and
  // the UNRECOGNIZED an unknown number reads as.
  default ValidatedPrism<Priority, DispatchPriority> priority() {
    return ValidatedPrism.of(
        wire ->
            switch (wire) {
              case PRIORITY_STANDARD -> Validated.validNel(DispatchPriority.STANDARD);
              case PRIORITY_EXPRESS -> Validated.validNel(DispatchPriority.EXPRESS);
              case PRIORITY_UNSPECIFIED, UNRECOGNIZED ->
                  Validated.invalidNel(FieldError.of("not a priority: " + wire));
            },
        domain ->
            switch (domain) {
              case STANDARD -> Priority.PRIORITY_STANDARD;
              case EXPRESS -> Priority.PRIORITY_EXPRESS;
            });
  }
}

// ANCHOR_END: protobuf_spec

// ANCHOR: protobuf_patch_spec
@GenerateMapping
interface DispatchPatch extends UpdateSpec<Dispatch, DispatchRequest>, DispatchVocabulary {}

// ANCHOR_END: protobuf_patch_spec

// ANCHOR: protobuf_enum_trap
// The same request, its priority kept as the generated enum itself: no leaf converts it.
record DispatchRecord(
    Customer customer,
    List<String> skus,
    Optional<String> note,
    Priority priority,
    Optional<String> locker,
    Optional<String> pickupPoint) {}

@GenerateMapping
interface DispatchRecordMapping extends MappingSpec<DispatchRecord, DispatchRequest> {}

// ANCHOR_END: protobuf_enum_trap

// ANCHOR: protobuf_required_trap
// descriptor.proto's NamePart, a proto2 message whose two fields are required.
record OptionName(String namePart, Optional<Boolean> isExtension) {}

@GenerateMapping
interface OptionNameMapping
    extends MappingSpec<OptionName, DescriptorProtos.UninterpretedOption.NamePart> {}

// ANCHOR_END: protobuf_required_trap
