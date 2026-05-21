import { SectionHeadingLeft } from "@/components/ui/common"
import { Search, Lightbulb, RefreshCw, GitBranch, Camera } from "lucide-react"

const highlights = [
  {
    icon: Search,
    number: "01",
    title: "SQL 约束感知 —— \"SQL 即规则\"",
    desc: "不只解析表结构，更深度分析 SQL 中的 WHERE 和 JOIN ON 条件：等值约束自动映射为枚举规则，范围比较映射为区间规则，同一字段的多条件自动取交集合并。",
    items: [
      "status = 'ACTIVE' → ENUM ['ACTIVE']",
      "age >= 18 AND age <= 60 → RANGE [18, 60]",
      "date BETWEEN '2024-01-01' AND '2024-12-31' → 日期范围",
    ],
  },
  {
    icon: Lightbulb,
    number: "02",
    title: "大模型驱动智能规则推荐",
    desc: "将表结构（字段名、类型、注释、PK/FK/自增标记）发送给大模型，由模型分析每列最佳策略，返回结构化 JSON 直接回填到配置表单。",
    items: [
      "unified_code → REGEX: [0-9A-Z]{18}",
      "company_name → LLM_DESCRIPTION: 中国境内真实企业名称",
      "business_status → ENUM: [正常, 注销, 吊销, 迁出]",
    ],
  },
  {
    icon: RefreshCw,
    number: "03",
    title: "列级无损重新生成",
    desc: "修改单列规则后，仅对目标列重新调用生成器，其他列数据保持不变。FK 引用列从已有父表数据中匹配，保证引用完整性。",
    items: [
      "过滤自增列，跳过不重生成",
      "外键从 existingData 提取父表 PK 值注入",
      "上下文感知：context = {rowIndex, currentRow}",
    ],
  },
  {
    icon: GitBranch,
    number: "04",
    title: "多表外键依赖全自动处理",
    desc: "同时处理 FK 依赖的发现（SQL JOIN + 数据库元数据）、拓扑排序（Kahn 算法）、同层并发生成、主键追踪、数据库兜底查询五大环节。",
    items: [
      "关系发现 → 拓扑排序 → 并行生成 → FK 追踪 → DB 兜底",
      "同一事务提交，任意表失败全回滚",
      "缺失父表数据时自动查询已有值",
    ],
  },
  {
    icon: Camera,
    number: "05",
    title: "历史任务全量快照",
    desc: "每次执行自动保存规则快照、SQL 分析快照和数据快照（每表最多 200 行），任意时刻可完整回溯复现。",
    items: [
      "rules_snapshot: 所有字段规则配置 JSON",
      "analysis_snapshot: 表结构 + FK + 生成顺序",
      "generated_data_snapshot: 每表 ≤200 行数据 (MEDIUMTEXT)",
    ],
  },
]

export function HighlightsSection() {
  return (
    <section className="py-24 px-4">
      <div className="container max-w-6xl">
        <SectionHeadingLeft
          overline="Innovation Highlights"
          title="五大创新亮点"
          description="深度解决测试数据生成领域的核心难题：真实感 vs 约束合规、灵活性 vs 一致性、效率 vs 易用性。"
        />

        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {highlights.map((h, i) => (
            <div
              key={i}
              className={`glass-card rounded-2xl p-6 flex flex-col group hover:-translate-y-1 transition-all duration-300 ${
                i === 0 ? "lg:col-span-2" : ""
              }`}
            >
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl bg-primary/10 border border-primary/20 flex items-center justify-center">
                  <h.icon className="w-5 h-5 text-primary" />
                </div>
                <span className="text-xs font-bold text-primary/60">亮点 {h.number}</span>
              </div>
              <h3 className="text-lg font-bold mb-2">{h.title}</h3>
              <p className="text-sm text-muted-foreground leading-relaxed mb-4">{h.desc}</p>
              <div className="mt-auto space-y-2">
                {h.items.map((item, j) => (
                  <div key={j} className="flex items-start gap-2 text-xs">
                    <span className="text-primary mt-0.5 flex-shrink-0">▸</span>
                    <code className="font-mono text-muted-foreground bg-muted/50 px-2 py-0.5 rounded-md">{item}</code>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
