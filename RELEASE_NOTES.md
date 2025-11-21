# Higher-Kinded-J 0.2.0 Release Notes

We're excited to announce Higher-Kinded-J 0.2.0, a major release with extensive enhancements to the optics library and new functional programming primitives!

## 🎯 What's New

### Massive Optics Expansion

This release dramatically expands the optics library with **6 new optic types** and dozens of utility classes:

- **Fold, Getter, Setter**: New fundamental optic types for read-only queries, single-element access, and write-only modifications
- **Indexed Optics**: Position-aware transformations with `IndexedTraversal`, `IndexedFold`, and `IndexedLens`
- **Filtered Traversals**: Compose predicates directly into optic chains with `filtered()` and `filterBy()`
- **Limiting Traversals**: Focus on specific portions of lists with `taking()`, `dropping()`, `takingWhile()`, `slicing()`, etc.
- **PartsOf**: Transform all focused elements as a single list with operations like `sorted()`, `reversed()`, `distinct()`
- **Fluent API**: New operator-style API with Free Monad DSL for building composable optic programs with multiple interpreters

### New Functional Programming Primitives

- **FreeMonad**: Build domain-specific languages (DSLs) with stack-safe execution and multiple interpreters
- **Trampoline**: Stack-safe recursion support for tail-call optimization
- **Const Functor**: Phantom type parameter support for advanced type-level programming
- **Alternative Type Class**: Choice and failure operations for applicative functors

### Enhanced Type Classes

- **Monoid**: New methods (`firstOptional()`, `lastOptional()`, `maximum()`, `minimum()`) and instances (Long, Double)
- **Monad**: Added `flatMap2-5` for sequencing multiple monadic values with effectful combining functions

### Performance & Infrastructure

- **JMH Benchmarks Module**: Professional performance testing infrastructure
- **Zero-cost Abstractions**: Refactored IO, Maybe, Either to eliminate wrapper overhead

### Comprehensive Integration

- **At & Ixed Type Classes**: CRUD operations and safe indexed access for Map and List
- **Core Types Integration**: Seamless optics support for Maybe, Either, Validated, Try
- **String & Tuple Traversals**: Built-in utilities for common data structures

## 📊 By The Numbers

- **31 commits** since 0.1.9
- **50+ pull requests** merged
- **6 new optic types**
- **20+ utility classes** added
- **100% backward compatible**

## 🚀 Getting Started

Update your dependencies to use the latest version:

```gradle
implementation("io.github.higher-kinded-j:hkj-core:0.2.0")
annotationProcessor("io.github.higher-kinded-j:hkj-processor:0.2.0")
annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:0.2.0")
```

## 📖 Examples

### Filtered Traversals
```java
Traversal<Company, Employee> activeEngineers =
    company.employees()
        .filtered(emp -> emp.isActive())
        .filtered(emp -> emp.department().equals("Engineering"));
```

### PartsOf
```java
// Sort all player scores in a league
League updated = leagueToAllPlayerScores
    .partsOf()
    .sorted()
    .set(league);
```

### Indexed Optics
```java
IndexedTraversal<Integer, List<Player>, Player> indexed =
    IndexedTraversals.forList();

List<Player> withPositions = indexed.imodify(
    (index, player) -> player.withJerseyNumber(index + 1),
    players
);
```

### FreeMonad DSL
```java
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, ageLens)
        .flatMap(age -> {
            if (age >= 18) {
                return OpticPrograms.set(person, statusLens, "ADULT");
            } else {
                return OpticPrograms.pure(person);
            }
        });
```

## 📚 Documentation

All documentation has been refreshed and expanded:
- [Optics Documentation](https://higher-kinded-j.github.io/optics/optics_intro.html)
- [Filtered Optics Guide](https://higher-kinded-j.github.io/optics/filtered_optics.html)
- [Indexed Optics Guide](https://higher-kinded-j.github.io/optics/indexed_optics.html)
- [Fluent API Guide](https://higher-kinded-j.github.io/optics/fluent_api.html)

## ⚠️ Breaking Changes

None! This release is fully backward compatible with 0.1.9.

## 🙏 Contributors

Thanks to all contributors and reviewers who made this release possible!

## 🔗 Links

- [Full Changelog](CHANGELOG.md)
- [Documentation](https://higher-kinded-j.github.io/home.html)
- [GitHub Repository](https://github.com/higher-kinded-j/higher-kinded-j)
- [Maven Central](https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core)

---

**Full Changelog**: v0.1.9...v0.2.0
