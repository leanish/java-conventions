# Known limitations

Limitations known at each release, with their effect and what avoids them. Newer releases first. How each release was
reviewed is in [validation.md](validation.md).

## 0.7.0

Found by the 0.7.0 release reviews and accepted. Each needs an uncommon setup, or affects only this repository's own
tests. The defects and doc gaps are candidates for 0.7.1. Deliberate behaviors are listed too, so upgraders know them.

### Consumer builds

- **Late `group` and owner-dependent publishing.** These cases apply when the GitHub owner comes from a `group` set
  after the `plugins { … }` block:
  - The POM URL or SCM properties are configured after evaluation. If the build already finalized them
    (`finalizeValue()`, `disallowChanges()`), configuration fails.
  - An existing non-Maven repository named `GitHubPackages` collides with the one the conventions add.
  - A blank `gpr.user` or `gpr.key` placeholder fails configuration, even for `help` or a repository-specific task.

  Each case needs consumer configuration beyond the late `group`. An owner known when the plugin is applied (the
  owner property or environment variable, or `group` set before `plugins { … }`) avoids all three.
  `leanish.conventions.publishing.githubPackages.enabled=false` avoids only the repository collision and the
  credentials lookup. Removing the blank placeholder avoids the third. Applying `maven-publish` only after
  evaluation, with publishing conventions on, is unverified.
- **Per-project base package inference can narrow NullAway and PIT.** These cases are covered by inference from
  `src/main/java` alone; setting `leanish.conventions.basePackage` explicitly covers every package:
  - A subproject that took its parent's inferred package now infers its own from its `src/main/java`. If it has more
    packages in another source directory, they are left out.
  - The detector can take a `package` line from a block comment before the real declaration. This is a pre-existing
    parser problem, which now reaches more subprojects.
  - A parent that assigns exactly its own inferred value in a build script is still treated as inferred.
- **Coverage verification's run condition.** The conventions replace the condition of `jacocoTestCoverageVerification`:
  - A condition another plugin or the build put on it before the conventions applied is dropped. A condition added
    after them still applies.
  - Without execution data, a JAR given as class input doesn't make it run, as in 0.6.5.
  - With tests skipped, execution data left by an earlier run can still be verified (pre-existing).
- **The Jackson import rule** covers ordinary and static imports of `com.fasterxml.jackson.core` and
  `com.fasterxml.jackson.databind`, not `import module` declarations.
- **Checkstyle files from the root project:** only `checkstyle.xml` and `suppressions.xml` are materialized. Any other
  file they reference relative to `${config_loc}` must be provided separately (pre-existing).

### Git hook installation

- **Installing from linked worktrees at the same time.** If two linked worktrees with different hook sources install
  at the same instant, the hook and its record can end up disagreeing. The next install then keeps the hook with a
  warning. A record the installer can't write fails `build`. When the hook's bytes differ from the source, the
  installer reads the record to decide ownership, and a record with invalid UTF-8 then fails it instead of keeping
  the hook.
- **Hooks that are already identical.** On POSIX filesystems an already-identical hook is chmodded to `0755` on every
  install. That can reset a deliberate mode, or fail in a shared checkout where another user owns the hook.
- **A hooks directory that resolves outside the repository's git directory** (for example through a symbolic link)
  is skipped with a warning. This is deliberate, but the README doesn't mention it.
- **A directory at `scripts/git-hooks/pre-commit`** fails `build` instead of being ignored.
- **`GIT_CONFIG` pointing at another file.** The `core.hooksPath` check then reads a different config than Git uses to
  find hooks. If the configured hooks directory is inside the repository's git directory, the hook is installed there.
- **The bundled hook's comparison.** It compares the staged files with the working copy, not the whole staged tree.
  Its working-tree checks were already limited this way before 0.7.0. The new comparison also has gaps:
  - it skips staged deletions;
  - `GIT_LITERAL_PATHSPECS=1` makes it miss changed paths;
  - if creating one of its temporary files fails, the earlier ones are left behind.

### This repository's own build and tests

- Git test fixtures inherit `GIT_DIR`, `GIT_WORK_TREE`, `GIT_COMMON_DIR` and `GIT_INDEX_FILE`. Running the tests with
  those variables exported can point fixture commands at another repository.
- A git shim that forwards through `PATH` can make the pre-commit failure-injection test hang. This was seen once
  while preparing 0.7.0 and not reproduced since.
- `./gradlew check -x test` no longer runs the mirror comparison, which is now a test.
- The two historical-hook fixtures depend on LF line endings, and no Git attribute pins them. A CRLF checkout fails
  the upgrade test.
- The publishing ref-guard test checks that two identical guard blocks exist, not which job each sits in. Both
  placements were checked by hand for 0.7.0.
- Not yet verified:
  - configuration-cache reuse of the mirror writer;
  - a real git launch failure reaching the "git can't run" branch;
  - a consumer build on a JDK 21 toolchain;
  - the `jacksonTwoImport` suppression in a root `suppressions.xml`.
- This repository's own GitHub Packages publishing block still lets a blank environment credential take precedence
  over `gpr.*` (pre-existing; consumers get the blank-as-unset behavior).
