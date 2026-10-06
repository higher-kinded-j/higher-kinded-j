<!-- description: Describe optic operations as a value with the Free Monad DSL, then run, log or check the same program with different interpreters. -->

# Programs as Data

> _"Any sufficiently advanced technology is indistinguishable from magic."_
>
> – Arthur C. Clarke

---

Most optic work is "update this nested field". Sometimes the problem is different: describe a sequence of optic operations as data, then decide later how to run it. The Free Monad DSL turns optic operations into a value you can pass around, inspect, and run under different strategies: production, audit, a checked run, or a test interpreter of your own. Interpreters are the strategies that turn the description into a result.

Here is the whole idea before any of the theory. One program, described once, run three different ways. Every line compiles against the real library on every build:

<!-- verify -->
```java
// A description, not an action: nothing has touched the account yet
Free<OpticOpKind.Witness, Account> withdrawal = Fixture.withdraw(Fixture.account, 30);

// Run it for real
DirectOpticInterpreter direct = OpticInterpreters.direct();
Account settled = direct.run(withdrawal);
// Account[id=ACC-1, balance=70]

// Run the same value again, recording every optic operation on the way
LoggingOpticInterpreter logging = OpticInterpreters.logging();
Account audited = logging.run(withdrawal);
List<String> trail = logging.getLog();
// one entry per optic operation the program performed

// Or run it and get a report of what went wrong instead of the result
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationOpticInterpreter.ValidationResult check = validator.validate(withdrawal);
boolean safeToRun = check.isValid();
// true: no nulls written, no modifier threw
```

The account starts on 100 and the withdrawal is 30. `Fixture` is the compiled example's own setup, not library API.

~~~admonish warning title="`validating()` is a checked run, not a dry run"
Despite the name, `validate` **executes** the program. Its own javadoc is explicit: operations are run so that `flatMap` chaining produces the right values, and the validation is collected alongside. A `modify` modifier is applied twice, once to check it and once to perform it. So it is safe for pure modifiers over immutable data, and unsafe for anything with a side effect.

For genuine inspection with nothing executed, use `ProgramAnalyser.analyse(program)`, whose traversal is structural and never runs a step. Its counts are a lower bound: a step reached through `flatMap`, inside an error handler, or under an `Ap` node stays out of sight until the program runs. `hasOpaqueRegions()` reports only the `flatMap` continuations, so `false` does not mean every step was inspected.
~~~

~~~admonish tip title="Why this matters"
The three blocks differ by one line. `withdrawal` is an ordinary value: it can be stored in a field, passed to a method, returned from one, and run later or never. That is the property the rest of this group trades on. An audit trail stops being logging statements scattered through the code and becomes a second interpreter over the same description; a structural analysis of what a program contains stops being guesswork and becomes a walk over the value.
~~~

If you have not yet hit a problem that needs this, you do not need this group. Come back when an audit requirement, a testability concern, or a multi-mode execution scenario forces the issue.

---

## Pages in this group

[Decision Trees](decision_trees.md#tree-4-which-interpreter) picks the interpreter for what you want back from a program.

1. [Free Monad DSL](free_monad_dsl.md): Optic programs as composable data
2. [Interpreters](interpreters.md): Several execution strategies for one program

~~~admonish info title="Hands-On Learning"
Practise the Fluent API and the Free Monad DSL in the [Fluent & Free DSL Journey](../tutorials/optics/fluent_free_journey.md) (22 exercises).
~~~

[Effect Handlers](../effect/effect_handlers_intro.md) is the Effect Path equivalent: free-monad-style algebraic effects for computations rather than optics.

---

**Previous:** [Auditing Complex Data](auditing_complex_data_example.md)
**Next:** [Free Monad DSL](free_monad_dsl.md)
