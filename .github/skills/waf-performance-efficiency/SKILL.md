---
name: waf-performance-efficiency
description: Azure Well-Architected Frameworkのパフォーマンス効率の柱に基づきレビューします。キャッシュ戦略、非同期メッセージング、コネクションプーリング、クエリ最適化を確認します。
metadata:
  agent: azure-waf
---

# パフォーマンス効率（Performance Efficiency）レビュー

以下のリポジトリを Azure Well-Architected Framework の**パフォーマンス効率の柱**に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

以下の観点で分析してください：
- 適切なキャッシュ戦略（Azure Cache for Redis等）
- CDNの活用によるレイテンシ削減
- 非同期メッセージング（Service Bus、Event Grid）の活用
- データベースの適切なパーティショニング
- コネクションプーリングの実装
- 自動スケーリングの応答性
- コンテンツ圧縮の実装
- クエリ最適化とインデックス設計
- マイクロサービス間通信の効率化
- 負荷テストとパフォーマンスベンチマークの実施

発見した問題と推奨対応を報告してください。
