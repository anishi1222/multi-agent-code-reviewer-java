# Release Notes

## Update Rules (Template)

Reference checklist: `reports/anishi1222/multi-agent-code-reviewer/documentation_sync_checklist_2026-02-17.md`

1. Add a new date section with the same structure in both EN and JA files.
2. Create and push an annotated tag (for example: `vYYYY.MM.DD-notes`).
3. Publish a GitHub Release from the tag and include EN/JA summary notes.
4. Update `README_en.md` and `README_ja.md` with release references and URLs.

## 2026-02-17

### Summary
- Completed the final remediation split workstream (PR-1 through PR-5).
- Closed remaining security/DI consistency backlog items and marked the final checklist as complete.
- Performed focused regression tests, full test validation, and end-to-end execution verification.

### Highlights
- PR-1: Changed `GithubMcpConfig.buildMcpServers()` to `Optional<Map<String,Object>>` (removed null-return contract).
- PR-2: Removed `resolvedToken` from `ReviewRunRequest`; token is now passed only at the execution boundary.
- PR-3: Introduced structured sandbox boundaries for `CustomInstruction` and explicitly enforced system-instruction precedence.
- PR-4: Added `LongSupplier` clock injection to `ContentCollector` and switched joined-cache validation to version-based invalidation.
- PR-5: Removed no-arg constructor from `CopilotService` and standardized on DI constructor usage.

### Validation
- Focused tests passed: `CopilotServiceTest`, `ReportServiceTest`, `SkillServiceTest`.
- Full suite validation: `mvn -q -DskipITs test`; confirmed 0 failures/errors in Surefire reports.
- Runtime validation: confirmed exit code 0 for `mvn clean package` and `run --repo ... --all` execution.

### References
- Final checklist: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_checklist_2026-02-16.md`
- Final summary: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_summary_2026-02-17.md`
- Release notes EN/JA alignment guide: `reports/anishi1222/multi-agent-code-reviewer/release_notes_bilingual_alignment_2026-02-17.md`
- Release tag: `v2026.02.17-notes`
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer-java/releases/tag/v2026.02.17-notes

## 2026-02-16

### Summary
- Continued the large-class decomposition track and completed modular splits across CLI, target collection, orchestrator, report pipeline, and Copilot startup flow.
- Preserved behavior while reducing command/service class responsibility density and improving testability through focused collaborators.
- Verified full regression safety after the latest `CopilotClientStarter` extraction.

### Highlights
- CLI refactoring:
  - `ReviewCommand` now delegates parsing/resolution/preparation/request-build/execution to dedicated classes.
  - `SkillCommand` now delegates parsing/preparation/execution/output formatting to dedicated classes.
  - Explicitly rejects direct token values for `--token` (stdin/env based flow only).
- Local source collection refactoring:
  - `LocalFileProvider` responsibilities split into candidate collection, candidate processing, content formatting, and normalized selection config.
- Orchestrator/report refactoring:
  - Added dedicated execution/result pipeline/context/precompute components in orchestrator.
  - Split report/summary internals into parser/formatter/builder helpers.
- Copilot startup refactoring:
  - `CopilotService` now delegates CLI path resolution, health checks, timeout resolution, startup error formatting, and startup execution to dedicated components.
  - Added `CopilotClientStarter` to isolate start-time timeout/cause mapping and safe close behavior.

### Validation
- Focused tests: 101 run, 0 failures, 0 errors
- Full test suite: 760 run, 0 failures, 0 errors

## 2026-02-14

### Summary
- Enforced supply-chain guardrails in Maven and GitHub Actions.
- Added CI required checks and validated branch protection behavior with a real PR.
- Documented dependency and CI policies in both English and Japanese READMEs.

### Highlights
- Maven policy hardening:
  - Checksum verification for Central artifacts is enforced.
  - SNAPSHOT dependencies/plugins are blocked via Maven Enforcer.
- Dependency Review policy hardening:
  - PRs fail on vulnerabilities with severity `moderate` or higher.
  - Denied licenses: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- CI enforcement:
  - Added CI workflow jobs `Supply Chain Guard` and `Build and Test`.
  - Confirmed required checks are correctly recognized by branch protection.

### Branch Protection (main)
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- Additional protections:
  - `strict` status checks enabled
  - `enforce_admins` enabled
  - `required_conversation_resolution` enabled

