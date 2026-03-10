'use client'

import React from 'react'
import { DeviceDashboard } from '@/components/device-dashboard'
import { Button } from '@/components/ui/button'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'

export default function DevicesOverviewPage() {
  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <Link href="/dashboard/devices">
          <Button variant="ghost" className="mb-4">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Device List
          </Button>
        </Link>
        <h1 className="text-3xl font-bold tracking-tight">Device Overview</h1>
        <p className="text-muted-foreground">
          Comprehensive analytics and monitoring dashboard for all your SMS gateway devices
        </p>
      </div>
      <DeviceDashboard />
    </div>
  )
}