# VResultPath

_Async work that can fail with a typed, domain error: the railway for the shape real services actually have._

`VTask<Either<E, A>>` (call something remote, get back either a value or a typed failure) is the single most common effect stack in real services. HKJ models both halves well separately (`VTaskPath` for async, `EitherPath` for typed errors); `VResultPath<E, A>` is their composition as a first-class path, so neither `Kind` ceremony nor a hand-rolled `EitherT` bridge ever surfaces:

``` java
VResultPath<OrderError, OrderResult> process(OrderRequest request) {
    return validateShippingAddress(request.shippingAddress())    // VResultPath<OrderError, Address>
        .via(addr     -> lookupAndValidateCustomer(request.customerId()))
        .via(customer -> buildValidatedOrder(request, customer))
        .via(order    -> reserveThenFulfil(order))               // short-circuits on first failure
        .recoverWith(this::retryOnce)                            // typed-error-aware recovery
        .mapError(this::enrich);                                 // transform the error channel
}
```

It speaks the family vocabulary exactly (`map`/`via`/`then` on the success channel, `mapError`/`recover`/`recoverWith`/`orElse`/`bimap` on the error channel), so it reads as "an `EitherPath` that happens to be async". `run()` returns the carrier `VTask<Either<E, A>>` for the boundary; nothing executes until it runs, and defects (thrown exceptions) stay on the `VTask` failure channel, never masquerading as typed errors.

## Construction

``` java
VResultPath<E, A> p1 = Path.vresultRight(value);          // pure success
VResultPath<E, A> p2 = Path.vresultLeft(error);           // typed failure
VResultPath<E, A> p3 = Path.vresultEither(either);        // lift a decided Either
VResultPath<E, A> p4 = Path.vresult(vtaskOfEither);       // lift the carrier
VResultPath<E, A> p5 = Path.vresultDefer(() -> decide()); // defer the decision itself
```

## Outcome-aware structured concurrency

The structured-concurrency surface keeps typed failures **in the value channel**: no thrown-and-recovered domain errors, no `instanceof` bridges, no side flags.

``` java
// First warehouse to succeed wins; typed failures do not abort, they're collected.
VResultPath<NonEmptyList<OrderError>, Reservation> reservation =
    VResultPath.firstSuccess(List.of(warehouse1, warehouse2, warehouse3))
        .withTimeout(Duration.ofSeconds(10), () -> NonEmptyList.of(OrderError.timeout()));

// Compensation is decided from the result value, not an AtomicBoolean.
VResultPath<OrderError, OrderResult> fulfilled =
    VResultPath.bracketOutcome(
        reserveInventory(order),                              // acquire
        reservation -> payThenShip(order, reservation),       // use
        (reservation, outcome) -> outcome.isRight()
            ? confirm(reservation)
            : releaseAndRefund(reservation),                  // release sees the outcome
        SystemError::fromDefect);                             // defects join the typed channel here
```

- **`firstSuccess`**: first `Right` wins and cancels the rest; if every candidate fails, the result is `Left` of **all** their errors (`NonEmptyList`, in candidate order). A winning `Right` outranks defects: a candidate that throws is a defect, and it fails the race only when no candidate ever succeeds.
- **`allSucceed`**: fail-fast; the first typed failure cancels the remaining tasks and becomes the result.
- **`allSucceedAccumulating`**: run everything to completion and collect every typed failure at once.
- **`withTimeout(duration, onTimeout)`**: a timeout becomes the designated typed error, on the railway.
- **`bracketOutcome`**: release *always* runs and receives the `Either` outcome, so confirm-vs-compensate is decided from the result; defects inside `use` are typed through `onDefect` first, so release always observes a real outcome. This is the substrate the order example's deferred compensation Saga hangs on.

~~~admonish note title="Why these live on VResultPath, not Scope"
The issue sketched `Scope.firstSuccess(...)`, but `Scope` lives in `hkt.vtask`, which `hkt.effect` depends on; statics there referencing `VResultPath` would create a package cycle. The combinators sit on `VResultPath`, implemented over the same `Scope`/`ScopeJoiner` substrate (which gained an `Either`-aware `firstSuccessEither` joiner).
~~~

## Escaping the path

``` java
VTask<Either<E, A>> carrier = path.run();                 // the boundary type
EitherPath<E, A>   decided = path.toEitherPath();         // blocking: runs the task
VTaskPath<A>       collapsed = path.toVTaskPath(e -> new DomainException(e)); // typed error -> failure channel
VTask<B>           folded  = path.fold(this::onError, this::onValue);
```

~~~admonish tip title="See Also"
- [VTaskPath](path_vtask.md) - The async half
- [EitherPath](path_either.md) - The typed-error half and the shared vocabulary
- [Effect Path Overview](effect_path_overview.md) - Where every path sits in the lattice
~~~
