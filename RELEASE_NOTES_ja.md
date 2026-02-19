# リリースノート

## 更新ルール（テンプレート）

参照チェックリスト: `reports/anishi1222/multi-agent-code-reviewer/documentation_sync_checklist_2026-02-17.md`

1. EN/JA 両方に同一構成で新しい日付セクションを追加する。
2. 注釈付きタグ（例: `vYYYY.MM.DD-notes`）を作成して push する。
3. タグから GitHub Release を作成し、EN/JA 要約を本文に含める。
4. `README_en.md` と `README_ja.md` にリリース参照とURLを追記する。

## 2026-02-19 (v4)

### 概要
- README EN/JA の運用完了チェックを 2026-02-19 時点に更新しました。
- チェック項目に PR #76 の信頼性対応を明示し、追跡性を改善しました。
- ドキュメント同期変更を PR #78 で `main` に反映しました。

### 主な変更

#### PR #78: 運用完了チェックの更新
- `README_en.md` / `README_ja.md` の該当セクション日付を 2026-02-18 から 2026-02-19 に更新。
- 固定テスト件数表現を全テストスイート合格の表現へ変更。
- PR #76（idle-timeout scheduler 停止時フォールバック）完了項目を追加。

### 検証
- PR #78 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78): 2026-02-19 運用完了チェック更新

---

## 2026-02-19 (v3)

### 概要
- 停止済み共有 scheduler へのアイドルタイムアウト登録で発生していたレビュー失敗を修正しました。
- スケジューリング不可時は idle watchdog なしで処理継続するフォールバックを実装し、実行の安定性を向上しました。
- shutdown 済み scheduler 経路の回帰テストを追加し、修正を PR #76 で `main` に反映しました。

### 主な変更

#### PR #76: Idle Timeout Scheduler の耐障害性向上
- `IdleTimeoutScheduler.schedule(...)` で scheduler が停止済み、または登録拒否された場合に no-op `ScheduledFuture` を返すよう変更。
- フォールバック時に警告ログを出力し、失敗させず可観測性を維持。
- `IdleTimeoutSchedulerTest` に shutdown scheduler ケースを追加。

### 検証
- フォーカステスト: `mvn -Dtest=IdleTimeoutSchedulerTest test` 成功
- 全体ビルド: `mvn clean package` 成功
- PR #76 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76): レビュー時の idle-timeout scheduler 停止を許容

---

## 2026-02-19 (v2)

### 概要
- CodeQL ワークフローの Java セットアップを 26 から 25 へ変更し、プロジェクト方針（Java 25.0.2）に統一しました。
- `pom.xml` と main CI ワークフローで先行して揃えていた Java 25 設定に、CodeQL 側も整合させて不一致を解消しました。
- 変更を PR #74 で `main` にマージしました。

### 主な変更

#### PR #74: CodeQL ワークフローの JDK 統一
- `.github/workflows/codeql.yml` の JDK 設定を `26` から `25` に更新。
- ビルド設定と GitHub Actions ワークフローの Java バージョン方針を全体で整合。

### 検証
- ローカル検証: `mvn clean package` 成功
- PR #74 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#74](https://github.com/anishi1222/multi-agent-code-reviewer/pull/74): CodeQL ワークフローの JDK を Java 25 に統一

---

## 2026-02-19

### 概要
- マルチパスレビュー時の性能指摘に対応し、同一エージェント内の複数パスで単一の `CopilotSession` を再利用するよう改善しました。
- 実行モデルを「パス単位」から「エージェント単位（内部で複数パス実行）」へリファクタし、マルチパスマージの挙動を維持しました。
- 単一 reviewer インスタンス再利用を検証する回帰テストを追加し、新しいマルチパス実行契約に合わせてオーケストレータテストを更新しました。
- 変更を PR #67 で `main` にマージしました。

### 主な変更

#### PR #67: マルチパスのセッション再利用とオーケストレータ改善
- `ReviewAgent.reviewPasses(...)` を追加し、同一エージェントの複数パスを1セッションで実行。
- `ReviewExecutionModeRunner` を1エージェント1タスク実行に変更し、タスク内でパス結果を収集。
- `AgentReviewExecutor` を `reviewPasses(...)` ベースに更新し、タイムアウト・失敗時のパス結果整形を実装。
- `ReviewOrchestrator` の実行経路を新しいパス対応フローへ更新。
- テスト追加・更新:
  - `AgentReviewExecutorTest`: マルチパスで reviewer 生成1回・`reviewPasses` 呼び出し1回を検証
  - `ReviewExecutionModeRunnerTest`: async/structured 実行の新しい list ベース契約を検証

