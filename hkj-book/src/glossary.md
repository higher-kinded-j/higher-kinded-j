# Glossary of Functional Programming Terms

~~~admonish info title="What This Section Covers"
- Key terminology used throughout Higher-Kinded-J documentation
- Explanations tailored for mid-level Java developers
- Practical examples to reinforce understanding
- Quick reference for concepts you encounter whilst coding
~~~

This glossary provides clear, practical explanations of functional programming and Higher-Kinded-J concepts. Each term includes Java-friendly explanations and examples where helpful.


---

## [Type System & Higher-Kinded Types](glossary/type-system.md)

Variance, higher-kinded type simulation, witnesses, and phantom types.

[Contravariant](glossary/type-system.md#contravariant), [Covariant](glossary/type-system.md#covariant), [Defunctionalisation](glossary/type-system.md#defunctionalisation), [Higher-Kinded Type (HKT)](glossary/type-system.md#higher-kinded-type-hkt), [Invariant](glossary/type-system.md#invariant), [Kind](glossary/type-system.md#kind), [Phantom Type](glossary/type-system.md#phantom-type), [Type Constructor](glossary/type-system.md#type-constructor), [TypeArity](glossary/type-system.md#typearity), [Variance Summary Table](glossary/type-system.md#variance-summary-table), [Witness Type](glossary/type-system.md#witness-type), [WitnessArity](glossary/type-system.md#witnessarity)

## [Type Classes](glossary/type-classes.md)

Functor, Monad, and the rest of the abstraction hierarchy.

[Applicative](glossary/type-classes.md#applicative), [Bifunctor](glossary/type-classes.md#bifunctor), [Coyoneda](glossary/type-classes.md#coyoneda), [Free Applicative](glossary/type-classes.md#free-applicative), [Functor](glossary/type-classes.md#functor), [Instances Facade](glossary/type-classes.md#instances-facade), [Map Fusion](glossary/type-classes.md#map-fusion), [Monad](glossary/type-classes.md#monad), [Monad Transformer](glossary/type-classes.md#monad-transformer), [MonadError](glossary/type-classes.md#monaderror), [Monoid](glossary/type-classes.md#monoid), [Natural Transformation](glossary/type-classes.md#natural-transformation), [Profunctor](glossary/type-classes.md#profunctor), [Selective](glossary/type-classes.md#selective), [Semigroup](glossary/type-classes.md#semigroup), [Update](glossary/type-classes.md#update)

## [Data & Core Effect Types](glossary/data-effects.md)

Core data structures and the effect types built on them.

[Choice](glossary/data-effects.md#choice), [Cons](glossary/data-effects.md#cons), [Const](glossary/data-effects.md#const), [Either](glossary/data-effects.md#either), [EitherOrBoth](glossary/data-effects.md#eitherorboth), [IO](glossary/data-effects.md#io), [Maybe](glossary/data-effects.md#maybe), [NonEmptyList](glossary/data-effects.md#nonemptylist), [Snoc](glossary/data-effects.md#snoc), [TimeSource](glossary/data-effects.md#timesource), [Try](glossary/data-effects.md#try), [Tuple](glossary/data-effects.md#tuple), [Unit](glossary/data-effects.md#unit), [Validated](glossary/data-effects.md#validated)

## [Effect Paths & Effect Handlers](glossary/effect-paths.md)

The Effect Path API and the Free-monad effect-handler machinery.

[BoundSet](glossary/effect-paths.md#boundset), [@ComposeEffects](glossary/effect-paths.md#composeeffects), [Continuation-Passing Style (CPS)](glossary/effect-paths.md#continuation-passing-style-cps), [Effect](glossary/effect-paths.md#effect), [Effect Algebra](glossary/effect-paths.md#effect-algebra), [Effect Path](glossary/effect-paths.md#effect-path), [Effect-Optics Bridge](glossary/effect-paths.md#effect-optics-bridge), [@EffectAlgebra](glossary/effect-paths.md#effectalgebra), [EitherF](glossary/effect-paths.md#eitherf), [foldMap](glossary/effect-paths.md#foldmap), [Free Monad](glossary/effect-paths.md#free-monad), [Inject](glossary/effect-paths.md#inject), [Interpreter (Effect Handler)](glossary/effect-paths.md#interpreter-effect-handler), [Leg](glossary/effect-paths.md#leg), [mapK](glossary/effect-paths.md#mapk), [Path](glossary/effect-paths.md#path), [ProgramAnalyser](glossary/effect-paths.md#programanalyser), [Railway-Oriented Programming](glossary/effect-paths.md#railway-oriented-programming), [recover](glossary/effect-paths.md#recover), [via](glossary/effect-paths.md#via), [VResultPath](glossary/effect-paths.md#vresultpath)

## [Optics, Validation & Mapping](glossary/optics.md)

Lenses, prisms, traversals, the Focus DSL, and record mapping.

[Affine](glossary/optics.md#affine), [At](glossary/optics.md#at), [Edits](glossary/optics.md#edits), [FieldError](glossary/optics.md#fielderror), [Focus DSL](glossary/optics.md#focus-dsl), [FocusPath](glossary/optics.md#focuspath), [Fold](glossary/optics.md#fold), [@GenerateAssembly](glossary/optics.md#generateassembly), [@GenerateErrorEnvelope](glossary/optics.md#generateerrorenvelope), [@GenerateMapping](glossary/optics.md#generatemapping), [@GenerateMerge](glossary/optics.md#generatemerge), [Iso (Isomorphism)](glossary/optics.md#iso-isomorphism), [Lens](glossary/optics.md#lens), [Parse, Don't Validate](glossary/optics.md#parse-dont-validate), [Prism](glossary/optics.md#prism), [Setter](glossary/optics.md#setter), [Traversal](glossary/optics.md#traversal), [Validated Assembly](glossary/optics.md#validated-assembly), [ValidatedPrism](glossary/optics.md#validatedprism)

## [Concurrency & Resilience](glossary/concurrency.md)

Virtual-thread concurrency, structured scopes, and resilience.

[Bracket Pattern](glossary/concurrency.md#bracket-pattern), [Bulkhead](glossary/concurrency.md#bulkhead), [Circuit Breaker](glossary/concurrency.md#circuit-breaker), [Resilience Combinators](glossary/concurrency.md#resilience-combinators), [Resource (VTask)](glossary/concurrency.md#resource-vtask), [Scope](glossary/concurrency.md#scope), [ScopeJoiner](glossary/concurrency.md#scopejoiner), [Structured Concurrency](glossary/concurrency.md#structured-concurrency), [Virtual Thread](glossary/concurrency.md#virtual-thread)
