# Quickstart: Your First 422

_From a blank project to an endpoint that answers with every bad field at once._

This is the shortest true path: five steps, each one taken from the example application in this
repository, so what you read is what the build compiles and runs. Nothing here is a sketch.

~~~admonish info title="What You'll Learn"
- Add the processor to a build, and meet the one prerequisite that will stop you
- Declare a mapping as an interface with no body, and add a leaf where a field needs checking
- Return the parse result straight from a controller, with no error-handling code in between
- Read the 422 your client receives, field by field
- Prove the mapping in one test call
~~~

~~~admonish example title="See Example Code"
The spec, the endpoint and the test are included from the hkj-spring example application:
[UserMapping.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/example/src/main/java/org/higherkindedj/spring/example/controller/UserMapping.java),
[UserController.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/example/src/main/java/org/higherkindedj/spring/example/controller/UserController.java),
[UserParseWebMvcSliceTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/example/src/test/java/org/higherkindedj/spring/example/controller/UserParseWebMvcSliceTest.java)
~~~

---

## Before you start

~~~admonish warning title="Java 25, with preview features enabled"
Higher-Kinded-J is built on **Java 25** with preview language features, so a build without
`--enable-preview` will not compile. Preview is tied to one release: `javac` accepts
`--enable-preview` only for the JDK it is running on, so a later JDK is not a free upgrade, and
some of the library's own class files can be loaded only by the release that compiled them.

Decide this first. On a team still on Java 21, or one whose platform is already moving past 25,
that is a conversation rather than a dependency bump. The [Quickstart](../quickstart.md) has the
details, and the Gradle plugin below sets the flags for you.
~~~

## 1. Add the plugin

```kotlin
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

That single line wires the dependencies, the annotation processor, `-parameters` and the preview
flags. For a hand-rolled build, or for Maven, see [Manual setup](../tooling/manual_setup.md). To
answer with an HTTP response at the end of step 3, add the Spring starter,
`hkj-spring-boot-starter`.

## 2. Declare the mapping

A mapping is an interface you own. Name the two records it maps between, and the processor writes
the rest. Where a field needs converting or checking, add a **leaf**: one method, named after the
domain component, returning the conversion.

``` java
{{#include ../../../hkj-spring/example/src/main/java/org/higherkindedj/spring/example/controller/UserMapping.java:quickstart_spec}}
```

~~~admonish tip title="If you know MapStruct"
The spec is the `@Mapper` interface and `UserMappingImpl` is the `UserMapperImpl` it generates: the
same shape, with three differences. One declaration gives both directions. A conversion is declared
per component and never inferred from the types. And the inbound direction returns every bad field
rather than throwing on the first.
~~~

The wire here is a getter/setter bean, because that is what a generated API client hands you. A
record wire works exactly the same way, and is what [Record Mapping Basics](basics.md) teaches. The
stock conversions, for UUIDs, dates, enums and money, are in [Standard Codecs](codecs.md), so most
boundaries need no hand-written leaf at all.

## 3. Return the parse result from a controller

`parse` gives you either the domain value or every located failure. With the starter on the
classpath, a controller returns that result as-is:

``` java
{{#include ../../../hkj-spring/example/src/main/java/org/higherkindedj/spring/example/controller/UserController.java:quickstart_endpoint}}
```

~~~admonish warning title="Two things that catch people here"
`FieldError` is Higher-Kinded-J's, not Spring's `org.springframework.validation.FieldError`: an IDE
will offer the wrong import. And the spec interface is not the thing you call. Inject the surface
(`ValidatedPrism<UserDto, User>`, registered as shown in
[Injecting and testing](testing.md#injecting-and-testing-generated-mappings)) or call
`UserMappingImpl.INSTANCE` in the caller. Never declare that constant on the spec itself:
[it can read null](basics.md#bind-in-the-caller).
~~~

## 4. Read the 422

Post a body with two bad fields, an email that is not one and a blank first name:

```json
{"id": "7", "email": "not-an-email", "firstName": "", "lastName": "Hopper"}
```

One response comes back, carrying both, each located by the field it belongs to:

```json
{
  "valid": false,
  "errorCount": 2,
  "errors": [
    { "path": "email",
      "segments": ["email"],
      "message": "not a valid email address" },
    { "path": "firstName",
      "segments": ["firstName"],
      "message": "must not be blank" }
  ]
}
```

The status is **422 Unprocessable Content**, and it is configurable: an API contract that promises
400 sets `hkj.web.validation-field-error-status`. [The 422
leg](../spring/spring_boot_integration.md#the-422-leg) is the full story.

A field the client leaves out is a located error too, `must not be null`, in the same response
rather than an exception. One rule covers both: [Null has an address, not a stack
trace](basics.md#null-doctrine).

~~~admonish warning title="Type the wire loosely"
Jackson binds the request before `parse` ever runs. A wire field typed `LocalDate`, an enum or an
`int` is Jackson's to reject, and it answers with its own 400 and no field path, so the located
response above never happens for that field. Keep a wire field a `String` wherever a codec or a
leaf converts it, and let the mapping do the checking.
~~~

## 5. Prove it

The example application asserts exactly that response, in a slice test:

``` java
{{#include ../../../hkj-spring/example/src/test/java/org/higherkindedj/spring/example/controller/UserParseWebMvcSliceTest.java:quickstart_proof}}
```

For the mapping itself, one call checks its laws, without a web layer:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/RecordMappingBookLawsTest.java:laws}}
```

---

~~~admonish info title="Where next"
- Shipping a record wire, and the declarations you will reach for: [Record Mapping Basics](basics.md)
- UUIDs, dates, enums and money without writing a leaf: [Standard Codecs](codecs.md)
- A PATCH endpoint, where an absent field means *leave unchanged*: [Sparse PATCH](beans_patch.md#what-patch-means)
- Judging the fit before adopting it: [Mapper at a Glance](at_a_glance.md)
~~~

~~~admonish info title="Hands-On Learning"
Practise the generated boundary in [Tutorial 26](../tutorials/optics/boundary_mapping_journey.md):
build, parse, read the located errors, and law-check the result.
~~~

---

**Previous:** [Mapping at the Boundary](ch_intro.md)
**Next:** [Record Mapping Basics](basics.md)
