<!--
Title: a sentence that says what the change does, starting with a verb, such as
"Make OpticOpFunctor.map apply its function". No `feat:` or `docs:` prefix, and no issue number.
See https://github.com/higher-kinded-j/higher-kinded-j/blob/main/CONTRIBUTING.md#pull-request-titles
-->

## What and why

<!-- What does this change, and why? Say if it adds a dependency or changes a public API, what a reviewer should look at first, and anything you found on the way that is not part of this change. -->

Closes #

<!--
"Closes #" and the issue number closes that issue when this pull request merges. For an issue this does only part of, write "Part of #" and the number instead, so the issue stays open. If there is no issue, delete the line.
GitHub treats "close", "fix" or "resolve", in any form such as "closes" or "fixed", just before an issue number as closing it, even in "this does not close" followed by the number. Keep those words away from an issue that should stay open.
See https://github.com/higher-kinded-j/higher-kinded-j/blob/main/CONTRIBUTING.md#linking-the-issue
-->

## How it was tested

<!--
Name the tests you added. Then show they would catch the problem:
- for a bug fix, keep the new tests, undo the fix under src/main, run them, and paste which fail and with what message; then restore the fix;
- for a feature, break the new code instead, for example by returning the wrong value, and paste what fails.
For a change to the docs only, say which checks you ran.
If a check fails for you on the `main` branch too, name it and its message here.
-->

## Book changes

Delete this section if the pull request changes neither the book nor a rule, refusal or diagnostic the book documents.

- [ ] `./gradlew :hkj-examples:test :hkj-examples:bookVerify` and `hkj-book/check.sh` pass
- [ ] The prose follows the [Style Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md), and the chapter's own guide where its Chapter Guides table lists one
- [ ] Each new rule or refusal is where the chapter's guide says it lives; a generated page, such as Compiler Messages, is regenerated rather than edited
- [ ] The readability counts this lowered are quoted here, and so is whether a war story or dialogue was used and why, or why a candidate site went without

## Checklist

- [ ] `./gradlew build` passes locally; it runs the tests, the coverage checks and Spotless
- [ ] `./gradlew spotlessCheck` passes, after `./gradlew spotlessApply` or through the pre-push hook (see [Coding Style](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/CONTRIBUTING.md#coding-style))
- [ ] New code follows its siblings (see [Matching the Code Around It](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/CONTRIBUTING.md#matching-the-code-around-it))
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] The Javadoc, README, book and [Claude Code skills](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/CONTRIBUTING.md#the-claude-code-skills) that describe what I changed now match it
- [ ] I have added an entry to `hkj-book/src/release-history/unreleased.md` for a change a user can see, with its upgrade impact in that page's Upgrading section
- [ ] The GitHub Actions CI checks pass
