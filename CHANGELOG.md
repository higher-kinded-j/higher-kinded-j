# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2025-11-21

This is a major release with extensive enhancements to the optics library, new functional programming primitives, and comprehensive documentation updates. The release includes 31 commits with significant additions to both the HKT and Optics capabilities.

### 🎯 Highlights

- **Massive Optics Expansion**: Added 6 new optic types and dozens of utility classes for practical data manipulation
- **Fluent API**: New operator-style API with Free Monad DSL for building composable optic programs
- **Advanced Traversals**: Indexed optics, filtered traversals, limiting traversals, and PartsOf combinator
- **New Data Types**: FreeMonad for DSL creation, Trampoline for stack-safe recursion, Const functor
- **Enhanced Type Classes**: Alternative, enhanced Monoid with new methods, flatMapN for Monad
- **Performance Infrastructure**: JMH benchmarking module for performance testing
- **Documentation Refresh**: Comprehensive updates across all documentation chapters

### ✨ New Features

#### New Optics Types
- **Fold** (#162): Read-only optic for querying and extracting data with operations like `getAll`, `preview`, `find`, `exists`, and `all`
- **Getter** (#176): Single-element read-only access with composition support
- **Setter** (#176): Write-only modification without requiring read access, supports effectful modifications
- **Indexed Optics** (#182): Position-aware transformations with `IndexedTraversal`, `IndexedFold`, and `IndexedLens`
  - Track index/position of each focused element
  - Index composition using Tuple2
  - Filter by index with `filterIndex()`
  - Combined index-value filtering

#### Optics Enhancements

**Type Classes**:
- **At** (#177): Lens to Optional value at index, enabling insert/update/delete semantics for Map and List
- **Ixed** (#178): Safe indexed access via Traversal for update-only operations (no insert/delete)

**Filtering & Selection**:
- **Filtered Traversals** (#179): Predicate-based filtering with `filtered(Predicate)` and `filterBy(Fold, Predicate)`
- **Limiting Traversals** (#180): Focus on specific portions of lists
  - `taking(n)`: First n elements
  - `dropping(n)`: Skip first n elements
  - `takingLast(n)`: Last n elements
  - `droppingLast(n)`: Exclude last n elements
  - `slicing(from, to)`: Index range [from, to)
- **Conditional Traversals** (ListTraversals):
  - `takingWhile(Predicate)`: Focus on prefix while predicate holds
  - `droppingWhile(Predicate)`: Skip prefix, focus on rest

**List Manipulation**:
- **PartsOf** (#181): Convert Traversal to Lens focusing on List of all elements
  - `sorted()`: Sort using natural ordering
  - `sorted(Comparator)`: Sort with custom comparator
  - `reversed()`: Reverse order of elements
  - `distinct()`: Remove duplicates

**Prism Enhancements** (#183):
- New convenience methods: `matches()`, `getOrElse()`, `mapOptional()`, `orElse()`, `modify()`, `modifyWhen()`, `setWhen()`
- New Prisms utility class with factory methods:
  - `some()`, `left()`, `right()`, `only()`, `notNull()`
  - `instanceOf(Class)` for type-based matching
  - `listHead()`, `listAt(int)`, `listLast()` for list access
  - `just()`, `valid()`, `invalid()`, `success()`, `failure()` for core types

**Traversal Utilities** (#184):
- `forOptional()`: Affine traversal for Optional<A>
- `forMapValues()`: Traverse all values in Map<K,V>
- **TupleTraversals**: `both()` for Tuple2<A,A>
- **StringTraversals**: `chars()`, `worded()`, `lined()` for string manipulation
- **EitherTraversals**, **MaybeTraversals**, **ValidatedTraversals**, **TryTraversals** (#186, #188): Comprehensive integration with core types

**Fluent API** (#185):
- **Tier 1**: Simple fluent operations with `OpticOps.get()`, `OpticOps.set()`, `OpticOps.modify()`
- **Tier 2**: Free Monad DSL for advanced workflows
  - Build optic programs as composable data structures
  - Multiple interpreters: Direct, Logging, Validation
  - Conditional logic and composition support

**Integration** (#189):
- Fluent API extensions for core types: `modifyEither`, `modifyMaybe`, `modifyAllValidated`
- Fold extensions for Maybe integration (FoldExtensions)
- Getter extensions for null-safe access (GetterExtensions)

#### New Data Types & Type Classes

**FreeMonad** (#173):
- Complete Free monad implementation for DSL creation
- Stack-safe foldMap interpreter using explicit continuation stack
- FreeFactory for fluent chaining without type annotations
- Multiple example programs and interpreters

**Trampoline** (#156):
- Stack-safe recursion support
- Enables tail-call optimization in Java
- Comprehensive tests and benchmarks

**Const Functor** (#157):
- Phantom type parameter support
- Key property: mapping second type parameter has no effect
- Useful for traversals and folds

**Alternative Type Class** (#160):
- Choice and failure operations for applicative functors
- Implementations for common types

**Monoid Enhancements** (#158):
- New methods: `firstOptional()`, `lastOptional()`, `maximum(Comparator)`, `minimum(Comparator)`
- Default methods: `combineAll(Iterable)`, `combineN(A, int)`, `isEmpty(A)`
- New instances: `longAddition()`, `longMultiplication()`, `doubleAddition()`, `doubleMultiplication()`
- Performance: `combineN()` uses exponentiation by squaring (O(log n))

**Monad Enhancements** (#174):
- Added `flatMap2`, `flatMap3`, `flatMap4`, `flatMap5` methods
- Sequence multiple monadic values and combine with effectful function
- Comprehensive documentation and examples

### 🔄 Changes

**Refactoring** (#175):
- IO, Maybe, and Either now directly implement Kind interfaces
- Eliminates wrapper/holder pattern for zero runtime overhead
- Reduced memory footprint and GC pressure
- Simpler, more consistent codebase

### 🐛 Bug Fixes

- Fixed broken link in README (#152)
- Fixed JavaDoc warnings across codebase (#194)
- Additional optics integration fixes (#188)

### 🏗️ Infrastructure

**Benchmarking** (#153):
- Created JMH benchmarks module for performance testing
- Comprehensive benchmarks for new features

### 📚 Documentation

Comprehensive documentation refresh across all chapters:
- README.md and optics intro reviewed (#195)
- General documentation refresh (#196)
- Functional programming chapter (#197)
- Monad chapter (#198)
- Transformers chapter (#199)
- Optics chapter (#200)
- Glossary and reading documentation (#201)
- Added flatMapN documentation to monad docs, quick reference, and glossary (#174)

### 🔗 Pull Requests

This release includes contributions from PRs #152-#201 (31 commits total).

### ⚠️ Breaking Changes

None. This release is fully backward compatible with 0.1.9.

### 📦 Migration Guide

No migration required. All existing code will continue to work. New features are additive.

### 🙏 Acknowledgments

Special thanks to all contributors and the gemini-code-assist bot for documentation improvements.

---

## [0.1.9] - Previous Release

See git history for previous releases.
