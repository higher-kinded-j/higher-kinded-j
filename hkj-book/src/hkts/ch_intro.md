<!-- description: Why Java needs an encoding for higher-kinded types, how a witness declares its arity, what one call costs, and how to add your own type. -->

# Higher-Kinded Types

<!-- source: Thomas Pynchon, Gravity's Rainbow (1973), "Proverbs for Paranoids, 3", p. 251 in the Vintage edition; wording as in the e-book text, where one hand transcription adds "the" before "answers" -->
> _"If they can get you asking the wrong questions, they don't have to worry about answers."_
>
> — Thomas Pynchon, *Gravity's Rainbow* (Proverbs for Paranoids, 3)

---

The right question is not "how do I map over this `List`?" or "how do I map over this `Optional`?" The right question is "how do I map over *any* container?" Java cannot ask it directly, because a type parameter cannot stand for a container that still waits for its element type. This group is about the encoding that lets Higher-Kinded-J ask it anyway.

Read this group when you want the reasons and the mechanics behind the encoding, or when you add a type of your own. The Ship pages in the [Chapter Contents](foundations_intro.md#chapter-contents) are enough to write code that works for any container, so you can skip the group until then.

---

## Pages in this group {#pages-in-this-group}

1. [Why Higher-Kinded Types?](hkt_introduction.md): What a higher-kinded type is, and why Java needs an encoding for one
2. [Type Arity](type-arity.md): How a witness declares how many type arguments its container still needs
3. [Lifting the Hood](lifting_the_hood.md): One call traced through the encoding, step by step
4. [Adding Your Own Type](extending-simulation.md): A witness, a helper and an instance for a type of your own, the laws that prove them, and a Path

The [Foundations FAQ](faq.md) answers the design questions this group raises, such as why `Kind` has two type parameters, and what `widen` and `narrow` cost.

---

**Previous:** [Check Your Understanding](self_check.md)
**Next:** [Why Higher-Kinded Types?](hkt_introduction.md)
