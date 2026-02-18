---
name: waf-reliability
description: Azure Well-Architected Frameworkの信頼性の柱に基づきレビューします。リトライ/指数バックオフ、サーキットブレーカー、タイムアウト設定、障害復旧戦略を確認します。
metadata:
  agent: waf-reliability
---

# 信頼性（Reliability）レビュー

以下のリポジトリを Azure Well-Architected Framework の**信頼性の柱**に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

以下の観点で分析してください：
- リトライロジックと指数バックオフの実装状況
- サーキットブレーカーパターンの適用有無
- 障害分離（Bulkhead）パターンの適用有無
- タイムアウトの適切な設定
- ヘルスチェックエンドポイントの実装
- グレースフルデグラデーションの実装
- 一時的な障害への対応（Transient Fault Handling）
- データの整合性とバックアップ戦略
- 障害復旧（Disaster Recovery）の考慮

発見した問題と推奨対応を報告してください。
