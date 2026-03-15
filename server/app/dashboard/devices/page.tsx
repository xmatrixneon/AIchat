'use client'

import React from 'react'
import { DeviceList } from '@/components/device-list'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { BarChart3, List, Plus, Smartphone } from 'lucide-react'
import Link from 'next/link'

export default function DevicesPage() {
  return (
    <div className="container mx-auto px-4 py-4 md:p-6">
      


      {/* Device List Section */}
      <div>
        <div className="mb-4 md:mb-6">
          <h2 className="text-xl md:text-2xl font-semibold">All Devices</h2>
          <p className="text-sm md:text-base text-muted-foreground">
            Complete list of registered SMS gateway devices
          </p>
        </div>
        <DeviceList />
      </div>
    </div>
  )
}