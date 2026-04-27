# Advanced Optics

> _"Any sufficiently advanced technology is indistinguishable from magic."_
>
> – Arthur C. Clarke

---

Most optic work involves the everyday tools: lenses, prisms, traversals, and the Focus DSL on top of them. But sometimes the problem at hand is not "update this nested field" but "describe a sequence of optic operations as data, then decide later how to run them."

This chapter is for those occasions. The Free Monad DSL turns optic operations into a value you can pass around, inspect, and execute under different strategies (production, audit, dry-run, mock). Interpreters are the strategies that turn descriptions into results.

If you have not yet hit a problem that needs this, you do not need this chapter. Come back when an audit requirement, a testability concern, or a multi-mode execution scenario forces the issue.

~~~admonish info title="In This Chapter"
- **Free Monad DSL** – Describe optic operations as composable data structures rather than executing them immediately. Enables dry-runs, audit trails, and the same program running under different execution policies.
- **Interpreters** – The execution strategies for Free Monad DSL programs. Covers direct execution for production, logging for debugging, validating for safety, and how to define your own interpreter for custom needs.
~~~

~~~admonish tip title="See Also"
- [Java-Friendly APIs](ch4_intro.md), the everyday optic APIs (Focus DSL, Fluent API).
- [Effect Handlers](../effect/effect_handlers_intro.md), the Effect Path equivalent: free-monad-style algebraic effects for computations rather than optics.
~~~

---

## Chapter Contents

1. [Free Monad DSL](free_monad_dsl.md) - Building optic programs as composable data
2. [Interpreters](interpreters.md) - Multiple execution strategies for the same program

---

**Next:** [Free Monad DSL](free_monad_dsl.md)
