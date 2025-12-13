# Documentation Style Guide

This document defines the house style for Higher-Kinded-J documentation. Follow these guidelines to ensure consistency across all documentation pages.

## General Principles

- Use **British English** spelling and punctuation (e.g., "colour", "behaviour", "optimisation")
- Do not use em dashes (—); use commas or semicolons instead
- Do not use emojis in documentation
- Keep explanations practical and focused on Java developers
- Avoid academic jargon; prefer accessible explanations

## Page Structure

### Title

Each page should start with a level-1 heading (`#`) that describes the topic.

**Type class pages** should use the pattern "TypeName: Brief Description":

```markdown
# Functor: The "Mappable" Type Class
# Monad: Composing Sequential Operations
# Applicative: Combining Independent Effects
```

**Monad/container pages** should use "TypeName: Description" or "TypeName" alone:

```markdown
# Maybe: Representing Optional Values
# Either: Modelling Success or Failure
```

### Opening Quote (Chapter Introductions)

Chapter introduction pages may include an opening quote to set the tone:

```markdown
# The Type Classes: Building Blocks of Abstraction

> _"Programs = Algorithms + Data Structures"_
> — Niklaus Wirth

This chapter introduces the foundational type classes...
```

Use blockquote formatting with italicised text and an attribution line.

### What You'll Learn Section

Every content page should have a "What You'll Learn" admonishment near the top, immediately after the title or introductory paragraph:

```markdown
~~~admonish info title="What You'll Learn"
- How to transform values inside containers without changing the container structure
- The difference between regular functions and functorial mapping
- Functor laws (identity and composition) and why they matter
~~~
```

### In This Chapter Section (Chapter Introductions Only)

Chapter introduction pages (`ch_intro.md`) should use an "In This Chapter" admonishment with **expanded descriptions** (1-2 sentences per item) that provide more context than the Chapter Contents list below:

```markdown
~~~admonish info title="In This Chapter"
- **Functor** – The foundational type class that enables transformation of values inside containers without changing the container's structure. Every other abstraction builds on this.
- **Applicative** – When you have multiple independent computations and need to combine their results. Unlike Monad, Applicative allows parallel evaluation since results don't depend on each other.
~~~
```

Guidelines for In This Chapter:
- Each item should have 1-2 sentences explaining the concept's purpose and significance
- Provide context that helps readers understand *why* they would use each topic
- Do not simply duplicate the brief descriptions from Chapter Contents
- Use bold for the topic name followed by an en-dash

### Chapter Contents Section (Chapter Introductions Only)

Every chapter introduction page (`ch_intro.md`) should include a "Chapter Contents" section that lists all pages in the chapter with links. This helps users on mobile devices or those who have not discovered the sidebar navigation.

```markdown
## Chapter Contents

1. [Page Title](page_file.md) - Brief description of the page content
2. [Another Page](another_page.md) - Another brief description
3. [Third Page](third_page.md) - Third description

---

**Next:** [Page Title](page_file.md)
```

Guidelines for Chapter Contents:
- Use a numbered list matching the order in SUMMARY.md
- Each item should include the page title as a link and a brief description
- Descriptions should be concise (under 10 words)
- Follow the list with a horizontal rule and a "Next" link to the first page

### Example Code Section

If the page has example code in the repository, include an admonishment linking to it:

```markdown
~~~admonish example title="See Example Code"
[FunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/functor/FunctorExample.java)
~~~
```

### Section Separators

Use horizontal rules (`---`) to separate major sections for visual clarity.

### Key Takeaways Section

If the page includes a Key Takeaways or Summary section, format it as an info admonishment:

```markdown
~~~admonish info title="Key Takeaways"
* **Point one** with explanation
* **Point two** with explanation
* **Point three** with explanation
~~~
```

### See Also Section

Use the "See Also" admonishment for **internal** Higher-Kinded-J references:

```markdown
~~~admonish tip title="See Also"
- [Foldable and Traverse](foldable_and_traverse.md) - See how Monoids power folding operations
- [Applicative](applicative.md) - Learn how Semigroups enable error accumulation
~~~
```

### Related Types Section

When a type has close relationships with other types (e.g., `Maybe` vs `Optional`), use a note admonishment:

```markdown
~~~admonish note title="Related Types"
- **`Optional<A>`** – Java's standard library optional with an HKT wrapper
- **`Either<L, A>`** – When you need an error value, not just absence
~~~
```

### Hands-On Learning Section

For pages with associated tutorials, link to them using an info admonishment:

```markdown
~~~admonish info title="Hands-On Learning"
Practice Lens basics in [Tutorial 01: Lens Basics](../tutorials/optics/Tutorial01_LensBasics.java) (7 exercises, ~8 minutes).
~~~
```

### Further Reading Section

Content pages may include a "Further Reading" admonishment at the **end** of the page, before the navigation links. This section should contain **external** references only.

**Important:** Do not add a separate markdown heading (`## Further Reading`) before the admonishment. The admonishment title serves as the heading.

