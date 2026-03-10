"use client"

import { useState, useEffect } from "react"
import { getCookie } from "@/utils/cookie"
import { formatDistanceToNow } from "date-fns"
import { Trash2, Mail, User, Clock, Search, MessageSquare, RefreshCw, Inbox, Copy } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

interface Message {
  _id: string
  sender: string
  receiver: string
  message: string
  port?: string
  createdAt: string
}

export default function MessagesGrid() {
  const [messages, setMessages] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null)
  const itemsPerPage = 9

  const fetchMessages = async () => {
    try {
      setLoading(true)
      const token = getCookie("token")
      const res = await fetch("/api/messages/all", {
        headers: { Authorization: `Bearer ${token}` },
      })
      const data = await res.json()
      if (data.success) setMessages(data.data)
    } catch (err) {
      console.error("Error fetching messages:", err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchMessages()
  }, [])

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this message?")) return
    try {
      const token = getCookie("token")
      const res = await fetch(`/api/messages/all/${id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      })
      const data = await res.json()
      if (data.success)
        setMessages((prev) => prev.filter((m) => m._id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  const handleCopy = async (message: string, id: string) => {
    try {
      await navigator.clipboard.writeText(message)
      setCopiedMessageId(id)
      setTimeout(() => setCopiedMessageId(null), 2000)
    } catch (err) {
      console.error("Failed to copy text: ", err)
      // Fallback for older browsers
      const textArea = document.createElement("textarea")
      textArea.value = message
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand("copy")
      document.body.removeChild(textArea)
      setCopiedMessageId(id)
      setTimeout(() => setCopiedMessageId(null), 2000)
    }
  }

  const filteredMessages = messages.filter(
    (m) =>
      m.sender.toLowerCase().includes(search.toLowerCase()) ||
      m.receiver.toLowerCase().includes(search.toLowerCase()) ||
      m.message.toLowerCase().includes(search.toLowerCase())
  )
  const totalPages = Math.ceil(filteredMessages.length / itemsPerPage)
  const paginatedMessages = filteredMessages.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <Inbox className="h-6 w-6 md:h-8 md:w-8" />
            Incoming Messages
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            View and manage all received SMS messages
          </p>
        </div>
        <Button variant="outline" onClick={fetchMessages} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="flex items-center gap-2">
            <Search className="h-5 w-5" />
            Search Messages
          </CardTitle>
          <span className="text-sm text-muted-foreground">
            {filteredMessages.length} messages
          </span>
        </CardHeader>
        <CardContent>
          <div className="relative">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search sender, receiver or message content..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setCurrentPage(1)
              }}
              className="pl-10"
            />
          </div>
        </CardContent>
      </Card>

      {loading ? (
        <div className="space-y-4">
          {[...Array(6)].map((_, i) => (
            <Card key={i} className="p-4">
              <div className="flex items-center space-x-4">
                <div className="w-10 h-10 bg-muted rounded-full animate-pulse"></div>
                <div className="space-y-2 flex-1">
                  <div className="h-4 bg-muted rounded animate-pulse w-3/4"></div>
                  <div className="h-3 bg-muted rounded animate-pulse w-1/2"></div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : filteredMessages.length === 0 ? (
        <div className="text-center py-16 space-y-4">
          <MessageSquare className="h-16 w-16 mx-auto opacity-50" />
          <p className="text-muted-foreground text-lg">No messages found</p>
          <Button variant="outline" onClick={fetchMessages}>
            <RefreshCw className="h-4 w-4 mr-2" />
            Refresh
          </Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {paginatedMessages.map((msg) => (
              <Card key={msg._id} className="hover:shadow-lg transition-shadow group">
                <CardContent className="p-4">
                  <div className="flex justify-between items-center mb-3">
                    <div className="flex items-center gap-2">
                      <User className="text-primary h-5 w-5" />
                      <span className="font-semibold text-sm">{msg.sender}</span>
                    </div>
                    <Badge variant="outline" className="text-xs">
                      {msg.port || "N/A"}
                    </Badge>
                  </div>

                  <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
                    <Mail className="h-4 w-4 text-muted-foreground" />
                    <span className="text-xs">{msg.receiver}</span>
                  </div>

                  <p className="text-sm text-muted-foreground mb-3 bg-muted/50 p-2 rounded">
                    {msg.message}
                  </p>

                  <div className="flex justify-between items-center text-xs text-muted-foreground">
                    <div className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      <span>
                        {msg.createdAt
                          ? formatDistanceToNow(new Date(msg.createdAt), {
                              addSuffix: true,
                            })
                          : "Unknown"}
                      </span>
                    </div>
                    <div className="flex items-center gap-1">
                      {copiedMessageId === msg._id ? (
                        <span className="text-xs text-green-600 font-medium">Copied!</span>
                      ) : (
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => handleCopy(msg.message, msg._id)}
                          className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                          title="Copy message"
                        >
                          <Copy className="h-3 w-3" />
                        </Button>
                      )}
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => handleDelete(msg._id)}
                        className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                        title="Delete message"
                      >
                        <Trash2 className="h-3 w-3" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Pagination */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mt-6">
            <div className="text-sm text-muted-foreground">
              Showing {paginatedMessages.length} of {filteredMessages.length} messages
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                disabled={currentPage === 1}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground mx-2">
                Page {currentPage} of {totalPages}
              </span>
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
        </>
      )}
    </div>
  )
}
