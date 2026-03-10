"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Loader2, Unlock, Lock, Filter, RefreshCw } from "lucide-react"
import { toast } from "sonner"

interface Lock {
  _id: string
  number: number
  country: string
  service: string
  locked: boolean
  createdAt?: string
  updatedAt?: string
}

export default function LocksList() {
  const [locks, setLocks] = useState<Lock[]>([])
  const [loading, setLoading] = useState(true)
  const [unlocking, setUnlocking] = useState<string | null>(null)
  const [unlockingAll, setUnlockingAll] = useState(false)
  const [selectedService, setSelectedService] = useState<string>("All")
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  useEffect(() => {
    async function fetchLocks() {
      try {
        setLoading(true)
        const res = await fetch(`/api/locks/list`)
        const data = await res.json()
        setLocks(data.locks || [])
      } catch (err) {
        console.error("Error fetching locks:", err)
      } finally {
        setLoading(false)
      }
    }
    fetchLocks()
  }, [])

  const handleUnlock = async (id: string) => {
    try {
      setUnlocking(id)
      const res = await fetch("/api/locks/unlock", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id }),
      })

      const data = await res.json()
      if (!res.ok) {
        toast.error(data.error || "Failed to unlock")
        return
      }

      setLocks((prev) =>
        prev.map((lock) =>
          lock._id === id ? { ...lock, locked: false } : lock
        )
      )
      toast.success("Number unlocked successfully!")
    } catch (err) {
      console.error("Unlock error:", err)
      toast.error("Failed to unlock number")
    } finally {
      setUnlocking(null)
    }
  }

  const handleUnlockAll = async () => {
    if (selectedService === "All") {
      toast.error("Please select a specific service to unlock all")
      return
    }

    try {
      setUnlockingAll(true)
      const res = await fetch("/api/locks/unlock-all", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ service: selectedService }),
      })

      const data = await res.json()
      if (!res.ok) {
        toast.error(data.error || "Failed to unlock all")
        return
      }

      // Refresh the locks list
      const refreshRes = await fetch(`/api/locks/list`)
      const refreshData = await refreshRes.json()
      setLocks(refreshData.locks || [])
      
      toast.success(data.message || `Unlocked all ${selectedService} locks successfully!`)
    } catch (err) {
      console.error("Bulk unlock error:", err)
      toast.error("Failed to unlock all numbers")
    } finally {
      setUnlockingAll(false)
    }
  }

  const formatIST = (dateString?: string) => {
    if (!dateString) return "N/A"
    return new Date(dateString).toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    })
  }

  const serviceOptions = ["All", ...Array.from(new Set(locks.map((l) => l.service || "Unknown Service")))]

  const filteredLocks =
    selectedService === "All"
      ? locks
      : locks.filter((l) => (l.service || "Unknown Service") === selectedService)

  const totalPages = Math.ceil(filteredLocks.length / itemsPerPage)
  const paginatedLocks = filteredLocks.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <Lock className="h-6 w-6 md:h-8 md:w-8" />
            Number Locks
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            Manage locked phone numbers and unlock them when needed
          </p>
        </div>
        <Button variant="outline" onClick={() => window.location.reload()}>
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader className="space-y-4 pb-2">
          <CardTitle className="flex items-center gap-2">
            <Filter className="h-5 w-5" />
            Locked Numbers
          </CardTitle>
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <span className="text-sm text-muted-foreground">
              {filteredLocks.length} locks
            </span>
            <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto">
              <Select value={selectedService} onValueChange={(value) => {
                setSelectedService(value)
                setCurrentPage(1)
              }}>
                <SelectTrigger className="w-full sm:w-[180px] md:w-[200px]">
                  <SelectValue placeholder="Filter by service" />
                </SelectTrigger>
                <SelectContent>
                  {serviceOptions.map((service) => (
                    <SelectItem key={service} value={service}>
                      {service}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {selectedService !== "All" && (
                <Button
                  variant="destructive"
                  onClick={handleUnlockAll}
                  disabled={unlockingAll}
                  className="flex items-center gap-2 w-full sm:w-auto"
                >
                  {unlockingAll ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Unlock className="h-4 w-4" />
                  )}
                  <span className="hidden sm:inline">Unlock All {selectedService}</span>
                  <span className="sm:hidden">Unlock All</span>
                </Button>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-4">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center space-x-4 p-4 border rounded-lg">
                  <div className="w-20 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="w-16 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="w-24 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="w-16 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="w-32 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="w-20 h-8 bg-muted rounded animate-pulse"></div>
                </div>
              ))}
            </div>
          ) : filteredLocks.length === 0 ? (
            <div className="text-center py-16 space-y-4">
              <Lock className="h-16 w-16 mx-auto opacity-50" />
              <p className="text-muted-foreground text-lg">No locked numbers found</p>
              <Button variant="outline" onClick={() => window.location.reload()}>
                <RefreshCw className="h-4 w-4 mr-2" />
                Refresh
              </Button>
            </div>
          ) : (
            <div className="rounded-md border overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[120px] min-w-[120px]">Number</TableHead>
                    <TableHead className="w-[100px] min-w-[100px]">Country</TableHead>
                    <TableHead className="min-w-[120px]">Service</TableHead>
                    <TableHead className="w-[100px] min-w-[100px]">Status</TableHead>
                    <TableHead className="w-[180px] min-w-[180px]">Created At</TableHead>
                    <TableHead className="w-[120px] min-w-[120px] text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {paginatedLocks.map((lock) => (
                    <TableRow key={lock._id}>
                      <TableCell className="font-medium">{lock.number}</TableCell>
                      <TableCell>{lock.country || "Unknown"}</TableCell>
                      <TableCell className="max-w-[200px] truncate">{lock.service || "Unknown"}</TableCell>
                      <TableCell>
                        <Badge variant={lock.locked ? "destructive" : "default"} className="flex items-center gap-1">
                          {lock.locked ? <Lock className="h-3 w-3" /> : <Unlock className="h-3 w-3" />}
                          {lock.locked ? "Locked" : "Unlocked"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm">
                        {formatIST(lock.createdAt)}
                      </TableCell>
                      <TableCell className="text-right">
                        {lock.locked && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleUnlock(lock._id)}
                            disabled={unlocking === lock._id}
                            className="w-full justify-center"
                          >
                            {unlocking === lock._id ? (
                              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                            ) : (
                              <Unlock className="h-4 w-4 mr-1" />
                            )}
                            <span className="hidden xs:inline">Unlock</span>
                            <span className="xs:hidden">UL</span>
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              {filteredLocks.length > 0 && (
                <div className="flex flex-col sm:flex-row items-center justify-between mt-4 gap-4 p-4">
                  <div className="text-sm text-muted-foreground text-center sm:text-left">
                    Showing {Math.min(filteredLocks.length, (currentPage - 1) * itemsPerPage + 1)}-
                    {Math.min(currentPage * itemsPerPage, filteredLocks.length)} of{" "}
                    {filteredLocks.length} locks
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
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
