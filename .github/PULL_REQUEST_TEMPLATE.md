## Description

Please include a summary of the change and which issue is fixed or feature is added. Please also include relevant motivation and context. List any dependencies that are required for this change.

Fixes # (issue number) / Relates to # (issue number)

## Type of change

Please delete options that are not relevant.

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Refactoring/Code cleanup
- [ ] Test addition/update

## How Has This Been Tested?

Please describe the tests that you ran to verify your changes. Provide instructions so we can reproduce. Please also list any relevant details for your test configuration.

- [ ] `./gradlew test` passes locally
- [ ] New unit tests added/updated
- [ ] Relevant examples in `MonadSimulation.java` or `OrderWorkflowRunner.java` updated/tested (if applicable)


## Book changes

Delete this section if the pull request changes neither the book nor a rule, refusal or diagnostic the book documents.

- [ ] `./gradlew :hkj-examples:test :hkj-examples:bookVerify` and `hkj-book/check.sh` pass
- [ ] The prose follows the [Style Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md), and the chapter's own guide where its Chapter Guides table lists one
- [ ] Each new rule or refusal is where the chapter's guide says it lives; a generated page, such as Compiler Messages, is regenerated rather than edited
- [ ] The readability counts this lowered are quoted here, and so is whether a war story or dialogue was used and why, or why a candidate site went without

## Checklist:

- [ ] My code follows the style guidelines of this project (Standard Google Java Conventions)[**See Google Java Style Guide**](https://google.github.io/styleguide/javaguide.html)
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation (e.g., README.md, Javadoc)
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes (`./gradlew test`)
- [ ] Any dependent changes have been merged and published in downstream modules (if applicable)
- [ ] I have checked that the GitHub Actions CI build passes with my changes

## Additional Comments (Optional)

Add any other comments here.