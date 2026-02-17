# 最終対応チェックリスト（2026-02-16）

対象: `best-practices_2026-02-16.md` / `code-quality_2026-02-16.md` / `performance_2026-02-16.md` / `security_2026-02-16.md`

要約: `final_remediation_summary_2026-02-17.md`

ドキュメント同期チェックリスト: `documentation_sync_checklist_2026-02-17.md`

## 1) 今回までで対応済み（主要）

- [x] `ReviewCustomInstructionResolver.addIfSafe` のフィルタ結果無視バグ修正（Critical）
- [x] レポートリンクと実ファイル名のサニタイズ不一致修正
- [x] トークン保持Record群の `toString()` マスキング導入（複数クラス）
- [x] `CliParsing.readToken` stdin経路の `byte[]` ゼロクリア
- [x] `ReviewService` のデフォルトセキュア化（暗黙ロード抑制）
- [x] `ReviewContext` の冗長フィールド削減（`LocalFileConfig`へ一元化）
- [x] `ReviewOrchestrator` コラボレータ構成改善（注入可能性向上）
- [x] `OrchestratorConfig` の防御的コピー/バリデーション追加
- [x] `ReviewResultMerger` 重複検出の責務分離・`NormalizedFinding` 導入
- [x] 近似重複検出の候補絞り込み（優先度インデックス等）
- [x] ローカルレビューの巨大結合プロンプトを分割送信へ変更（メモリ圧縮）
- [x] `LocalFileCandidateProcessor` の TOCTOU 再検証強化
- [x] `LocalFileCandidateCollector` の不要文字列/Path生成削減
- [x] `SummaryConfig` 新設と `application.yml` 外部設定化
- [x] `CustomInstructionSafetyValidator` の疑わしいパターン外部化
- [x] `LocalFileConfig` 既定リストのリソース外部化
- [x] `LocalFileContentFormatter.detectLanguage` を宣言的Map化
- [x] `LifecycleRunner` 導入で実行ライフサイクル重複排除
- [x] `FeatureFlags` 冗長アクセサ整理
- [x] `ModelConfig` / `GithubMcpConfig` の default解決パターン統一
- [x] `GithubMcpConfig.applyAuthHeader` の到達不能ガード削除
- [x] `ReviewOrchestratorFactory` の過度な `RuntimeException` 捕捉を縮小
- [x] `ReviewRetryExecutor` にリトライ定数責務を集約
- [x] `AgentConfigLoader` builder導入
- [x] `ReviewAgent` のコラボレータ注入可能化

## 2) 今回追加で対応した項目（最終詰め）

- [x] `AgentMarkdownParser` の不変リスト返却（防御的コピー徹底）
- [x] `AgentMarkdownParser.extractSections` の行処理を `String.lines()` に統一
- [x] `CliParsing.splitComma` を不変リスト返却に変更
- [x] `CliValidationException` に `@Serial` + `serialVersionUID` 追加
- [x] `ModelConfig.Builder` を `final` 化
- [x] `ContentCollector` の `accumulatedSize` をロック戦略に合わせて plain `int` 化

## 3) テスト強化（追加）

- [x] `AgentServiceTest`
- [x] `ReportServiceTest`
- [x] `FindingsParserTest`
- [x] `ListAgentsCommandTest`
- [x] `ReviewAppTest`
- [x] `CopilotCliHealthCheckerTest`

検証結果: 全体テスト `796 passed / 0 failed`

## 4) 旧保留項目の完了（2026-02-17更新）

以下の保留項目は PR-3〜PR-5 で完了済み。

- [x] プロンプトインジェクション防御の「構造化サンドボックス化」導入（PR-3）
- [x] `ContentCollector` の Clock注入・キャッシュ戦略再設計（PR-4）
- [x] `CopilotService` no-arg コンストラクタ削除（PR-5）

## 5) 追加実施推奨（次サイクル）

- 現時点で未着手の推奨項目はありません（PR-5 完了）。

## 6) PR分割と進捗（2026-02-17更新）

- [x] **PR-1**: `GithubMcpConfig.buildMcpServers()` を `Optional<Map<String,Object>>` 化
  - 変更: `GithubMcpConfig` / `ReviewOrchestrator` / `SkillExecutor` / `GithubMcpConfigTest`
  - 備考: `runTests` の差分コンパイル表示が不安定だったため、`mvn -q test -DskipITs` でビルド成功（exit code 0）を確認
- [x] **PR-2**: `ReviewRunRequest` から `resolvedToken` を分離（トークン寿命最小化）
  - 変更: `ReviewRunExecutor` / `ReviewExecutionCoordinator` / `ReviewRunRequestFactory` / `ReviewCommand` / 関連CLIテスト
  - 備考: 実行シグネチャを `execute(resolvedToken, runRequest)` に変更し、トークン保持を短寿命引数へ限定
- [x] **PR-3**: Prompt sandbox化（構造化インストラクション境界 + 互換性評価）
  - 変更: `CustomInstruction.toPromptSection()` に `<user_provided_instruction ...>` 境界と優先ルール文を導入
  - 備考: 既存の `ReviewSystemPromptFormatter` ブロックガードと二重化し、システム命令優先を明示
- [x] **PR-4**: `ContentCollector` の `Clock` 注入とキャッシュ戦略見直し
  - 変更: `ContentCollector` に `LongSupplier` 注入コンストラクタを追加（既存コンストラクタ互換維持）
  - 備考: 連結キャッシュ判定をサイズ依存から更新バージョン依存へ変更し、テスト追加で回帰防止
- [x] **PR-5**: `CopilotService` no-arg コンストラクタ削除（DI純化）