### 検証
- PR #67 のCI必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`
- ローカルコンパイル検証: `mvn -q -DskipTests compile` 成功

### マージ済み PR
- [#67](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/67): `CopilotSession` のマルチパス再利用と関連更新

---

## 2026-02-18

### 概要
- 3回目のフルレビューサイクル（best-practices、複数ラウンド）を実施し、PR #41〜#65 で全指摘事項に対応しました。
- Azure WAF エージェント（`azure-waf.agent.md`）を Well-Architected Framework の5つのピラー別エージェントに分割しました。
- report パッケージをサブパッケージに分割し、モジュール性と可視性制御を改善しました。
- StepSecurity による CI セキュリティ強化と GitHub Actions 用 Dependabot 設定を追加しました。
- `SkillService` のスレッドセーフティ問題を `Collections.synchronizedMap` で修正しました。
- テスト数が 722 から 730 以上に増加（nullable・防御的コピーのテスト追加）。

### 主な変更

#### PR #65: WAF エージェントのピラー別分割
- 単一の `azure-waf.agent.md` を5つのピラー別エージェントに分割:
  - `waf-reliability.agent.md`、`waf-security.agent.md`、`waf-cost-optimization.agent.md`、`waf-operational-excellence.agent.md`、`waf-performance-efficiency.agent.md`
- 全 SKILL.md のエージェント参照を対応するピラー別エージェントに更新

#### PR #44: Report パッケージのサブパッケージ分割
- `report/` を `report/finding/`、`report/summary/`、`report/util/` サブパッケージに分割
- 36ファイルでクラスの可視性を最適化

#### PRs #42, #43, #46, #47, #48: ベストプラクティスレビュー対応（ラウンド1〜5）
- `@Factory` + `@Named` PrintStream ビーンによる CLI 出力の統一
- `AgentConfigLoader` に `LinkedHashMap` を使用し安定したエージェント順序を確保
- `OrchestratorConfig` のプロンプトフィールドを `PromptTexts` レコードにグループ化
- `ReviewContext` のフィールドを `TimeoutConfig` と `CachedResources` レコードにグループ化
- `ContentCollector` のキャッシュフィールドに volatile を追加
- 仮想スレッドに名前プレフィックスを追加（可観測性向上）
- `FrontmatterParser` の catch 句を縮小、`loadAs()` で型安全性向上
- `ReviewResult` のタイムスタンプを `LocalDateTime` から `Instant` に変更、テスト容易性のため `Clock` を注入
- 全 `toLowerCase` 呼び出しに `Locale.ROOT` を追加
- `AgentPromptBuilder` と `CustomInstruction` の文字列連結をテキストブロックに変換
- 不要コード削除: 未使用の `CommandExecutor` オーバーロード、`CopilotService` の initialized フラグ、空の `FeatureFlags` コンストラクタ

#### PRs #61, #62: ベストプラクティスフォローアップ対応
- `ReviewCommand.ParsedOptions` にコンパクトコンストラクタ + ビルダーを追加
- `SkillService` の LRU を `LinkedHashMap.removeEldestEntry` でイディオマティックに変更
- `ContentCollector` の汎用 `RuntimeException` を `SessionEventException` に変更
- `SkillResult` のタイムスタンプを `Instant` に移行、`Clock` ベースのオーバーロードを追加
- `SkillConfig.defaults()` ファクトリメソッドを追加
- 全 catch ブロックのログに例外オブジェクトを最終 SLF4J 引数として追加（14箇所）

#### PR #56: SkillService スレッドセーフティ修正
- `SkillService.executorCache` を `ConcurrentHashMap` から `Collections.synchronizedMap` に変更し `computeIfAbsent` デッドロックリスクを回避

#### PR #49: StepSecurity セキュリティ強化
- CI ワークフローの GitHub Actions を SHA ベース参照にピン留め
- GitHub Actions 自動バージョン更新用 `dependabot.yml` を追加

#### PR #60: CODEOWNERS と CI 強化
- CodeQL ワークフローの `oracle-actions/setup-java` をピン留め

#### PR #63: 不足ユニットテスト追加
- nullable・防御的コピー変更に対するユニットテストを12ファイルで追加（+203行）

#### PR #64: README 修正
- README ファイルの重複画像参照を削除

### 検証
- テスト数: 730 以上（116テストクラス、0失敗、0エラー）
- CI: 全PR で全必須チェック合格

### マージ済み PR
- [#41](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/41)〜[#49](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/49)、[#56](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/56)、[#60](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/60)〜[#65](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/65)

---

## 2026-02-17 (v2)

### 概要
- マルチエージェントコードレビュアー自体に対して2回目のフルレビューサイクル（best-practices / code-quality / performance / security）を実施しました。
- 全指摘事項を7つのPR（#34〜#40）で対応。セキュリティ強化、パフォーマンス最適化、コード品質、ベストプラクティス、テストカバレッジを改善しました。
- テスト数が614から722に増加（+108テスト）。

### 主な変更

#### PR #34: 初期レビュー指摘対応（セキュリティ・パフォーマンス・ベストプラクティス・テスト追加）
- `ContentSanitizer` に XSS 防止用 `DANGEROUS_HTML_PATTERN` を追加
- 安全性バリデータにホモグリフ正規化・デリミタインジェクション検出を追加
- `.agent.md` ファイルのプロンプトインジェクション検証を追加
- `ReviewResultMerger` の O(N²) 近似重複検出を改善
- `ContentCollector` のロック競合を軽減
- `CopilotCliException` に `@Serial serialVersionUID` を追加
- `CopilotCliHealthChecker` に `CopilotTimeoutResolver` を DI 注入（DRY）
- 17テストファイルに91件の新規テストを追加

#### PR #35: ベストプラクティスレビュー対応
- `LocalFileConfig` の volatile 遅延初期化を Holder イディオムに変更
- `SkillExecutor` に `AutoCloseable` を実装
- `CustomInstructionLoader` を Micronaut DI 対応
- `ReviewTarget.isLocal()` を exhaustive `switch` 式に変更
- `GithubMcpConfig` に名前付き `MaskedToStringMap` クラスを導入
- `SkillService` を `LinkedHashMap(accessOrder=true)` による LRU キャッシュに変更
- `CliValidationException` に cause-chain コンストラクタを追加
- `ReviewResult.Builder` に `final` を追加

#### PR #36: コード品質レビュー対応
- `AgentMarkdownParser.extractFocusAreas()` のイミュータブルリストバグを修正（High）
- `AggregatedFinding` の record 不変性を復元（`addPass()` → `withPass()`）
- `AgentConfigValidator` のデッドコード `focusAreas() == null` チェックを削除
- `DEFAULT_LOCAL_REVIEW_RESULT_PROMPT` を `AgentPromptBuilder` に集約
- `SummaryCollaborators.withDefaults()` メソッドを追加
- `AgentReviewExecutor` のネスト try-catch を平坦化

#### PR #37: パフォーマンスレビュー対応
- **Critical**: `SkillService` の `computeIfAbsent` デッドロックリスクを修正
- `normalizeText()` の7段 `String.replace()` を単一パス `char[]` 処理に変更
- `ContentSanitizer` の CoT+HTML パターンを統合（3パス→2パス）
- `ContentSanitizationRule` に `find()` 事前チェックを追加
- `ContentCollector` の StringBuilder を 64KB→4KB に削減

#### PR #38: セキュリティレビュー対応
- ソースコードを `<source_code trust_level="untrusted">` デリミタで囲み保護
- `--trust` 使用時の監査ログを追加
- `sensitive-file-patterns.txt` に汎用設定ファイルパターンを追加
- `javascript:` および `data:base64` URI 検出を追加
- `CliPathResolver` に Windows `.exe`/`.cmd` 対応を追加

#### PR #40: テスト整備
- `SkillExecutorTest` を deprecated `shutdown()` → `close()` に更新
- `ContentSanitizerTest` に HTML/XSS サニタイズ7テストを追加
- `LogbackLevelSwitcherTest` を新規作成
- cause-chain コンストラクタ、`generateReports()`、ソースコードデリミタのテストを追加

### 検証
- 全体テスト: 722テスト合格、0失敗、0エラー
- CI: 全PR で全5チェック合格（Build and Test, Native Image, Supply Chain Guard, Dependency Review, Dependency Submission）
- 実行確認: `mvn clean package` + `run --repo ... --all` 終了コード 0

### マージ済み PR
- [#34](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/34)〜[#40](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/40)

---

## 2026-02-17

### 概要
- 最終リメディエーションの分割対応（PR-1〜PR-5）を完了しました。
- セキュリティとDI一貫性の未完了項目を解消し、最終チェックリストを完了状態に更新しました。
- 回帰防止のため、影響範囲テスト・全体テスト・実行確認まで実施しました。

### 主な変更
- PR-1: `GithubMcpConfig.buildMcpServers()` を `Optional<Map<String,Object>>` 化（null契約排除）。
- PR-2: `ReviewRunRequest` から `resolvedToken` を分離し、実行境界で短寿命受け渡しへ変更。
- PR-3: `CustomInstruction` を構造化サンドボックス化し、システム命令優先を明示。
- PR-4: `ContentCollector` に `LongSupplier` 時計注入を追加し、連結キャッシュ判定をバージョン依存へ変更。
- PR-5: `CopilotService` no-arg コンストラクタを削除し、DIコンストラクタへ統一。

### 検証
- 影響テスト: `CopilotServiceTest` / `ReportServiceTest` / `SkillServiceTest` 成功。
- 全体テスト: `mvn -q -DskipITs test` 実行後、Surefire レポートで failures/errors 0 を確認。
- 実行確認: `mvn clean package` と `run --repo ... --all` の終了コード 0 を確認。

### 参照
- 最終チェックリスト: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_checklist_2026-02-16.md`
- 最終サマリー: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_summary_2026-02-17.md`
- RELEASE_NOTES 日英対応ガイド: `reports/anishi1222/multi-agent-code-reviewer/release_notes_bilingual_alignment_2026-02-17.md`
- リリースタグ: `v2026.02.17-notes`
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer-java/releases/tag/v2026.02.17-notes

## 2026-02-16

### 概要
- 大規模クラス分割の継続対応として、CLI、ローカルファイル収集、orchestrator、report パイプライン、Copilot 起動処理の責務分離を完了しました。
- 既存挙動を維持したまま、コマンド/サービスの責務密度を下げ、テスト容易性を向上しました。
- 直近の `CopilotClientStarter` 抽出後も回帰がないことを確認しました。

### 主な変更
- CLI リファクタ:
  - `ReviewCommand` を解析/解決/準備/リクエスト生成/実行調停に分離。
  - `SkillCommand` を解析/準備/実行/出力整形に分離。
  - `--token` の直接値指定を拒否し、stdin/env ベース運用に統一。
- ローカルソース収集リファクタ:
  - `LocalFileProvider` を候補収集・候補処理・内容整形・設定正規化へ分離。
- Orchestrator/Report リファクタ:
  - orchestrator 側に実行モード・結果パイプライン・コンテキスト生成・事前収集の専用コンポーネントを追加。
  - report/summary 内部を parser/formatter/builder 系ヘルパーへ分割。
- Copilot 起動リファクタ:
  - `CopilotService` から CLI パス解決、ヘルスチェック、タイムアウト解決、起動エラーフォーマット、起動実行を分離。
  - `CopilotClientStarter` を追加し、起動時の timeout/cause 変換と安全な close を独立化。

### 検証
- フォーカステスト: 101 run, 0 failures, 0 errors
- 全体テスト: 760 run, 0 failures, 0 errors

## 2026-02-14

### 概要
- Maven と GitHub Actions にサプライチェーンガードレールを導入しました。
- CI の必須チェックを追加し、実PRでブランチ保護の動作を検証しました。
- 依存関係ポリシーと CI ポリシーを英語/日本語 README に明記しました。

### 主な変更
- Maven ポリシー強化:
  - Maven Central 成果物のチェックサム検証を強制。
  - Maven Enforcer により SNAPSHOT 依存関係/プラグインを禁止。
- Dependency Review ポリシー強化:
  - 脆弱性 `moderate` 以上で PR を失敗。
  - 拒否ライセンス: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`。
- CI 強制:
  - `Supply Chain Guard` と `Build and Test` ジョブを追加。
  - ブランチ保護で required checks が正しく認識されることを確認。

### ブランチ保護（main）
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- 追加保護:
  - `strict` status checks 有効
  - `enforce_admins` 有効
  - `required_conversation_resolution` 有効

### マージ済み PR
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### 変更ファイル（PR #10）
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`

## 2026-02-15

### 概要
- 4レビュー（best-practices / code-quality / performance / security）の指摘 26 件（High 4 / Medium 10 / Low 12）に対応しました。
- セキュリティ、保守性、性能、設定/DI 一貫性に関する改善を実施しました。
- PR #13 を squash merge し、`main` へ反映しました。

### 主な変更
- セキュリティ強化:
  - `CustomInstructionSafetyValidator` を regex + Unicode 正規化（NFKC）ベースに強化。
  - 自動ロードされたカスタムインストラクションにも安全性検証を適用。
  - `TemplateService` のテンプレート名検証を allowlist + 正規化パス検証に変更。
  - `GithubMcpConfig` のヘッダ文字列表現で認証値をマスク化。
- コード品質/設計改善:
  - 旧重複コマンド 3 クラスを削除（デッドコード解消）。
  - `FeatureFlags` / `SkillRegistry` を Micronaut DI 管理へ統一。
  - `ReviewCommand.applyOption` を分割し複雑度を低減。
  - wildcard import を明示 import に統一。
- パフォーマンス改善:
  - `LocalFileProvider` に one-pass 収集 API（`collectAndGenerate`）を追加。
  - `ReviewContext` の共有再利用、`FindingsExtractor` の single-pass 化、`SummaryGenerator` 置換処理の軽量化。

### 検証
- `mvn -B -ntp clean package`: BUILD SUCCESS
- テスト結果: 431 run, 0 failures, 0 errors

### マージ済み PR
- [#13](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/13): fix: resolve all findings from 2026-02-15 reports

### 参照
- 詳細対応表: `reports/anishi1222/multi-agent-code-reviewer/remediation_checklist_2026-02-15.md`

## 2026-02-15 (v0.03)

### 概要
- マルチパスレビュー機能を追加し、各エージェントが複数回レビューを実施して結果をマージする仕組みを導入しました。
- 全レビュー指摘（High / Medium / Low）に対する追加修正とテストカバレッジ強化を実施しました。
- CI ワークフローの依存関係修正、設定のデフォルト値追加など安定性を向上しました。

### 主な変更

#### 新機能: マルチパスレビュー
- 各エージェントが `review-passes` 回（デフォルト: 1）レビューを実施し、結果を `ReviewResultMerger` で統合。
- 全パスを Virtual Thread プールに同時投入し、`Semaphore(parallelism)` で同時実行数を制御。
- マージは文字列連結のみ（追加 AI 呼び出しなし）。
- `application.yml` の `reviewer.execution.review-passes` で設定可能。

#### セキュリティ強化
- `ReviewTarget` のパスバリデーション強化。
- `CliPathResolver` に CLI パスの安全性チェックを追加。
- `CustomInstructionSafetyValidator` のプロンプトインジェクション検出を拡充・正規化。
- `GithubMcpConfig` の MCP ヘッダマスキング動作改善。

#### パフォーマンス・安定性
- `ReviewAgent` のリトライ動作とタイムアウトハンドリング改善。
- `ReviewOrchestrator` の起動時クリーンアップとサマリーフォールバック品質向上。
- `ReviewResultMerger` にエージェント単位の重複排除機能を追加。
- `LocalFileProvider` のファイル収集ロジックをリファクタリングし効率化。
- `CopilotService` のリトライとエラーハンドリング改善。

#### 設定・構成
- `AgentPathConfig` を新設し、エージェントパス設定を外部化。
- `LocalFileConfig` にデフォルト値と `@Nullable` アノテーション追加。
- `application.yml` に `reviewer.local-files` のデフォルト設定追加。

#### テスト
- `ReviewResultMergerTest`（9 ケース）を新規追加。
- `AgentPathConfigTest`、`LocalFileConfigTest`、`ReviewTargetTest`、`LocalFileProviderTest`、`CommandExecutorTest`、`CliPathResolverTest` を追加・拡充。
- テスト総数: 453（0 failures, 0 errors）。

#### ドキュメント
- README（日英）にマルチパスレビュー機能の説明、設定例、アーキテクチャ図を追加。

### マージ済み PR
- [#16](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/16): fix: add default reviewer.local-files settings
- [#17](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/17): fix: update build-native-image job dependencies
- [#18](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/18): fix: remediate 2026-02-15 report findings (all severities)
- [#19](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/19): feat: マルチパスレビュー機能の追加
- [#20](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/20): docs: README にマルチパスレビュー機能の説明を追加
- [#21](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/21): fix: all review findings and strengthen coverage

### 検証
- `mvn test`: 453 run, 0 failures, 0 errors
- CI: Build and Test / Native Image Build / Supply Chain Guard / Dependency Review すべて成功
