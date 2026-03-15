"use client"

import {
  BarChart2,
  Receipt,
  Building2,
  CreditCard,
  Folder,
  Wallet,
  Users2,
  Shield,
  MessagesSquare,
  Video,
  Settings,
  HelpCircle,
  Menu,
  Home,
  ClipboardList,
  Smartphone,
} from "lucide-react"

import Link from "next/link"
import { useState } from "react"
import Image from "next/image"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Sheet, SheetContent, SheetTrigger, SheetTitle } from "@/components/ui/sheet"

export default function CustomSidebar() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)

  function handleNavigation() {
    setIsMobileMenuOpen(false)
  }

  function NavItem({
    href,
    icon: Icon,
    children,
  }: {
    href: string
    icon: any
    children: React.ReactNode
  }) {
    return (
      <Link
        href={href}
        onClick={handleNavigation}
        className="flex items-center px-3 py-3 text-base md:text-sm rounded-md transition-colors text-muted-foreground hover:text-foreground hover:bg-accent"
      >
        <Icon className="h-5 w-5 md:h-4 md:w-4 mr-3 flex-shrink-0" />
        <span>{children}</span>
      </Link>
    )
  }

  const navigationSections = [
    {
      title: "Overview",
      items: [
        { href: "/dashboard", icon: Home, label: "Dashboard" },
        { href: "/dashboard/addservice", icon: BarChart2, label: "Add Services" },
        { href: "/dashboard/addcountry", icon: Folder, label: "Add Country" },
        { href: "/dashboard/addnumber", icon: Building2, label: "Add Numbers" },
      ],
    },
    {
      title: "List",
      items: [
        { href: "/dashboard/countireslist", icon: Wallet, label: "Countires List" },
        { href: "/dashboard/serviceslist", icon: Receipt, label: "Services List" },
        { href: "/dashboard/numberslist", icon: CreditCard, label: "Numbers List" },
        { href: "/dashboard/devices", icon: Smartphone, label: "Devices" },
      ],
    },
        {
          title: "Others",
          items: [
            { href: "/dashboard/active-orders", icon: Wallet, label: "Active Orders" },
            { href: "/dashboard/activation", icon: Receipt, label: "Activations" },
            { href: "/dashboard/messagelist", icon: CreditCard, label: "Messages" },
            { href: "/dashboard/locks", icon: BarChart2, label: "Number Locks" },
            { href: "/dashboard/sms-template-generator", icon: MessagesSquare, label: "SMS Template Generator" },
            { href: "/dashboard/changelog", icon: ClipboardList, label: "Changelog" },
          ],
        },
  ]

  return (
    <>
      {/* Mobile Menu Button */}
      <Sheet open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
        <SheetTrigger asChild className="lg:hidden">
          <Button
            variant="outline"
            size="icon"
            className={`fixed top-4 left-4 z-50 bg-background transition-opacity ${isMobileMenuOpen ? 'opacity-0 pointer-events-none' : 'opacity-100'}`}
          >
            <Menu className="h-5 w-5" />
          </Button>
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0 z-[100]">
          <SheetTitle className="sr-only">Sidebar Navigation</SheetTitle>
          <SidebarContent />
        </SheetContent>
      </Sheet>

      {/* Desktop Sidebar */}
      <nav className="hidden lg:block fixed inset-y-0 left-0 z-[70] w-64 bg-background border-r">
        <SidebarContent />
      </nav>
    </>
  )

  function SidebarContent() {
    return (
      <div className="h-full flex flex-col">
        {/* Header */}
        <div className="h-16 px-6 flex items-center border-b">
          <Link href="#" className="flex items-center gap-3">
            <Image
              src="https://img.pikbest.com/png-images/20241029/simple-little-cat-logo-_11020838.png!sw800"
              alt="Acme"
              width={32}
              height={32}
              className="flex-shrink-0 hidden dark:block"
            />
            <Image
              src="https://img.pikbest.com/png-images/20241029/simple-little-cat-logo-_11020838.png!sw800"
              alt="Acme"
              width={32}
              height={32}
              className="flex-shrink-0 block dark:hidden"
            />
            <span className="text-lg font-semibold">Manager</span>
          </Link>
        </div>

        {/* Navigation */}
        <div className="flex-1 overflow-y-auto py-4 px-4">
          <div className="space-y-6">
            {navigationSections.map((section, index) => (
              <div key={index}>
                <div className="px-3 mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {section.title}
                </div>
                <div className="space-y-1">
                  {section.items.map((item, itemIndex) => (
                    <NavItem key={itemIndex} href={item.href} icon={item.icon}>
                      {item.label}
                    </NavItem>
                  ))}
                </div>
                {index < navigationSections.length - 1 && (
                  <Separator className="my-4" />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }
}
