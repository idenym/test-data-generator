import { SectionHeading } from "@/components/ui/common"

const techStack = [
  { category: "后端框架", tech: "Spring Boot", version: "2.7.18", reason: "生产成熟，零配置快速启动" },
  { category: "数据持久化", tech: "JPA + H2", version: "—", reason: "内嵌数据库，DDL 自动维护" },
  { category: "SQL 解析", tech: "JSQLParser", version: "4.9", reason: "工业级语法解析，支持复杂 SQL" },
  { category: "AI 调用", tech: "RestTemplate / OkHttp", version: "—", reason: "OpenAI 兼容接口，无厂商锁定" },
  { category: "假数据生成", tech: "DataFaker", version: "1.9.0", reason: "多语言本地化兜底数据" },
  { category: "正则反向生成", tech: "RgxGen", version: "2.0", reason: "正则→字符串反向生成" },
  { category: "JSON 处理", tech: "FastJSON", version: "1.2.83", reason: "高性能 JSON 序列化" },
  { category: "前端框架", tech: "Vue 3", version: "—", reason: "无构建步骤，SPA 直接部署" },
  { category: "并发处理", tech: "CompletableFuture", version: "—", reason: "线程池 max(8, CPU×2)" },
]

export function TechStackSection() {
  return (
    <section className="py-24 px-4 relative">
      <div className="absolute inset-0 bg-gradient-to-b from-background via-card/30 to-background" />
      <div className="container max-w-5xl relative z-10">
        <SectionHeading
          overline="Technology"
          title="技术选型"
          description="分层清晰的技术栈选择，每层选型有明确理由。"
        />

        <div className="glass-card rounded-2xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="px-6 py-4 font-semibold">层次</th>
                <th className="px-6 py-4 font-semibold">技术 / 库</th>
                <th className="px-6 py-4 font-semibold hidden md:table-cell">版本</th>
                <th className="px-6 py-4 font-semibold">选型理由</th>
              </tr>
            </thead>
            <tbody>
              {techStack.map((t, i) => (
                <tr key={i} className="border-b border-border/20 last:border-0 hover:bg-primary/5 transition-colors">
                  <td className="px-6 py-4 font-medium">{t.category}</td>
                  <td className="px-6 py-4 font-mono text-primary">{t.tech}</td>
                  <td className="px-6 py-4 text-muted-foreground hidden md:table-cell">{t.version}</td>
                  <td className="px-6 py-4 text-muted-foreground">{t.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
