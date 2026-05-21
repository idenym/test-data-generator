import { SectionHeading } from "@/components/ui/common"
import { Layers, Brain, Cpu, Database, Globe, Box } from "lucide-react"

const layers = [
  { label: "Browser", desc: "Vue 3 SPA · 五步向导", color: "border-blue-400/50 bg-blue-400/10", icon: Globe },
  { label: "Controller", desc: "7 个 REST API", color: "border-cyan-400/50 bg-cyan-400/10", icon: Box },
  { label: "Service", desc: "LlmService · SqlParserService · DataGeneratorService", color: "border-teal-400/50 bg-teal-400/10", icon: Brain },
  { label: "Engine", desc: "Pipeline · RuleMatching · FieldGenerator × 7", color: "border-emerald-400/50 bg-emerald-400/10", icon: Cpu },
  { label: "Repository", desc: "JPA / H2 内嵌数据库", color: "border-green-400/50 bg-green-400/10", icon: Database },
  { label: "External", desc: "目标 MySQL · AI 大模型服务", color: "border-primary/50 bg-primary/10", icon: Layers },
]

export function ArchitectureSection() {
  return (
    <section className="py-24 px-4 relative overflow-hidden">
      <div className="absolute inset-0 bg-card/50" />
      <div className="container max-w-6xl relative z-10">
        <SectionHeading
          overline="System Architecture"
          title="技术架构"
          description="分层清晰、插件化设计，Controller → Service → Engine → Repository，每层职责明确。"
        />

        {/* Architecture stack visualization */}
        <div className="max-w-3xl mx-auto space-y-3 mb-20">
          {layers.map((layer, i) => (
            <div
              key={i}
              className="animate-fade-in"
              style={{ animationDelay: `${i * 120}ms` }}
            >
              <div className={`glass-card rounded-xl p-5 border-l-4 ${layer.color} flex items-center gap-5 group hover:scale-[1.02] transition-all duration-300`}>
                <div className={`w-12 h-12 rounded-xl ${layer.color} border flex items-center justify-center flex-shrink-0`}>
                  <layer.icon className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-lg font-bold">{layer.label}</div>
                  <div className="text-sm text-muted-foreground">{layer.desc}</div>
                </div>
              </div>
              {i < layers.length - 1 && (
                <div className="flex justify-center py-1">
                  <div className="w-0.5 h-6 bg-gradient-to-b from-border to-border/20" />
                </div>
              )}
            </div>
          ))}
        </div>

        {/* FieldGenerator grid */}
        <SectionHeading
          overline="Core Engine"
          title="FieldGenerator 体系"
          description="7 种生成器实现，可插拔扩展，5 级优先级自动匹配。"
        />
        <div className="grid gap-4 md:grid-cols-4 max-w-5xl mx-auto">
          {[
            { name: "LlmBatchGenerator", deps: "LlmService", note: "预填值池 · 循环取用" },
            { name: "RegexGenerator", deps: "RgxGen 2.0", note: "正则反向生成字符串" },
            { name: "RangeGenerator", deps: "—", note: "区间内均匀随机" },
            { name: "EnumGenerator", deps: "—", note: "按权重抽样" },
            { name: "ForeignKeyGenerator", deps: "—", note: "从父表 PK 池随机引用" },
            { name: "SequenceGenerator", deps: "—", note: "自增序列" },
            { name: "DefaultGenerator", deps: "DataFaker", note: "数据类型兜底", span: "md:col-span-1" },
          ].map((g, i) => (
            <div
              key={i}
              className={`glass-card rounded-xl p-4 text-center hover:-translate-y-1 transition-all duration-200 ${
                (g as any).span === "md:col-span-1" ? "md:col-start-2" : ""
              }`}
            >
              <div className="font-mono text-sm font-bold text-primary mb-1">{g.name}</div>
              <div className="text-xs text-muted-foreground mb-1.5">依赖: {g.deps}</div>
              <div className="text-xs text-accent">{g.note}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
