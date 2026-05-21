import { Menu, X } from "lucide-react"
import { useState } from "react"

const navLinks = [
  { label: "产品概述", href: "#hero" },
  { label: "功能流程", href: "#workflow" },
  { label: "创新亮点", href: "#highlights" },
  { label: "技术架构", href: "#architecture" },
  { label: "AI 交互", href: "#ai-flow" },
  { label: "技术选型", href: "#tech-stack" },
]

export function Navbar() {
  const [open, setOpen] = useState(false)

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 border-b border-border/30 bg-background/80 backdrop-blur-xl">
      <div className="container max-w-6xl px-4">
        <div className="flex items-center justify-between h-16">
          <a href="#hero" className="flex items-center gap-2.5 font-bold text-lg tracking-tight">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center text-xs font-extrabold text-white">
              TD
            </div>
            <span className="hidden sm:inline text-gradient">TestDataGen</span>
          </a>

          <div className="hidden lg:flex items-center gap-1">
            {navLinks.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="px-3 py-2 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 transition-all duration-200"
              >
                {link.label}
              </a>
            ))}
          </div>

          <button
            className="lg:hidden p-2 rounded-lg hover:bg-muted/50 transition-colors"
            onClick={() => setOpen(!open)}
          >
            {open ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>

        {open && (
          <div className="lg:hidden py-4 border-t border-border/30">
            {navLinks.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="block px-4 py-3 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 transition-all"
                onClick={() => setOpen(false)}
              >
                {link.label}
              </a>
            ))}
          </div>
        )}
      </div>
    </nav>
  )
}
