"use client"

import { useEffect, useState } from "react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { getCookie } from "@/utils/cookie"
import { Globe, Save, Settings, RefreshCw } from "lucide-react"
import { toast } from "sonner"

export default function PanelPage() {
  const [url, setUrl] = useState("")
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  const fetchPanelUrl = async () => {
    setRefreshing(true)
    try {
      const token = getCookie("token")
      const res = await fetch("/api/panel", {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
      const data = await res.json()
      if (data?.url) setUrl(data.url)
    } catch (err) {
      console.error(err)
    } finally {
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchPanelUrl()
  }, [])

  const handleSave = async () => {
    setLoading(true)
    try {
      const token = getCookie("token")
      const res = await fetch("/api/panel", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ url }),
      })
      const data = await res.json()
      toast.success(data.message || "Panel URL saved successfully")
    } catch (err) {
      console.error(err)
      toast.error("Failed to save panel URL")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen px-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="flex items-center justify-center gap-2">
            <Globe className="h-5 w-5 md:h-6 md:w-6" />
            <span className="text-lg md:text-xl">Panel Configuration</span>
          </CardTitle>
          <CardDescription>
            Set the URL for your external panel integration
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">Panel URL</label>
              <Button
                variant="ghost"
                size="sm"
                onClick={fetchPanelUrl}
                disabled={refreshing}
              >
                <RefreshCw className={`h-4 w-4 mr-1 ${refreshing ? 'animate-spin' : ''}`} />
                Refresh
              </Button>
            </div>
            <Input
              type="url"
              placeholder="https://example.com/panel"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="w-full"
            />
          </div>
          
          <Button 
            onClick={handleSave} 
            disabled={loading || !url}
            className="w-full"
          >
            <Save className="h-4 w-4 mr-2" />
            {loading ? "Saving..." : "Save Panel URL"}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
