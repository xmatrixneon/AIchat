"use client"

import { useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
} from "recharts"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart"
import { getCookie } from "@/utils/cookie"

interface ChartData {
  date: string
  activation: number
  action: number
  cancel: number
}

interface ApiChartData {
  date: string
  totalSuccessOrders: number
  totalUnsuccessOrders: number
  usedNumbers: number
}

export function ActivationActionChart() {
  const [chartData, setChartData] = useState<ChartData[]>([])
  const [loading, setLoading] = useState(true)
  const token = getCookie("token")
  
  useEffect(() => {
    async function fetchChartData() {
      try {
        setLoading(true)
        const res = await fetch("/api/overview/chart",{
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        })
        const data: ApiChartData[] = await res.json()
        const mapped = data.map(item => ({
          date: item.date,
          activation: item.totalSuccessOrders,
          action: item.totalUnsuccessOrders,
          cancel: item.usedNumbers,
        }))
        setChartData(mapped)
      } catch (err) {
        console.error("Error fetching chart data:", err)
      } finally {
        setLoading(false)
      }
    }
    fetchChartData()
  }, [])

  if (loading) {
    return (
      <Card className="col-span-full">
        <CardHeader>
          <CardTitle>Activation & Orders Trends (Last 7 Days)</CardTitle>
        </CardHeader>
        <CardContent className="px-2 sm:px-4 py-2">
          <div className="flex items-center justify-center h-96">
            <div className="animate-pulse text-muted-foreground">Loading chart data...</div>
          </div>
        </CardContent>
      </Card>
    )
  }

  const hasData = chartData.some(item => 
    item.activation > 0 || item.action > 0 || item.cancel > 0
  )

  if (!hasData) {
    return (
      <Card className="col-span-full">
        <CardHeader>
          <CardTitle>Activation & Orders Trends (Last 7 Days)</CardTitle>
        </CardHeader>
        <CardContent className="px-2 sm:px-4 py-2">
          <div className="flex items-center justify-center h-96">
            <div className="text-muted-foreground text-center">
              <p>No data available for the last 7 days.</p>
              <p className="text-sm">Data will appear here as orders are created.</p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="col-span-full">
      <CardHeader>
        <CardTitle>Activation & Orders Trends (Last 7 Days)</CardTitle>
      </CardHeader>
      <CardContent className="px-2 sm:px-4 py-2">
        <div style={{ width: "100%", height: 400 }}>
          <ChartContainer
            config={{
              activation: { label: "Success Orders", color: "var(--chart-1)" },
              action: { label: "Unsuccess Orders", color: "var(--chart-2)" },
              cancel: { label: "Used Numbers", color: "var(--chart-3)" },
            }}
          >
            <div className="w-full h-[300px] sm:h-[350px] md:h-[400px] lg:h-[450px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={chartData}
                  margin={{ top: 20, right: 20, left: 0, bottom: 5 }}
                >
                  <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis 
                    dataKey="date" 
                    tickLine={false} 
                    axisLine={false}
                    tick={{ fill: "var(--muted-foreground)" }}
                  />
                  <YAxis tick={{ fill: "var(--muted-foreground)" }} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <Legend />
                  <Bar dataKey="activation" fill="var(--chart-1)" name="Success Orders" />
                  <Bar dataKey="action" fill="var(--chart-2)" name="Unsuccess Orders" />
                  <Bar dataKey="cancel" fill="var(--chart-3)" name="Used Numbers" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </ChartContainer>
        </div>
      </CardContent>
    </Card>
  )
}
