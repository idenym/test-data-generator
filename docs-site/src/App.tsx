import { Navbar } from "@/components/Navbar"
import { HeroSection } from "@/components/HeroSection"
import { WorkflowSection } from "@/components/WorkflowSection"
import { StrategiesSection } from "@/components/StrategiesSection"
import { HighlightsSection } from "@/components/HighlightsSection"
import { ArchitectureSection } from "@/components/ArchitectureSection"
import { AIFlowSection } from "@/components/AIFlowSection"
import { TechStackSection } from "@/components/TechStackSection"
import { Footer } from "@/components/Footer"

function App() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <Navbar />
      <main>
        <div id="hero"><HeroSection /></div>
        <div id="workflow"><WorkflowSection /></div>
        <div id="strategies"><StrategiesSection /></div>
        <div id="highlights"><HighlightsSection /></div>
        <div id="architecture"><ArchitectureSection /></div>
        <div id="ai-flow"><AIFlowSection /></div>
        <div id="tech-stack"><TechStackSection /></div>
      </main>
      <Footer />
    </div>
  )
}

export default App