```markdown
~~~admonish tip title="Further Reading"
- **Author/Source Name**: [Article Title](https://example.com/url) - Brief description
~~~
```

Guidelines for Further Reading:
- **Prefer internal "See Also" links** over external "Further Reading" links when Higher-Kinded-J already covers the topic
- **Only add external links** if they offer unique value not already covered in Higher-Kinded-J documentation (e.g., foundational articles, unique perspectives, or comprehensive treatments of a topic)
- **Verify all links resolve correctly** before adding them - broken links (404 pages) significantly harm user experience and documentation credibility
- Prefer practical, developer-focused resources over academic papers
- Java-focused resources are preferred where available
- Avoid Vavr references as Higher-Kinded-J provides its own implementations

### Navigation Links

Every content page (except chapter introductions) should end with Previous/Next navigation links:

```markdown
---

**Previous:** [Page Title](page_file.md)
**Next:** [Page Title](page_file.md)
```

Notes:
- The first page in a chapter should only have a **Next** link
- The last page in a chapter should only have a **Previous** link
- Chapter introduction pages (`ch_intro.md`) should only have a **Next** link

## Content Patterns

### Question-Style Headings

Use question-style headings to engage readers and address common concerns:

```markdown
## Why Does This Matter?
## When Should You Use Monad vs Applicative?
## How Is This Different from Optional?
```

These are particularly effective for:
- Addressing why a concept is useful
- Comparing related concepts
- Explaining when to use one approach over another

### Comparison Tables

When comparing related concepts, use markdown tables:

```markdown
| Aspect | Functor | Applicative | Monad |
|--------|---------|-------------|-------|
| Key method | `map` | `ap`, `map2` | `flatMap` |
| Combines | One effect | Independent effects | Sequential effects |
| Use case | Transform values | Parallel validation | Chained operations |
```

Tables work well for:
- Comparing type classes
- Summarising optic types
- Contrasting implementation approaches

### Type Class/Monad Page Structure

Pages documenting type classes or monadic types should follow this structure:

1. **Title** (with descriptive subtitle)
2. **What You'll Learn** admonishment
3. **Example Code** link (if applicable)
4. **Introductory paragraph** explaining the purpose
5. **Core concepts** (interface, operations, laws)
6. **Practical examples** (code with explanations)
7. **Common use cases** or "When to use"
8. **Comparison with related types** (if applicable)
9. **Summary or Key Takeaways**
10. **See Also** (internal links)
11. **Further Reading** (external links)
12. **Navigation** (Previous/Next)

## Admonishment Types

Use the following admonishment types consistently:

| Type | Usage |
|------|-------|
| `info` | "What You'll Learn", "Key Takeaways", "Hands-On Learning", "In This Chapter" |
| `tip` | "Further Reading", "See Also" |
| `example` | Links to example code |
| `note` | Important clarifications, "Related Types", additional context |
| `warning` | Potential pitfalls or common mistakes |

## Code Formatting

### Code Blocks

Use triple backticks with language specifier:

```markdown
```java
// Java code here
```
```

### Inline Code

Use backticks for:
- Method names: `map`, `flatMap`, `of`
- Class names: `Functor`, `Monad`, `Kind<F, A>`
- Package names: `org.higherkindedj.hkt`

## Terminology

- Use "type class" (two words) not "typeclass"
- Use "Higher-Kinded-J" when referring to the library by name
- Use "higher-kinded types" or "HKT" when referring to the concept

## File Naming

- Use lowercase with underscores for markdown files: `monad_error.md`
- Match the SUMMARY.md structure for consistency

## Chapter Structure

Within a chapter, pages should follow this order:
1. Chapter introduction (`ch_intro.md`)
2. Core concepts in logical order
3. More advanced topics towards the end

## Checklist for New Pages

When creating a new documentation page, ensure:

- [ ] Title is clear and descriptive
- [ ] "What You'll Learn" admonishment is present
- [ ] Example code links are included (if applicable)
- [ ] Sections are separated with horizontal rules
- [ ] "Key Takeaways" uses info admonishment (if applicable)
- [ ] "See Also" section for internal links (if applicable)
- [ ] "Further Reading" section with validated external links
- [ ] Previous/Next navigation links at the end
- [ ] British English spelling throughout
- [ ] No emojis
- [ ] All code examples are properly formatted

## Checklist for Chapter Introductions

When creating a chapter introduction page (`ch_intro.md`), ensure:

- [ ] Title reflects the chapter theme
- [ ] Opening quote and introductory prose
- [ ] "In This Chapter" admonishment with **expanded descriptions** (1-2 sentences per item, not just brief phrases)
- [ ] "Chapter Contents" section with numbered links and brief descriptions
- [ ] "In This Chapter" and "Chapter Contents" are distinct (expanded context vs. brief navigation)
- [ ] **Next** link to the first page in the chapter (no Previous link)
- [ ] British English spelling throughout
- [ ] No emojis