## 7) リリース運用完了（2026-02-17更新）

- [x] リリースタグ作成: `v2026.02.17-notes`
- [x] GitHub Release 公開: https://github.com/anishi1222/multi-agent-code-reviewer-java/releases/tag/v2026.02.17-notes
- [x] README（EN/JA）にRelease URLを追記（PR #27 で反映済み）

## 8) 2026-02-17 追加レビューサイクル対応

2026-02-17に再実施したレビュー（best-practices/code-quality/performance/security）の全指摘事項を対応。

### PR #34: 初期対応（セキュリティ・パフォーマンス・ベストプラクティス・テスト追加）

- [x] `CopilotCliException` に `@Serial serialVersionUID` 追加
- [x] `CopilotCliHealthChecker` に `CopilotTimeoutResolver` DI注入（DRY）
- [x] `CliUsage` の `GENERAL_USAGE` 定数抽出（DRY）
- [x] `EventSubscriptions.closeAll()` で `List.of()` 使用
- [x] `GithubMcpConfig` プレースホルダー形式 `{token}` のみに統一
- [x] `AgentConfig` に `@Nullable` アノテーション追加
- [x] `ReviewCustomInstructionResolver` 例外キャッチ具体化
- [x] `ReviewResultPipeline.collectFromFutures()` null結果スキップ
- [x] `AgentReviewExecutor` の `failedResult()` ヘルパー抽出（DRY）
- [x] `ContentSanitizer` に `DANGEROUS_HTML_PATTERN` 追加（XSS防止）
- [x] `CustomInstruction` XMLタグクローズサンドボックス脱出防止
- [x] `CustomInstructionSafetyValidator` ホモグリフ正規化追加
- [x] `CustomInstructionSafetyValidator` デリミタインジェクションパターン追加
- [x] `AgentConfigLoader` エージェント定義ファイルのプロンプトインジェクション検証
- [x] `ReviewOrchestrator` ExecutorService リソースリーク防止（try-catch）
- [x] `SkillService` エグゼキュータキャッシュサイズ上限追加
- [x] `ReviewResultMerger` O(N²)改善（blank-priority インデックス）
- [x] `ContentCollector` StringBuilder 初期容量最適化
- [x] `ContentCollector` ロック競合軽減
- [x] `SummaryPromptBuilder` テンプレート事前ロード
- [x] `suspicious-patterns.txt` 追加パターン
- [x] テスト17ファイル新規作成（91テスト追加）

### PR #35: ベストプラクティスレビュー対応

- [x] `LocalFileConfig`: volatile → `DefaultsHolder` イディオム
- [x] `SkillExecutor`: `AutoCloseable` 実装
- [x] `CustomInstructionLoader`: `PromptLoader`/`ScopedInstructionLoader` DI対応
- [x] `ReviewTarget.isLocal()`: exhaustive `switch` 式
- [x] `GithubMcpConfig`: 名前付き `MaskedToStringMap` クラス
- [x] `SkillService`: `LinkedHashMap(accessOrder=true)` LRU
- [x] `FeatureFlags`: コンパクトコンストラクタ追加
- [x] `ConfigDefaults`: InputStream try-with-resources
- [x] `CliValidationException`: cause chain コンストラクタ追加
- [x] `ReviewResult.Builder`: `final` 修飾子追加
- [x] `ReportGenerator`: for-each + 失敗追跡ログ
- [x] `SummaryGenerator`: パラメータ再代入 → `effective` ローカル変数

### PR #36: コード品質レビュー対応

- [x] `AgentMarkdownParser.extractFocusAreas()`: イミュータブルリストバグ修正
- [x] `AggregatedFinding`: `addPass()` → `withPass()` record不変性復元
- [x] `ReviewResultMerger`: `withPass()` + `put()` パターン更新
- [x] `AgentConfigValidator`: デッドコード `focusAreas() == null` 削除
- [x] `AgentPromptBuilder`: `DEFAULT_LOCAL_REVIEW_RESULT_PROMPT` 定数集約
- [x] `SummaryCollaborators.withDefaults()` メソッド追加
- [x] `ReviewMessageFlow`: 戻り値破棄ドキュメント追加
- [x] `AgentReviewExecutor`: ネスト try-catch → `executeWithTimeout()` 平坦化

### PR #37: パフォーマンスレビュー対応

- [x] **Critical**: `SkillService` `computeIfAbsent` デッドロック排除（get/put パターン）
- [x] `ReviewFindingSimilarity.normalizeText()`: 単一パス `char[]` 処理
- [x] `ContentSanitizer`: CoT+HTML パターン統合 + `find()` 事前チェック
- [x] `bigrams()`: `HashSet.newHashSet()` 初期容量最適化
- [x] `ContentCollector`: StringBuilder 64KB→4KB
- [x] `ReviewFindingParser.findingKeyFromNormalized()` 追加

### PR #38: セキュリティレビュー対応

- [x] `AgentPromptBuilder`: `<source_code trust_level="untrusted">` デリミタ追加
- [x] `ReviewCustomInstructionResolver`: `--trust` 監査ログ追加
- [x] `sensitive-file-patterns.txt`: 汎用設定ファイルパターン追加
- [x] `ContentSanitizer`: `javascript:` / `data:*;base64` URI 検出追加
- [x] `CliPathResolver`: Windows `.exe`/`.cmd` 拡張子対応

### 検証結果

- 全体テスト: 705テスト合格 / 0失敗
- CI: 全PR で全5チェック合格（Build and Test, Native Image, Supply Chain Guard, Dependency Review, Dependency Submission）
