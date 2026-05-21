export function Footer() {
  return (
    <footer className="py-16 px-4 border-t border-border/30">
      <div className="container max-w-6xl text-center">
        <div className="text-3xl font-extrabold tracking-tight mb-3">
          <span className="text-gradient">智能测试数据生成平台</span>
        </div>
        <p className="text-muted-foreground text-sm mb-6">
          AI 驱动 · 一键造数 · 约束感知 · 事务安全
        </p>
        <div className="flex flex-wrap justify-center gap-4 text-xs text-muted-foreground">
          <span>Spring Boot 2.7 + Vue 3 + JSQLParser</span>
          <span className="hidden md:inline">·</span>
          <span>DeepSeek V4 Pro + Mimo V2.5 Pro</span>
          <span className="hidden md:inline">·</span>
          <span>DataFaker + RgxGen + FastJSON</span>
        </div>
        <div className="mt-10 text-xs text-muted-foreground/50">
          Test Data Generator — 让测试数据准备从小时级压缩至分钟级
        </div>
      </div>
    </footer>
  )
}
