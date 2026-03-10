"use client"

import type { ReactNode } from "react"
import { useEffect, useState } from "react"
import { useTheme } from "next-themes"
import CustomSidebar from "@/components/ui/custom-sidebar"
import TopNavEnhanced from "@/components/ui/top-nav-enhanced"
import { AuthProvider } from "@/context/AuthContext"

interface LayoutProps {
  children: ReactNode
}

export default function Layout({ children }: LayoutProps) {
  const { theme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  if (!mounted) {
    return null
  }

  return (
    <AuthProvider>
      <div className={`flex h-screen ${theme === "dark" ? "dark" : ""}`}>
        <CustomSidebar />
        <div className="w-full flex flex-1 flex-col lg:ml-64">
          <header className="h-16 border-b">
            <TopNavEnhanced />
          </header>
          <main className="flex-1 overflow-auto p-6 bg-background">
            {children}
          </main>
        </div>
      </div>
    </AuthProvider>
  )
}
