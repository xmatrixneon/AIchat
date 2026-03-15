'use client'

import React from 'react'
import { Button } from '@/components/ui/button'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'

export default function DevicesOverviewPage() {
  return (
    <div className="container mx-auto px-4 py-4 md:p-6">
      <div className="mb-4 md:mb-6">
        <Link href="/dashboard/devices">
          <Button variant="ghost" size="sm" className="mb-3 md:mb-4 h-9">
            <ArrowLeft className="h-3.5 w-3.5 mr-1.5" />
            Back
          </Button>
        </Link>
        <h1 className="text-2xl md:text-3xl font-bold tracking-tight">Device Overview</h1>
        <p className="text-sm md:text-base text-muted-foreground">
          Comprehensive analytics and monitoring dashboard for all your SMS gateway devices
        </p>
      </div>
    </div>
  )
}