import { cn } from "@/lib/utils"
import { ReactNode } from "react"

interface BadgeProps {
  children: ReactNode
  variant?: "primary" | "accent" | "warning" | "muted"
  className?: string
}

export function Badge({ children, variant = "primary", className }: BadgeProps) {
  return (
    <span className={cn(
      "inline-flex items-center rounded-full px-3 py-1 text-xs font-medium",
      variant === "primary" && "bg-primary/15 text-primary border border-primary/30",
      variant === "accent" && "bg-accent/15 text-accent border border-accent/30",
      variant === "warning" && "bg-warning/15 text-warning border border-warning/30",
      variant === "muted" && "bg-muted text-muted-foreground border border-border",
      className
    )}>
      {children}
    </span>
  )
}

interface SectionHeadingProps {
  overline?: string
  title: string
  description?: string
}

export function SectionHeading({ overline, title, description }: SectionHeadingProps) {
  return (
    <div className="text-center mb-16 animate-fade-in">
      {overline && (
        <span className="text-primary text-sm font-semibold tracking-wider uppercase mb-3 block">
          {overline}
        </span>
      )}
      <h2 className="text-3xl md:text-4xl font-bold tracking-tight">
        <span className="text-gradient">{title}</span>
      </h2>
      {description && (
        <p className="mt-4 text-muted-foreground text-lg max-w-2xl mx-auto leading-relaxed">
          {description}
        </p>
      )}
    </div>
  )
}

export function SectionHeadingLeft({ overline, title, description }: SectionHeadingProps) {
  return (
    <div className="mb-12">
      {overline && (
        <span className="text-primary text-sm font-semibold tracking-wider uppercase mb-2 block">
          {overline}
        </span>
      )}
      <h2 className="text-3xl md:text-4xl font-bold tracking-tight">
        <span className="text-gradient">{title}</span>
      </h2>
      {description && (
        <p className="mt-4 text-muted-foreground text-lg max-w-3xl leading-relaxed">
          {description}
        </p>
      )}
    </div>
  )
}

interface CardProps {
  children: ReactNode
  className?: string
  glow?: boolean
}

export function Card({ children, className, glow }: CardProps) {
  return (
    <div className={cn(
      "glass-card rounded-xl p-6 transition-all duration-300",
      "hover:-translate-y-1",
      glow && "glow-border",
      className
    )}>
      {children}
    </div>
  )
}
