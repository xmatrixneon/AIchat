"use client"

import { useEffect, useState } from "react"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { getCookie } from "@/utils/cookie"
import { Loader2, RefreshCw, Activity, Clock, MessageSquare, Hash, Key, Calendar } from "lucide-react"

interface ActiveOrder {
  id: string
  number: string
  serviceName: string
  dialcode: number
  isused: boolean
  ismultiuse: boolean
  nextsms: boolean
  messageCount: number
  keywords: string[]
  formate: string
  createdAt: string
  updatedAt: string
}

export default function ActiveOrdersPage() {
  const [orders, setOrders] = useState<ActiveOrder[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const perPage = 30
  const token = getCookie("token")

  async function fetchOrders() {
    try {
      setRefreshing(true)
      const res = await fetch("/api/overview/active-orders", {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      })
      const data: ActiveOrder[] = await res.json()
      setOrders(data)
    } catch (err) {
      console.error("Error fetching active orders:", err)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchOrders()
    const interval = setInterval(fetchOrders, 20000) // Auto-refresh every 20s
    return () => clearInterval(interval)
  }, [])

  const totalPages = Math.ceil(orders.length / perPage)
  const paginatedOrders = orders.slice(
    (currentPage - 1) * perPage,
    currentPage * perPage
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <Activity className="h-6 w-6 md:h-8 md:w-8" />
            Active Orders
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            Monitor and manage all currently active orders
          </p>
        </div>
        <Button
          variant="outline"
          onClick={fetchOrders}
          disabled={refreshing}
        >
          <RefreshCw className={`h-4 w-4 mr-2 ${refreshing ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle>Orders Overview</CardTitle>
          <Badge variant="outline" className="text-xs">
            Auto-refreshing every 20s
          </Badge>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-4">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center space-x-4">
                  <Skeleton className="h-12 w-12 rounded-full" />
                  <div className="space-y-2">
                    <Skeleton className="h-4 w-[250px]" />
                    <Skeleton className="h-4 w-[200px]" />
                  </div>
                </div>
              ))}
            </div>
          ) : orders.length === 0 ? (
            <div className="text-center py-16 space-y-4">
              <div className="text-muted-foreground text-lg">
                <Activity className="h-16 w-16 mx-auto mb-4 opacity-50" />
                <p>No active orders found</p>
              </div>
              <Button variant="outline" onClick={fetchOrders}>
                <RefreshCw className="h-4 w-4 mr-2" />
                Refresh
              </Button>
            </div>
          ) : (
            <>
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Number</TableHead>
                      <TableHead>Service</TableHead>
                      <TableHead>Dial Code</TableHead>
                      <TableHead>Used</TableHead>
                      <TableHead>Multi-use</TableHead>
                      <TableHead>Next SMS</TableHead>
                      <TableHead>Messages</TableHead>
                      <TableHead>Created</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedOrders.map(order => (
                      <TableRow key={order.id}>
                        <TableCell className="font-medium">{order.number}</TableCell>
                        <TableCell>{order.serviceName}</TableCell>
                        <TableCell>{order.dialcode}</TableCell>
                        <TableCell>
                          <Badge variant={order.isused ? "destructive" : "secondary"}>
                            {order.isused ? "Yes" : "No"}
                          </Badge>
                        </TableCell>
                        <TableCell>{order.ismultiuse ? "Yes" : "No"}</TableCell>
                        <TableCell>{order.nextsms ? "Yes" : "No"}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{order.messageCount}</Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {new Date(order.createdAt).toLocaleString()}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              <div className="flex flex-col md:flex-row justify-between items-center gap-3 mt-6">
                <div className="text-sm text-muted-foreground">
                  Page <span className="font-medium">{currentPage}</span> of{" "}
                  <span className="font-medium">{totalPages}</span>
                </div>
                <div className="flex gap-2">
                  <Button
                    disabled={currentPage === 1}
                    onClick={() => setCurrentPage(prev => prev - 1)}
                    variant="outline"
                    size="sm"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={currentPage === totalPages}
                    onClick={() => setCurrentPage(prev => prev + 1)}
                    size="sm"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
