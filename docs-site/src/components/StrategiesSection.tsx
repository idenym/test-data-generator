import { SectionHeading, Card, Badge } from "@/components/ui/common"
import { Sparkles, Braces, Minus, List } from "lucide-react"

const strategies = [
  {
    icon: Sparkles,
    title: "AI 语义生成",
    type: "LLM_DESCRIPTION",
    config: "语义描述文本",
    desc: "大模型根据字段语义描述批量生成高质量真实感数据，如姓名、地址、公司名、订单备注等语义丰富字段。",
    examples: ["企业名称", "用户备注", "商品描述", "地址信息"],
    color: "text-primary",
    bgColor: "bg-primary/10 border-primary/20",
  },
  {
    icon: Braces,
    title: "正则表达式",
    type: "REGEX",
    config: "正则 pattern",
    desc: "基于 RgxGen 引擎将正则表达式反向生成匹配字符串，精确控制输出格式。",
    examples: ["身份证号", "手机号", "统一社会信用代码", "业务编号"],
    color: "text-accent",
    bgColor: "bg-accent/10 border-accent/20",
  },
  {
    icon: Minus,
    title: "范围值",
    type: "RANGE",
    config: "min / max / type",
    desc: "在指定区间内均匀随机生成整数、小数、日期或 datetime 值。",
    examples: ["金额范围", "年龄区间", "日期范围", "积分等级"],
    color: "text-warning",
    bgColor: "bg-warning/10 border-warning/20",
  },
  {
    icon: List,
    title: "枚举值",
    type: "ENUM",
    config: "候选值列表 + 权重",
    desc: "从候选值列表中按权重随机抽取，支持自定义概率分布。",
    examples: ["状态码", "性别", "省份", "业务类型"],
    color: "text-muted-foreground",
    bgColor: "bg-border/30 border-border",
  },
]

export function StrategiesSection() {
  return (
    <section className="py-24 px-4 relative">
      <div className="absolute inset-0 bg-gradient-to-b from-background via-card/40 to-background" />
      <div className="container max-w-6xl relative z-10">
        <SectionHeading
          overline="Generation Strategies"
          title="四种生成策略"
          description="覆盖从格式规定到语义理解的完整数据生成场景，灵活组合满足各类字段需求。"
        />

        <div className="grid gap-6 md:grid-cols-2">
          {strategies.map((s, i) => (
            <Card key={i} glow={s.title === "AI 语义生成"}>
              <div className="flex items-start gap-4 mb-4">
                <div className={`w-12 h-12 rounded-xl ${s.bgColor} border flex items-center justify-center flex-shrink-0`}>
                  <s.icon className={`w-6 h-6 ${s.color}`} />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="text-xl font-bold">{s.title}</h3>
                    <Badge variant="muted">{s.type}</Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">配置项：{s.config}</p>
                </div>
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed mb-4">{s.desc}</p>
              <div className="flex flex-wrap gap-2">
                {s.examples.map((ex) => (
                  <span key={ex} className="px-2.5 py-1 rounded-lg bg-muted/50 text-xs text-muted-foreground border border-border/40">
                    {ex}
                  </span>
                ))}
              </div>
            </Card>
          ))}
        </div>

        {/* Auto-skip note */}
        <div className="mt-8 flex items-center justify-center gap-2 text-sm text-muted-foreground">
          <span className="w-2 h-2 rounded-full bg-muted-foreground/40" />
          自增列 &amp; 外键引用列由引擎自动处理，无需用户配置
        </div>
      </div>
    </section>
  )
}
