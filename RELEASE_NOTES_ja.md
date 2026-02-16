# リリースノート

## 更新ルール（テンプレート）

1. EN/JA 両方に同一構成で新しい日付セクションを追加する。
2. 注釈付きタグ（例: `vYYYY.MM.DD-notes`）を作成して push する。
3. タグから GitHub Release を作成し、EN/JA 要約を本文に含める。
4. `README_en.md` と `README_ja.md` にリリース参照とURLを追記する。

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
