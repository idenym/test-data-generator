import { Badge } from "@/components/ui/common"

export function HeroSection() {
  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden">
      {/* Background decoration */}
      <div className="absolute inset-0">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[600px] bg-primary/10 rounded-full blur-[120px]" />
        <div className="absolute bottom-0 right-1/4 w-[500px] h-[400px] bg-accent/8 rounded-full blur-[100px]" />
        <div className="absolute top-1/3 left-0 w-[400px] h-[300px] bg-primary/6 rounded-full blur-[80px]" />
      </div>

      {/* Grid pattern */}
      <div className="absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage: `radial-gradient(circle, hsl(217,91%,60%) 1px, transparent 1px)`,
          backgroundSize: '40px 40px',
        }}
      />

      <div className="container relative z-10 px-4 text-center">
        <div className="mb-8">
          <Badge variant="primary">
            <span className="w-2 h-2 rounded-full bg-primary mr-2 animate-pulse" />
            AI · 大模型驱动
          </Badge>
        </div>

        <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight leading-tight mb-6 max-w-5xl mx-auto">
          智能测试数据
          <br />
          <span className="text-gradient">生成平台</span>
        </h1>

        <p className="text-xl md:text-2xl text-muted-foreground max-w-3xl mx-auto mb-12 leading-relaxed">
          解析业务 SQL，自动理解数据库表结构与关联关系，
          融合 AI 大模型语义生成能力，一键生成符合业务语义、
          满足数据库约束的真实感测试数据。
        </p>

        {/* Feature pills */}
        <div className="flex flex-wrap justify-center gap-3 mb-16">
          {["SQL 约束感知", "AI 语义生成", "正则/范围/枚举", "多表 FK 自动处理", "事务原子写入"].map((f) => (
            <span key={f} className="px-4 py-2 rounded-full border border-primary/20 bg-primary/5 text-sm text-primary/90">
              {f}
            </span>
          ))}
        </div>

        {/* Flow diagram */}
        <div className="max-w-5xl mx-auto glass-card rounded-2xl p-8 mt-8">
          <div className="flex flex-wrap items-center justify-center gap-4 md:gap-6">
            {[
              { step: "①", label: "配置连接", icon: "🔌" },
              { step: "②", label: "输入 SQL", icon: "📝" },
              { step: "③", label: "配置规则", icon: "⚙️" },
              { step: "④", label: "预览数据", icon: "👁️" },
              { step: "⑤", label: "写入数据库", icon: "💾" },
            ].map((item, i) => (
              <div key={i} className="flex items-center gap-0">
                <div className="flex flex-col items-center">
                  <div className="w-16 h-16 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-2xl mb-2">
                    {item.icon}
                  </div>
                  <span className="text-xs text-primary font-semibold">{item.step}</span>
                  <span className="text-sm text-muted-foreground mt-1">{item.label}</span>
                </div>
                {i < 4 && (
                  <div className="hidden md:block w-12 h-0.5 bg-gradient-to-r from-primary/40 to-primary/10 ml-4 mr-2" />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
