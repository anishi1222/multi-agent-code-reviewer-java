---
name: waf-security
description: Azure Well-Architected Frameworkのセキュリティの柱に基づきレビューします。マネージドID、Key Vault、ゼロトラスト原則、RBAC最小権限設計を確認します。
metadata:
  agent: azure-waf
---

# セキュリティ（Security）レビュー

以下のリポジトリを Azure Well-Architected Framework の**セキュリティの柱**に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

以下の観点で分析してください：
- マネージドIDの活用（ハードコードされた認証情報の排除）
- ゼロトラストアーキテクチャの原則適用
- ネットワーク分離とプライベートエンドポイントの使用
- 保存時・転送時のデータ暗号化
- Azure Key Vaultによるシークレット管理
- ロールベースアクセス制御（RBAC）の最小権限設計
- 入力バリデーションとサニタイズ
- 監査ログの記録と保持
- セキュアな依存関係管理

発見した問題と推奨対応を報告してください。