### Merged PRs
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### Affected Files (PR #10)
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`

## 2026-02-15

### Summary
- Addressed all 26 findings (High 4 / Medium 10 / Low 12) reported by the 4 reviews (best-practices / code-quality / performance / security).
- Delivered improvements across security, maintainability, performance, and DI/config consistency.
- Merged PR #13 into `main` using squash merge.

### Highlights
- Security hardening:
  - Strengthened `CustomInstructionSafetyValidator` with regex-based detection and Unicode normalization (NFKC).
  - Applied safety validation to auto-loaded custom instructions.
  - Switched `TemplateService` template-name validation to allowlist + normalized path containment checks.
  - Masked auth header values in `GithubMcpConfig` string representation.
- Code quality and design:
  - Removed 3 legacy duplicate command classes (dead code).
  - Unified Micronaut DI usage for `FeatureFlags` and `SkillRegistry`.
  - Reduced complexity in `ReviewCommand.applyOption` by splitting into focused handlers.
  - Replaced wildcard imports with explicit imports.
- Performance:
  - Added one-pass local source collection API (`collectAndGenerate`) in `LocalFileProvider`.
  - Reused shared `ReviewContext`, converted `FindingsExtractor` to single-pass parsing, and reduced template expansion overhead in `SummaryGenerator`.

### Validation
- `mvn -B -ntp clean package`: BUILD SUCCESS
- Test result: 431 run, 0 failures, 0 errors

### Merged PRs
- [#13](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/13): fix: resolve all findings from 2026-02-15 reports

### Reference
- Detailed checklist: `reports/anishi1222/multi-agent-code-reviewer/remediation_checklist_2026-02-15.md`

## 2026-02-15 (v0.03)

### Summary
- Added multi-pass review capability allowing each agent to perform multiple review passes and merge results into a single report.
- Applied additional fixes for all review findings (High / Medium / Low) and strengthened test coverage.
- Improved CI workflow dependencies and added default configuration values for stability.

### Highlights

#### New Feature: Multi-Pass Review
- Each agent now performs `review-passes` iterations (default: 1), with results merged by `ReviewResultMerger`.
- All passes are submitted concurrently to a Virtual Thread pool, governed by `Semaphore(parallelism)` for concurrency control.
- Merge is string-concatenation only (no additional AI calls).
- Configurable via `reviewer.execution.review-passes` in `application.yml`.

#### Security Hardening
- Strengthened path validation in `ReviewTarget`.
- Added CLI path safety checks in `CliPathResolver`.
- Expanded prompt-injection detection and normalization in `CustomInstructionSafetyValidator`.
- Improved MCP header masking behavior in `GithubMcpConfig`.

#### Performance & Stability
- Improved retry behavior and timeout handling in `ReviewAgent`.
- Enhanced startup cleanup and summary fallback quality in `ReviewOrchestrator`.
- Added per-agent duplicate finding elimination in `ReviewResultMerger`.
- Refactored local file collection logic in `LocalFileProvider` for efficiency.
- Improved retry and error handling in `CopilotService`.

#### Configuration
- Added `AgentPathConfig` to externalize agent path settings.
- Added default values and `@Nullable` annotations to `LocalFileConfig`.
- Added default `reviewer.local-files` settings in `application.yml`.

#### Testing
- Added `ReviewResultMergerTest` (9 test cases).
- Added/expanded `AgentPathConfigTest`, `LocalFileConfigTest`, `ReviewTargetTest`, `LocalFileProviderTest`, `CommandExecutorTest`, `CliPathResolverTest`.
- Total tests: 453 (0 failures, 0 errors).

#### Documentation
- Updated README (EN/JA) with multi-pass review feature description, configuration examples, and architecture diagram.

### Merged PRs
- [#16](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/16): fix: add default reviewer.local-files settings
- [#17](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/17): fix: update build-native-image job dependencies
- [#18](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/18): fix: remediate 2026-02-15 report findings (all severities)
- [#19](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/19): feat: multi-pass review
- [#20](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/20): docs: add multi-pass review to README
- [#21](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/21): fix: all review findings and strengthen coverage

### Validation
- `mvn test`: 453 run, 0 failures, 0 errors
- CI: Build and Test / Native Image Build / Supply Chain Guard / Dependency Review all passed
