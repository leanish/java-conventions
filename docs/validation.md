# Release validation

One section per release: its candidates, what each reviewer added, the rough cost per model, and the evidence of the
real runs. Known limitations are in [coverage-gaps.md](coverage-gaps.md).

## 0.7.0 (2026-11-19)

Released from `v0.7.0-rc.2` (8fd0248) as `v0.7.0` (06c2534), compared with the deployed `v0.6.5`. Published to GitHub
Packages from the tag; the Gradle Plugin Portal publish is done separately by the owner.

### Candidates

- **rc.1** (19fac81): everything merged since 0.6.5, mostly fixes from a code audit, plus tool upgrades.
- **rc.2** (8fd0248): rc.1 plus #147, which fixes the blocker the last review of rc.1 found and several of the rc.1
  reviews' minor findings. The others were accepted as known limitations or deferred.

### Reviews per candidate

rc.1:

- Three Haiku reviewers (claims, checklist, gap-hunter): no blocker. They found two inaccuracies in the release text:
  the bare `-DexcludeTags` flag, and the hook path in linked worktrees. Both were fixed in rc.2. Their other findings
  were intended, already documented changes, verification gaps, and stale wording in merged PR descriptions.
- Sol, unprimed: eight findings it first marked blocking. After settling, none blocked, because each needs an
  uncommon setup or affects only this repository's own tests. Two were fixed in rc.2: the hook lookup now ignores an
  inherited `GIT_DIR`, and the tests ignore a developer's global git config. The rest became known limitations or
  accepted changes. Sol also added several minor text findings and two pre-existing limitations, and independently
  ran the publishing ref guard on all nine cases.
- Astra, last review: found the blocker everyone else missed. The new run condition of
  `jacocoTestCoverageVerification` looked only at loose classes in `classDirectories`. A build that supplied classes
  through `additionalClassDirs` (or as a JAR) therefore skipped verification even with execution data, where 0.6.5
  verified. Astra also found further minor gaps, including the `installGitHooks` task-type change and the
  interface-only coverage case.

The #147 fix PR, before merging:

- Haiku (three reviewers): a missing test for the no-data branch through `additionalClassDirs`, which was added. They
  also corrected the hook-ownership and blank-variable wording.
- Sol: confirmed the fix and asked for three wording corrections.
- The new `GIT_DIR` regression test caught a first attempt that removed variables from the exec environment in place,
  which didn't reach git.

rc.2:

- Haiku (three reviewers): no blocker and no new confirmed defect.
- Sol, unprimed in a fresh thread: three findings it first marked blocking. The Windows `setExecutable` one was
  withdrawn, since the JDK returns `true` there. The other two, test fixtures inheriting git's repository variables
  and a possible hang with a forwarding git shim, affect only this repository's own test suite and go to 0.7.1. Sol
  also found new minor limitations, listed in coverage-gaps.
- Astra, last review: no blocker. It added another way per-project base-package inference can narrow NullAway and
  PIT, and three hook edge cases: a directory at `scripts/git-hooks/pre-commit`, an ownership record with invalid
  UTF-8, and CRLF checkouts of the test fixtures.

### Rough cost

- Haiku: about 90K tokens for rc.1, 79K for the #147 fix and 166K for rc.2 (three reviewers each time).
- Sol, high effort: about 5.0M input tokens on the rc.1 thread (unprimed review, settling, fix plan, fix review) and
  3.1M on the rc.2 thread. Over 90% of input was cached.
- Astra, medium effort: about 2.35M input tokens for rc.1 and 2.16M for rc.2, mostly cached.
- Astra's rc.1 pass was the most valuable review of this release: it found the only blocker.

### Real runs

- The plugin on JDK 25: `./gradlew check publishToMavenLocal` on rc.1 (182 tests) and
  `./gradlew clean check publishToMavenLocal` on rc.2 (185 tests). Both had 0 skipped and 0 failures, plus the full CI
  on each PR.
- Two adopter builds on each candidate. Each changed only the plugin version and put Maven local first in its plugin
  repositories:
  - one ran `check publishToMavenLocal` on rc.1 and `clean check publishToMavenLocal` on rc.2. It had 312 tests each
    time, including a LocalStack integration test. On rc.1 the generated POM had the expected coordinates,
    developer, SCM and runtime dependencies;
  - the other ran `check` on rc.1 and `clean check` on rc.2, where coverage verification ran. Error Prone 2.52.0
    printed new warnings, none failing.
- `installGitHooks` in a throwaway clone of an adopter:
  - it kept the project's own `scripts/git-hooks/pre-commit`;
  - without that file, it replaced the hook 0.6.5 had installed with the new bundled hook;
  - the new hook refused a commit with an unstaged change to a staged file, and accepted it once fully staged;
  - on rc.2, with `GIT_DIR` set to another throwaway repository, it updated the clone's hook and left the other
    repository untouched.
- The release operator ran two local checks, both of which passed:
  - `./gradlew check publishToMavenLocal publishPlugins --validate-only` on the release branch;
  - an adopter's `clean check` against `0.7.0` from Maven local.
- The "Publishing GitHub Packages" run from `v0.7.0` (Actions run 44433572480) passed all its jobs: legacy JDK 17 and
  21 tests, the JDK 25 check, the build and stage step, and the upload.
