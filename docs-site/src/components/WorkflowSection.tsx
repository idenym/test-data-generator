import { SectionHeading } from "@/components/ui/common"
import { Database, FileCode, Sliders, Eye, Rocket } from "lucide-react"

const steps = [
  {
    icon: Database,
    title: "配置连接",
    desc: "管理多套目标数据库连接，支持开发/测试/预发环境独立配置，连通性一键测试，凭据 AES 加密存储。",
    color: "bg-primary/10 text-primary border-primary/20",
  },
  {
    icon: FileCode,
    title: "输入 SQL",
    desc: "粘贴业务 SQL 或上传文件，自动解析表结构、字段元信息、外键依赖、WHERE 约束，可视化展示 ER 关系图。",
    color: "bg-accent/10 text-accent border-accent/20",
  },
  {
    icon: Sliders,
    title: "配置规则",
    desc: "为每个字段选择 AI 语义生成、正则、范围或枚举策略。自动回填历史规则，一键触发大模型智能推荐。",
    color: "bg-warning/10 text-warning border-warning/20",
  },
  {
    icon: Eye,
    title: "预览数据",
    desc: "实时预览生成效果，支持单元格双击编辑、列级无损重新生成、Diff 对比，多模型随机分配提升多样性。",
    color: "bg-primary/10 text-primary border-primary/20",
  },
  {
    icon: Rocket,
    title: "写入数据库",
    desc: "一键事务写入目标数据库，全表原子提交保证一致性。自动保存规则/分析/数据三类快照供任意时刻回溯。",
    color: "bg-accent/10 text-accent border-accent/20",
  },
]

export function WorkflowSection() {
  return (
    <section className="py-24 px-4">
      <div className="container max-w-6xl">
        <SectionHeading
          overline="Five-Step Wizard"
          title="五步向导，零编码造数"
          description="从 SQL 粘贴到数据落库，全程可视化操作，无需编写任何生成脚本。"
        />

        <div className="grid gap-6 md:grid-cols-5">
          {steps.map((step, i) => (
            <div key={i} className="relative">
              <div className="glass-card rounded-2xl p-6 h-full flex flex-col items-center text-center group hover:-translate-y-2 transition-all duration-300">
                <div className={`w-14 h-14 rounded-xl ${step.color} border flex items-center justify-center mb-5 shadow-md`}>
                  <step.icon className="w-6 h-6" />
                </div>
                <div className="text-xs font-bold text-muted-foreground mb-2">
                  STEP 0{i + 1}
                </div>
                <h3 className="font-bold text-lg mb-3">{step.title}</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">{step.desc}</p>
              </div>
              {i < 4 && (
                <div className="hidden md:block absolute top-1/2 -right-4 w-8 h-[2px] bg-gradient-to-r from-primary/20 to-transparent" />
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
