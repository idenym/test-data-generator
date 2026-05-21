import { SectionHeading, Badge } from "@/components/ui/common"
import { BrainCircuit, ArrowRight, Bot, Shield, AlertTriangle } from "lucide-react"

export function AIFlowSection() {
  return (
    <section className="py-24 px-4">
      <div className="container max-w-6xl">
        <SectionHeading
          overline="AI & LLM Integration"
          title="AI 大模型交互设计"
          description="两大调用场景、Prompt 工程、多模型随机分配、Extended Thinking、四层容错降级。"
        />

        {/* Two scenarios */}
        <div className="grid gap-8 md:grid-cols-2 mb-16">
          <div className="glass-card rounded-2xl p-6 glow-border">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-primary/15 border border-primary/30 flex items-center justify-center">
                <Bot className="w-5 h-5 text-primary" />
              </div>
              <div>
                <h3 className="font-bold">场景 A：批量字段值生成</h3>
                <p className="text-xs text-muted-foreground">核心高频调用</p>
              </div>
              <Badge variant="primary" className="ml-auto">async × N</Badge>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              预览开始时，所有 LLM 字段并发预填充。每次 API 请求返回 50 条值（可配置），
              存入线程安全值池（CopyOnWriteArrayList），逐行生成时循环取用。
            </p>
            <div className="space-y-2 text-xs font-mono">
              <div className="bg-muted/30 rounded-lg p-3 border border-border/40">
                <span className="text-primary/70">System: </span>
                <span className="text-muted-foreground">"你是一个严谨的数据库测试数据生成专家..."</span>
              </div>
              <div className="bg-muted/30 rounded-lg p-3 border border-border/40">
                <span className="text-accent/70">User: </span>
                <span className="text-muted-foreground">"请根据以下字段定义生成50条realistic且diverse的测试数据..."</span>
              </div>
            </div>
          </div>

          <div className="glass-card rounded-2xl p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-accent/15 border border-accent/30 flex items-center justify-center">
                <BrainCircuit className="w-5 h-5 text-accent" />
              </div>
              <div>
                <h3 className="font-bold">场景 B：表级规则推荐</h3>
                <p className="text-xs text-muted-foreground">低频辅助调用</p>
              </div>
              <Badge variant="accent" className="ml-auto">on demand</Badge>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              用户在规则配置页点击"AI 建议规则"，平台将整张表的所有字段元信息打包发送给大模型，
              由其分析每列最佳策略，返回结构化 JSON 直接回填到表单。
            </p>
            <div className="bg-muted/30 rounded-lg p-3 border border-border/40 text-xs font-mono text-muted-foreground">
              <span className="text-accent/70">Prompt: </span>
              "Analyze this table... Columns: id(bigint) [PK], name(varchar) -- 企业名称,
              status(varchar) -- 状态... Return JSON: [{'\{}'}columnName, ruleType, ruleConfig{'\}'}']"
            </div>
          </div>
        </div>

        {/* Full pipeline */}
        <div className="glass-card rounded-2xl p-8 mb-16">
          <h3 className="font-bold text-xl mb-8 text-center">LLM 调用全链路</h3>
          <div className="flex flex-col items-center gap-0">
            {[
              { label: "用户点击「预览」", icon: "🖱️" },
              { label: "DataGenerationPipeline.preview()", icon: "⚙️" },
              { label: "preFillLlmGenerators() — CompletableFuture × N 并发", icon: "⚡" },
              { label: "LlmService.generateBatchValues()", icon: "🧠" },
              { label: "buildGenerationPrompt() — 构造含约束的结构化 Prompt", icon: "📋" },
              { label: "pickModel(models) — ThreadLocalRandom 随机选择", icon: "🎲" },
              { label: "POST /v1/chat/completions", icon: "🌐" },
              { label: "parseValuesFromResponse() — 清理 Markdown，解析 JSON", icon: "🔍" },
              { label: "LlmBatchGenerator.addValues() → valuePool (线程安全)", icon: "📦" },
              { label: "generateRows() 逐行 generator.generate(context)", icon: "✅" },
            ].map((step, i) => (
              <div key={i} className="flex flex-col items-center w-full max-w-md">
                <div className="w-full glass-card rounded-xl p-4 text-center hover:border-primary/30 transition-all duration-200">
                  <span className="text-lg mr-3">{step.icon}</span>
                  <span className="text-sm font-medium">{step.label}</span>
                </div>
                {i < 9 && (
                  <div className="h-8 flex items-center justify-center">
                    <ArrowRight className="w-4 h-4 text-primary/40" />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Multi-model & fault tolerance */}
        <div className="grid gap-6 md:grid-cols-3 mb-12">
          <div className="glass-card rounded-xl p-6 text-center">
            <div className="text-3xl mb-3">🎲</div>
            <h4 className="font-bold mb-2">多模型随机分配</h4>
            <p className="text-sm text-muted-foreground leading-relaxed">
              用户可为同一任务选择多个模型，不同字段随机使用不同模型（DeepSeek / Mimo），提升数据多样性。
            </p>
          </div>
          <div className="glass-card rounded-xl p-6 text-center">
            <div className="text-3xl mb-3">💡</div>
            <h4 className="font-bold mb-2">Extended Thinking</h4>
            <p className="text-sm text-muted-foreground leading-relaxed">
              DeepSeek: {"thinking: {type: 'enabled'}"}
              <br />
              Mimo: {"thinking: true"}
              <br />
              开启后先内部推理再给答案，复杂约束场景更优。
            </p>
          </div>
          <div className="glass-card rounded-xl p-6 text-center">
            <div className="text-3xl mb-3">🛡️</div>
            <h4 className="font-bold mb-2">四层容错降级</h4>
            <p className="text-sm text-muted-foreground leading-relaxed">
              重试 3 次（指数退避）→ 返回空池 → DefaultGenerator 兜底 → 并发回退串行。
            </p>
          </div>
        </div>

        {/* Fault tolerance table */}
        <div className="glass-card rounded-xl overflow-hidden">
          <div className="flex items-center gap-2 px-6 py-4 border-b border-border">
            <Shield className="w-4 h-4 text-primary" />
            <span className="font-semibold text-sm">容错与降级机制</span>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border/50 text-left">
                <th className="px-6 py-3 font-medium text-muted-foreground">异常场景</th>
                <th className="px-6 py-3 font-medium text-muted-foreground">处理策略</th>
              </tr>
            </thead>
            <tbody>
              {[
                ["API 调用失败", "自动重试，最多 3 次，指数退避（1s → 2s → 4s）"],
                ["全部重试失败", "返回 \"[]\"，LlmBatchGenerator 值池为空"],
                ["值池为空时生成", "回退到 DefaultGenerator 按数据类型兜底生成"],
                ["并发预填异常", "回退到串行逐列预填模式"],
                ["响应含 Markdown", "自动去除 ```json 和 ``` 代码块标记"],
              ].map((row, i) => (
                <tr key={i} className="border-b border-border/20 last:border-0">
                  <td className="px-6 py-3 flex items-center gap-2">
                    <AlertTriangle className="w-3.5 h-3.5 text-warning flex-shrink-0" />
                    {row[0]}
                  </td>
                  <td className="px-6 py-3 text-muted-foreground">{row[1]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
