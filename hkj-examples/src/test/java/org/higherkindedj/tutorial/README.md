# Higher-Kinded-J Tutorials

Welcome to the Higher-Kinded-J tutorial series! These hands-on tutorials will teach you how to use higher-kinded types and optics in Java through practical exercises.

## Overview

This tutorial series consists of two tracks:
1. **Core Types** (10 tutorials, ~80 minutes) - Learn about Functors, Applicatives, Monads, and more
2. **Optics** (15 tutorials, ~150 minutes) - Learn about Lenses, Prisms, Traversals, Focus DSL, and advanced patterns

Each tutorial contains exercises where you need to replace `___` placeholders with working code. The tests will fail until you complete the exercises correctly.

## How to Use

### Running the Tutorials

1. Navigate to a tutorial file (e.g., `Tutorial01_KindBasics.java`)
2. Read the instructions and javadoc for each exercise
3. Replace each `___` with the correct code
4. Run the test to see if your solution is correct
5. If the test fails, read the error message and try again

### Getting Help

- Read the tutorial comments carefully - they contain hints
- Check the solution files in the `solutions/` directory
- Refer to the main examples in `hkj-examples/src/main/java`
- Consult the library documentation

## Core Types Tutorial Series

### Tutorial 01: Kind Basics (~8 minutes)
Learn the foundation of higher-kinded types in Java:
- Understanding `Kind<F, A>`
- Widening and narrowing
- Witness types

### Tutorial 02: Functor Mapping (~8 minutes)
Learn to transform values in context:
- The `map` operation
- Functor laws
- Method references

### Tutorial 03: Applicative Combining (~10 minutes)
Learn to combine independent values:
- Using `of` to lift values
- Combining with `map2`, `map3`, `map4`, `map5`
- Form validation

### Tutorial 04: Monad Chaining (~12 minutes)
Learn to chain dependent computations:
- The `flatMap` operation
- Chaining operations
- Error short-circuiting

### Tutorial 05: Monad Error Handling (~8 minutes)
Learn explicit error handling:
- `raiseError` and `handleErrorWith`
- `recover` and `orElse`
- Try for exceptions

### Tutorial 06: Concrete Types (~10 minutes)
Learn when to use each type:
- Either for error handling
- Maybe for optional values
- List for collections
- Validated for accumulating errors

### Tutorial 07: Real World (~12 minutes)
Apply everything to real scenarios:
- Validation pipelines
- Data processing
- Configuration with Reader
- Combining effects

## Optics Tutorial Series

### Tutorial 01: Lens Basics (~8 minutes)
Learn immutable field access:
- `get`, `set`, `modify`
- Generated lenses
- Custom lenses

### Tutorial 02: Lens Composition (~10 minutes)
Learn to access nested structures:
- Composing with `andThen`
- Deep updates
- Reusable composed lenses

### Tutorial 03: Prism Basics (~8 minutes)
Learn to work with sum types:
- `getOptional`, `build`, `modify`
- Pattern matching
- Sealed interfaces

### Tutorial 04: Affine Basics (~10 minutes)
Learn to work with optional fields:
- Zero-or-one focus
- Lens + Prism composition
- Optional field access

### Tutorial 05: Traversal Basics (~10 minutes)
Learn to work with multiple values:
- Bulk modifications
- Filtering
- Composing traversals

### Tutorial 06: Optics Composition (~10 minutes)
Learn to combine different optic types:
- Lens + Prism
- Lens + Traversal
- Complex compositions

### Tutorial 07: Generated Optics (~8 minutes)
Learn annotation-based generation:
- `@GenerateLenses`
- `@GeneratePrisms`
- `@GenerateTraversals`

### Tutorial 08: Real World Optics (~12 minutes)
Apply optics to real problems:
- User profile management
- API response processing
- E-commerce orders
- Data validation

### Tutorial 09: Fluent Optics API (~12 minutes)
Learn the ergonomic fluent API:
- OpticOps static methods (source-first)
- Collection operations: getAll, modifyAll, setAll
- Query operations: exists, count, find
- Validation with Either, Maybe, Validated
- Real-world form validation

### Tutorial 10: Advanced Prism Patterns (~10 minutes)
Master advanced prism techniques:
- Predicate-based matching with `nearly`
- Exclusion filtering with `doesNotMatch`
- Cross-optic composition patterns

### Tutorial 11: Advanced Optics DSL (~15 minutes)
Master the Free Monad DSL:
- Building programs as data structures
- Composing with flatMap
- Conditional workflows
- Multi-step transformations
- Logging interpreter (audit trails)
- Validation interpreter (dry-runs)

### Tutorial 12: Focus DSL (~12 minutes)
Learn type-safe path navigation:
- FocusPath and AffinePath
- Fluent chaining with `andThen`
- Pattern matching with Focus DSL

### Tutorial 13: Advanced Focus DSL (~12 minutes)
Type class integration with Focus DSL:
- Functor, Applicative, and Monad integration
- Effect-aware transformations
- Complex path compositions

### Tutorial 14: Focus-Effect Bridge (~15 minutes)
Connecting optics with effects:
- Bridging Focus DSL and Effect paths
- Combining optic access with effectful operations
- Real-world integration patterns

### Tutorial 15: List Prisms (~12 minutes)
Functional list decomposition:
- Cons (head/tail) and snoc (init/last) patterns
- head, last, tail, init accessors
- Stack-safe trampoline operations
- Composing with other optics

## Solutions

Complete solutions for all exercises are available in:
- `solutions/coretypes/` - Core types solutions
- `solutions/optics/` - Optics solutions

Try to solve the exercises yourself before looking at the solutions!

## Tips

1. **Start from the beginning** - Each tutorial builds on previous concepts
2. **Read the hints** - They're there to help you
3. **Run tests frequently** - Get immediate feedback on your progress
4. **Experiment** - Try different approaches to understand the concepts better
5. **Don't rush** - Take time to understand each concept before moving on

## Next Steps

After completing these tutorials, explore:
- The examples in `hkj-examples/src/main/java`
- The library source code in `hkj-core` and `hkj-api`
- The documentation at the project website

Happy learning!
