package dev.logicojp.reviewer.agent;

import java.util.List;

/**
 * Configuration model for a review agent.
 * Loaded from YAML files in the agents/ directory.
 */
public class AgentConfig {
    
    private String name;
    private String displayName;
    private String model;
    private String systemPrompt;
    private List<String> focusAreas;
    
    public AgentConfig() {
        // Default constructor for YAML deserialization
    }
    
    public AgentConfig(String name, String displayName, String model, 
                       String systemPrompt, List<String> focusAreas) {
        this.name = name;
        this.displayName = displayName;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.focusAreas = focusAreas;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    public List<String> getFocusAreas() {
        return focusAreas;
    }
    
    public void setFocusAreas(List<String> focusAreas) {
        this.focusAreas = focusAreas;
    }
    
    /**
     * Builds the complete system prompt including output format instructions.
     */
    public String buildFullSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");
        
        sb.append("## レビュー観点\n");
        for (String area : focusAreas) {
            sb.append("- ").append(area).append("\n");
        }
        sb.append("\n");
        
        sb.append("""
            ## 出力フォーマット
            
            レビュー結果は必ず以下の形式で出力してください。複数の指摘がある場合は、それぞれについて以下の形式で記載してください。
            
            ---
            
            ### [指摘番号]. [タイトル]
            
            | 項目 | 内容 |
            |------|------|
            | **Priority** | Critical / High / Medium / Low のいずれか |
            | **指摘の概要** | 何が問題かの簡潔な説明 |
            | **修正しない場合の影響** | 放置した場合のリスクや影響 |
            | **該当箇所** | ファイルパスと行番号（例: `src/main/java/Example.java` L42-50） |
            
            **推奨対応**
            
            具体的な修正方法の説明。可能な場合はコード例を含める：
            
            ```
            // 修正前
            問題のあるコード
            
            // 修正後
            推奨されるコード
            ```
            
            **効果**
            
            この修正による改善効果の説明。
            
            ---
            
            ## Priority の基準
            - **Critical**: セキュリティ脆弱性、データ損失、本番障害につながる問題。即時対応必須
            - **High**: 重大なバグ、パフォーマンス問題、重要な機能の不具合。早急な対応が必要
            - **Medium**: コード品質の問題、保守性の低下、軽微なバグ。計画的に対応
            - **Low**: スタイルの問題、軽微な改善提案。時間があれば対応
            
            指摘がない場合は「指摘事項なし」と記載してください。
            """);
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "AgentConfig{name='" + name + "', displayName='" + displayName + "'}";
    }
}
