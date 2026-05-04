# Applicative: Combining Independent Computations

~~~admonish info title="What We'll Learn"
- How `ap` lets us apply a wrapped function to a wrapped value
- Why `map2`, `map3`, and friends are the practical workhorses we will reach for
- Why `Applicative` is the right tool when we want to *accumulate* errors rather than stop at the first
- Where `Applicative` sits between `Functor` and `Monad`, and when each one earns its keep
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial03_ApplicativeCombining.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java)
~~~

## When `map` Is Not Enough

`Functor` lets us run a function over a single container. Most real code reaches for two or three containers at once.

A registration form has a username, a password, and an email. Each one is validated independently. We want to combine the three results into a `User` if everything is fine, or return *every* error we found if anything is wrong. `Functor.map` cannot help here; it only knows how to lift one input.

```
   Applicative: independent paths, combined at the end

   validateName("")       ────┐
   validateEmail("bad")   ────┼──> map3 ──>  Validated<User>
   validatePassword("?")  ────┘
   (all three run, errors accumulate)
```

`Applicative` is the type class that names this pattern. It adds two operations to `Functor` and gets a fistful of useful combinators in return.

---

## What an `Applicative` Provides

An `Applicative<F>` is a `Functor<F>` plus two methods:

1. **`of`** (sometimes called `pure`): lift a plain value into the container. `of(42)` becomes `Optional.of(42)`, `Either.right(42)`, `Just(42)`, depending on the instance.
2. **`ap`**: apply a function that *lives inside the container* to a value that also lives inside the container. `ap(Optional<Function<A, B>>, Optional<A>) -> Optional<B>`.

`ap` is the part that surprises new readers, because we rarely write `Optional<Function<A, B>>` ourselves. We do not need to. The library uses `ap` and `map` together to build `map2`, `map3`, `map4`, and so on, and those are what we actually call.

~~~admonish note title="Interface Signature"
```java
@NullMarked
public interface Applicative<F extends WitnessArity<TypeArity.Unary>> extends Functor<F> {

  <A> @NonNull Kind<F, A> of(@Nullable A value);

  <A, B> @NonNull Kind<F, B> ap(
      Kind<F, ? extends Function<A, B>> ff,
      Kind<F, A> fa);

  default <A, B, C> @NonNull Kind<F, C> map2(
      Kind<F, A> fa,
      Kind<F, B> fb,
      BiFunction<? super A, ? super B, ? extends C> f) {
    return ap(map(a -> b -> f.apply(a, b), fa), fb);
  }

  // map3, map4, map5 build similarly on top of ap and map
}
```
~~~

---

## The Reason We Care: Error Accumulation

`Monad.flatMap` short-circuits on the first failure. That is exactly what we want for sequencing dependent steps, and exactly the wrong thing when we want to validate a form. A user who submits a bad username, a bad password, and a bad email expects to see all three errors at once, not have to fix one and resubmit three times.

`Applicative` does not short-circuit. Every input runs, and the results combine through whatever rule the container defines. For `Validated` paired with a `Semigroup`, that rule is "concatenate the errors".

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.Semigroups;

import java.util.List;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

record User(String username, String password) {}

public Validated<List<String>, String> validateUsername(String username) {
    if (username.length() < 3) {
        return Validated.invalid(List.of("Username must be at least 3 characters"));
    }
    return Validated.valid(username);
}

public Validated<List<String>, String> validatePassword(String password) {
    if (!password.matches(".*\\d.*")) {
        return Validated.invalid(List.of("Password must contain a number"));
    }
    return Validated.valid(password);
}

// An Applicative for Validated, with a Semigroup that concatenates error lists
Applicative<ValidatedKind.Witness<List<String>>> applicative =
    ValidatedMonad.instance(Semigroups.list());

// All checks pass
Kind<ValidatedKind.Witness<List<String>>, User> ok =
    applicative.map2(
        VALIDATED.widen(validateUsername("test_user")),
        VALIDATED.widen(validatePassword("password123")),
        User::new);
// Valid(User[username=test_user, password=password123])

// Both checks fail; both errors land in the result
Kind<ValidatedKind.Witness<List<String>>, User> bad =
    applicative.map2(
        VALIDATED.widen(validateUsername("no")),
        VALIDATED.widen(validatePassword("bad")),
        User::new);
// Invalid([Username must be at least 3 characters, Password must contain a number])
```

A `Monad` cannot do this. After the first `Invalid`, `flatMap` would stop. The user would fix one error, resubmit, find another, and silently learn to dread our forms. `Applicative` is the polite choice.

---

## When to Use `Applicative` Instead of `Monad`

The rule of thumb is mechanical:

- If the next step needs to *look at* the previous result before it can run, we need `Monad.flatMap`.
- If the steps are independent, `Applicative.mapN` is enough, and we get richer error semantics for free.

`Applicative` is also the layer that allows parallel evaluation, since the inputs do not depend on each other. `Monad` cannot promise parallelism in general, because step *n+1* might be a function of step *n*'s value.

For a longer treatment with a decision flow, see [Choosing Your Abstraction Level](abstraction_levels.md).

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"`Applicative` is just `Monad` minus `flatMap`."** That is true mechanically and misses the point. The reason `Applicative` exists is precisely because some types (like `Validated` with a `Semigroup`) have a useful `Applicative` instance with *different* behaviour from their `Monad` instance. `Validated`'s `flatMap` is fail-fast; its `ap` is fail-slow. Same type, two different stories, depending on which type class we ask.
- **"`map2` is only useful with two arguments."** It is the entry point. Most real code reaches for `map3`, `map4`, or `map5` to combine four or five validated fields. The names get tedious past five; for those cases, [For comprehensions](for_comprehension.md) read better.
- **"`ap` is the operation I will call directly."** Almost never. `ap` is the primitive that `mapN` is built on. We define new `Applicative` instances by implementing `ap` and `of`, but we use them through `map2` and friends.
- **"Error accumulation only works with `Validated`."** It works with any `Applicative` whose instance defines an accumulating combine rule. `Validated` is the most common, but the same machinery applies to other accumulating types we might define ourselves.
~~~

---

~~~admonish info title="Key Takeaways"
* `Applicative` combines independent computations, where `Monad` sequences dependent ones
* `map2`, `map3`, and friends are the everyday surface; `ap` and `of` are the primitives that build them
* Error accumulation with `Validated` is the canonical reason to reach for `Applicative` rather than `Monad`
* Same type, different type-class instance, different story; `Validated`'s `flatMap` and `ap` deliberately disagree
~~~

~~~admonish tip title="See Also"
- [Functor](functor.md) - The simpler foundation that Applicative extends
- [Monad](monad.md) - For dependent computations, but with short-circuiting, not accumulation
- [Choosing Your Abstraction Level](abstraction_levels.md) - When to reach for which one
- [Validated](../monads/validated_monad.md) - The type designed for error accumulation
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Functional Programming in Java](https://www.baeldung.com/java-functional-programming) - Practical functional patterns for Java developers
- **Mark Seemann**: [Applicative Functors](https://blog.ploeh.dk/2018/10/01/applicative-functors/) - Accessible introduction with practical examples
~~~

~~~admonish info title="Hands-On Learning"
Practice Applicative combining in [Tutorial 03: Applicative Combining](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Functor](functor.md)
**Next:** [Alternative](alternative.md)
