"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Search, Filter, Calendar, MessageSquare, RefreshCw, History } from "lucide-react"

interface Country {
  _id: string
  name: string
  flag: string
  code: string
  dial: number
}

interface Service {
  _id: string
  name: string
  code: string
  image?: string
}

interface Order {
  _id: string
  number: number
  countryid: Country
  serviceid: Service
  createdAt: string
  isused: boolean
  active?: boolean
  message?: string[]
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(false)
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("all")
  const [selectedMessages, setSelectedMessages] = useState<string[] | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  const fetchOrders = async () => {
    setLoading(true)
    try {
      const query = new URLSearchParams()
      if (from) query.append("from", from)
      if (to) query.append("to", to)

      const res = await fetch(`/api/overview/activation?${query.toString()}`)
      const data = await res.json()
      if (data.success) setOrders(data.orders)
    } catch (err) {
      console.error("Error fetching orders:", err)
    }
    setLoading(false)
  }

  useEffect(() => {
    fetchOrders()
  }, [])

  const formatIST = (dateStr: string) => {
    const date = new Date(dateStr)
    return date.toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      dateStyle: "medium",
      timeStyle: "short",
    })
  }

  const getOrderStatus = (order: Order) => {
    if (!order.active) {
      return order.isused ? "used" : "canceled"
    }
    return order.isused ? "used" : "pending"
  }

  const filteredOrders = orders.filter((order) => {
    const query = search.toLowerCase()
    const matchesSearch =
      order.number.toString().includes(query) ||
      order.countryid?.name.toLowerCase().includes(query) ||
      order.serviceid?.name.toLowerCase().includes(query)

    const status = getOrderStatus(order)
    const matchesStatus = statusFilter === "all" || status === statusFilter

    return matchesSearch && matchesStatus
  })

  const totalPages = Math.ceil(filteredOrders.length / itemsPerPage)
  const paginatedOrders = filteredOrders.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <History className="h-6 w-6 md:h-8 md:w-8" />
            Order History
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            View and manage your order activation history
          </p>
        </div>
        <Button variant="outline" onClick={fetchOrders} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col lg:flex-row gap-4 items-end">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 flex-1">
              <div className="space-y-2">
                <label className="text-sm font-medium">From Date</label>
                <div className="relative">
                  <Calendar className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    type="date"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                    className="pl-10"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">To Date</label>
                <div className="relative">
                  <Calendar className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    type="date"
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                    className="pl-10"
                  />
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Status</label>
              <Select value={statusFilter} onValueChange={(value) => {
                setStatusFilter(value)
                setCurrentPage(1)
              }}>
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder="Filter by status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Status</SelectItem>
                  <SelectItem value="used">Used</SelectItem>
                  <SelectItem value="pending">Pending</SelectItem>
                  <SelectItem value="canceled">Canceled</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2 flex-1">
              <label className="text-sm font-medium">Search</label>
              <div className="relative">
                <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search orders..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value)
                    setCurrentPage(1)
                  }}
                  className="pl-10"
                />
              </div>
            </div>

            <Button onClick={fetchOrders}>
              <Filter className="h-4 w-4 mr-2" />
              Apply Filters
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Order History</CardTitle>
          <Badge variant="outline">{filteredOrders.length} orders</Badge>
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
          ) : filteredOrders.length === 0 ? (
            <div className="text-center py-16 space-y-4">
              <div className="text-muted-foreground text-lg">
                <MessageSquare className="h-16 w-16 mx-auto mb-4 opacity-50" />
                <p>No orders found matching your criteria</p>
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
                      <TableHead>Country</TableHead>
                      <TableHead>Service</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Active</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paginatedOrders.map((order) => (
                      <TableRow key={order._id}>
                        <TableCell className="font-medium">{order.number}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            {order.countryid?.flag && (
                              <img
                                src={order.countryid.flag}
                                alt={order.countryid.name}
                                className="w-5 h-5 rounded-full"
                              />
                            )}
                            <span>{order.countryid?.name || "N/A"}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            {order.serviceid?.image && (
                              <img
                                src={order.serviceid.image}
                                alt={order.serviceid.name}
                                className="w-5 h-5 rounded"
                              />
                            )}
                            <span>{order.serviceid?.name || "N/A"}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              getOrderStatus(order) === "used"
                                ? "default"
                                : getOrderStatus(order) === "pending"
                                ? "secondary"
                                : "destructive"
                            }
                          >
                            {getOrderStatus(order)}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={order.active ? "default" : "destructive"}>
                            {order.active ? "Active" : "Inactive"}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {formatIST(order.createdAt)}
                        </TableCell>
                        <TableCell>
                          {order.isused && order.message && order.message.length > 0 && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setSelectedMessages(order.message!)}
                            >
                              <MessageSquare className="h-4 w-4 mr-1" />
                              Messages
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              {filteredOrders.length > 0 && (
                <div className="flex items-center justify-between mt-4">
                  <div className="text-sm text-muted-foreground">
                    Showing {Math.min(filteredOrders.length, (currentPage - 1) * itemsPerPage + 1)}-
                    {Math.min(currentPage * itemsPerPage, filteredOrders.length)} of{" "}
                    {filteredOrders.length} orders
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                      disabled={currentPage === 1}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
                      disabled={currentPage === totalPages}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!selectedMessages} onOpenChange={() => setSelectedMessages(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Order Messages</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 max-h-60 overflow-y-auto">
            {selectedMessages?.map((msg, i) => (
              <div
                key={i}
                className="p-3 rounded-lg border bg-muted/50 text-sm"
              >
                {msg}
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button onClick={() => setSelectedMessages(null)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
