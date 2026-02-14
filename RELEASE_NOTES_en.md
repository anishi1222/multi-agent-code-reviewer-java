# Release Notes

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
