"use client"

import { useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
  Legend,
  Tooltip,
  LabelList,
  Cell,
} from "recharts"
import { getCookie } from "@/utils/cookie"

interface TodayChartData {
  hour: number
  totalSuccessOrders: number
  totalUnsuccessOrders: number
}

export function TodaySuccessChart() {
  const [chartData, setChartData] = useState<TodayChartData[]>([])
  const [successRate, setSuccessRate] = useState(0)
  const [peakHour, setPeakHour] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const token = getCookie("token")
  
  useEffect(() => {
    async function fetchChartData() {
      try {
        setLoading(true)
        const res = await fetch("/api/overview/today",{
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        })
        const data: TodayChartData[] = await res.json()

        const fullData: TodayChartData[] = Array.from({ length: 24 }, (_, i) => {
          const item = data.find(d => d.hour === i)
          return item || { hour: i, totalSuccessOrders: 0, totalUnsuccessOrders: 0 }
        })

        const totalOrdersPerHour = fullData.map(d => ({
          ...d,
          total: d.totalSuccessOrders + d.totalUnsuccessOrders
        }))

        const maxOrders = Math.max(...totalOrdersPerHour.map(d => d.total))
        const peak = totalOrdersPerHour.find(d => d.total === maxOrders)?.hour ?? null

        const totalSuccess = fullData.reduce((acc, d) => acc + d.totalSuccessOrders, 0)
        const totalOrders = fullData.reduce((acc, d) => acc + d.totalSuccessOrders + d.totalUnsuccessOrders, 0)
        const rate = totalOrders > 0 ? Math.round((totalSuccess / totalOrders) * 100) : 0

        setChartData(fullData)
        setSuccessRate(rate)
        setPeakHour(peak)
      } catch (err) {
        console.error(err)
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
          <CardTitle>Today’s Orders (Hourly)</CardTitle>
        </CardHeader>
        <CardContent className="px-2 sm:px-4 py-2">
          <div className="flex items-center justify-center h-96">
            <div className="animate-pulse text-muted-foreground">Loading chart data...</div>
          </div>
        </CardContent>
      </Card>
    )
  }

  const hasData = chartData.some(item => item.totalSuccessOrders > 0 || item.totalUnsuccessOrders > 0)

  if (!hasData) {
    return (
      <Card className="col-span-full">
        <CardHeader>
          <CardTitle>Today’s Orders (Hourly)</CardTitle>
        </CardHeader>
        <CardContent className="px-2 sm:px-4 py-2">
          <div className="flex items-center justify-center h-96">
            <div className="text-muted-foreground text-center">
              <p>No order data available for today.</p>
              <p className="text-sm">Orders will appear here as they are created.</p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="col-span-full">
      <CardHeader>
        <CardTitle>Today’s Orders (Hourly)</CardTitle>
      </CardHeader>
      <CardContent className="px-2 sm:px-4 py-2">
        <div style={{ width: "100%", height: 400 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 20, right: 20, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
              <XAxis 
                dataKey="hour" 
                tickLine={false} 
                axisLine={false}
                tick={{ fill: "var(--muted-foreground)" }}
              />
              <YAxis tick={{ fill: "var(--muted-foreground)" }} />
              <Tooltip 
                contentStyle={{ 
                  backgroundColor: "var(--background)",
                  borderColor: "var(--border)",
                  color: "var(--foreground)"
                }} 
              />
              <Legend />
              <Bar dataKey="totalSuccessOrders" name="Success Orders" fill="var(--chart-1)">
                {chartData.map((entry, index) => (
                  <Cell
                    key={`success-${index}`}
                    fill={entry.hour === peakHour ? "var(--chart-5)" : "var(--chart-1)"}
                  />
                ))}
                <LabelList 
                  dataKey="totalSuccessOrders" 
                  position="top" 
                  fill="var(--foreground)"
                  fontSize={12}
                />
              </Bar>
              <Bar dataKey="totalUnsuccessOrders" name="Failed Orders" fill="var(--chart-2)">
                {chartData.map((entry, index) => (
                  <Cell
                    key={`fail-${index}`}
                    fill={entry.hour === peakHour ? "var(--chart-5)" : "var(--chart-2)"}
                  />
                ))}
                <LabelList 
                  dataKey="totalUnsuccessOrders" 
                  position="top" 
                  fill="var(--foreground)"
                  fontSize={12}
                />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Summary text */}
        <div className="mt-3 text-center text-lg font-medium">
          Today’s Success Rate: <span className="text-green-500">{successRate}%</span>
        </div>
        {peakHour !== null && (
          <div className="mt-1 text-center text-sm text-yellow-600 font-semibold">
            Peak Orders Hour: {peakHour}:00 - {peakHour + 1}:00
          </div>
        )}
        <p className="mt-1 text-center text-sm text-muted-foreground">
          Peak buying hour is highlighted in yellow.
        </p>
      </CardContent>
    </Card>
  )
}
