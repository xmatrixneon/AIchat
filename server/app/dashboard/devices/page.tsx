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
    <div className="container mx-auto p-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight">Device Management</h1>
        <p className="text-muted-foreground">
          Monitor and manage your SMS gateway devices
        </p>
      </div>

      {/* Quick Actions Cards */}
      <div className="grid gap-4 md:grid-cols-3 mb-8">
        <Card className="hover:shadow-md transition-shadow cursor-pointer">
          <Link href="/dashboard/devices/overview">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg">Analytics Dashboard</CardTitle>
                <BarChart3 className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                View comprehensive device statistics and performance metrics
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <Badge variant="outline">Advanced</Badge>
                <span className="text-sm text-muted-foreground">Real-time insights</span>
              </div>
            </CardContent>
          </Link>
        </Card>

        <Card className="hover:shadow-md transition-shadow cursor-pointer">
          <Link href="/dashboard/devices">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg">Device List</CardTitle>
                <List className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Browse and manage all registered devices
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <Badge variant="outline">Management</Badge>
                <span className="text-sm text-muted-foreground">All devices</span>
              </div>
            </CardContent>
          </Link>
        </Card>

        <Card className="hover:shadow-md transition-shadow cursor-pointer">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-lg">Add New Device</CardTitle>
              <Plus className="h-5 w-5 text-muted-foreground" />
            </div>
            <CardDescription>
              Register a new SMS gateway device
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between">
              <Badge variant="outline">Setup</Badge>
              <Button size="sm">
                <Plus className="h-4 w-4 mr-2" />
                Add Device
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Device List Section */}
      <div>
        <div className="mb-6">
          <h2 className="text-2xl font-semibold">All Devices</h2>
          <p className="text-muted-foreground">
            Complete list of registered SMS gateway devices
          </p>
        </div>
        <DeviceList />
      </div>
    </div>
  )
}