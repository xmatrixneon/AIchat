"use client"

import { useState, useEffect, useRef } from "react"
import { getCookie } from "@/utils/cookie"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { toast } from "sonner"
import { MessageSquare, Image, Hash, Plus, Check, Loader2, Copy, Trash2, MessageCircle, Clock, Calendar, Hash as HashIcon, Type } from "lucide-react"

export default function AddServiceForm() {
  const [smsFormats, setSmsFormats] = useState<string[]>([""]) 
  const [showReplaceButtons, setShowReplaceButtons] = useState<boolean[]>([false]) 
  const [loading, setLoading] = useState(false)
  const [multisms, setMultisms] = useState(true)

  const textareaRefs = useRef<(HTMLTextAreaElement | null)[]>([])

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>, index: number) => {
    e.preventDefault()
    const pastedText = e.clipboardData.getData("text")
    const newFormats = [...smsFormats]
    newFormats[index] = pastedText
    setSmsFormats(newFormats)

    const newShow = [...showReplaceButtons]
    newShow[index] = true
    setShowReplaceButtons(newShow)
  }

  const handleReplace = (index: number) => {
    let replacedText = smsFormats[index]
    const otpKeywords = ["otp", "code", "password", "pass", "pin", "verification"]
    const otpRegex = new RegExp(`(${otpKeywords.join("|")})[^\\d]{0,10}(\\d{4,8})`, "i")
    const match = replacedText.match(otpRegex)

    if (match) {
      const otpValue = match[2]
      const otpNumberRegex = new RegExp(`\\b${otpValue}\\b`)
      replacedText = replacedText.replace(otpNumberRegex, "{otp}")
    } else {
      replacedText = replacedText.replace(/\b\d{4,8}\b/, "{otp}")
    }

    const newFormats = [...smsFormats]
    newFormats[index] = replacedText
    setSmsFormats(newFormats)

    const newShow = [...showReplaceButtons]
    newShow[index] = false
    setShowReplaceButtons(newShow)
  }


  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setLoading(true)

    const form = e.currentTarget
    const token = getCookie("token")
    const formData = new FormData(form)

    const body = {
      name: formData.get('name') as string,
      code: formData.get('code') as string,
      formate: smsFormats,
      image: formData.get('imageUrl') as string,
      multisms: multisms,
      maxmessage: formData.get('maxmessage') as string
    }

    try {
      const res = await fetch("/api/services/add", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      })

      const result = await res.json()
      if (result.success) {
        toast.success("Service created successfully.")
        form.reset()
        setSmsFormats([""])
        setShowReplaceButtons([false])
      } else {
        toast.error(result.error || "Something went wrong.")
      }
    } catch (error) {
      console.error(error)
      toast.error("Network error")
    } finally {
      setLoading(false)
    }
  }

  const addFormat = () => {
    setSmsFormats([...smsFormats, ""])
    setShowReplaceButtons([...showReplaceButtons, false])
  }

  const removeFormat = (index: number) => {
    const newFormats = smsFormats.filter((_, i) => i !== index)
    const newShow = showReplaceButtons.filter((_, i) => i !== index)
    setSmsFormats(newFormats)
    setShowReplaceButtons(newShow)
  }

  const insertPlaceholder = (index: number, placeholder: string) => {
    const textarea = textareaRefs.current[index]
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const value = smsFormats[index]

    const newValue = value.substring(0, start) + placeholder + value.substring(end)
    const newFormats = [...smsFormats]
    newFormats[index] = newValue
    setSmsFormats(newFormats)

    setTimeout(() => {
      textarea.focus()
      textarea.selectionStart = textarea.selectionEnd = start + placeholder.length
    }, 0)
  }

  const placeholders = ["{otp}", "{random}", "{date}", "{time}"]

  return (
    <div className="w-full">
      <form onSubmit={handleSubmit} className="w-full">
        <Card className="w-full">
          <CardHeader className="flex flex-row items-center gap-2">
            <MessageSquare className="h-5 w-5 md:h-6 md:w-6" />
            <CardTitle className="text-lg md:text-xl">Add Service</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-6">
            <div className="grid gap-2">
              <Label htmlFor="name">Service Name</Label>
              <div className="relative">
                <Type className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="name" name="name" placeholder="e.g., Telegram" className="pl-10" required />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="imageUrl">Image URL</Label>
              <div className="relative">
                <Image className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input
                  id="imageUrl"
                  name="imageUrl"
                  type="url"
                  placeholder="https://example.com/image.png"
                  className="pl-10"
                  required
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="code">Service Code</Label>
              <div className="relative">
                <Hash className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="code" name="code" placeholder="e.g., wiz" className="pl-10" required />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="maxmessage">Max Message</Label>
              <div className="relative">
                <MessageCircle className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="maxmessage" name="maxmessage" placeholder="0 for unlimited" type="number" className="pl-10" required />
              </div>
            </div>

            <div className="flex items-center gap-4">
              <MessageSquare className="h-4 w-4" />
              <Label htmlFor="multisms">Multisms</Label>
              <Switch id="multisms" checked={multisms} onCheckedChange={setMultisms} />
            </div>

            <div className="grid gap-4">
              <Label className="flex items-center gap-2">
                <Copy className="h-4 w-4" />
                SMS Formats
              </Label>
              {smsFormats.map((format, index) => (
                <div key={index} className="relative border p-3 rounded-lg">
                  <Textarea
                    ref={(el) => { textareaRefs.current[index] = el }}
                    value={format}
                    onChange={(e) => {
                      const newFormats = [...smsFormats]
                      newFormats[index] = e.target.value
                      setSmsFormats(newFormats)
                    }}
                    onPaste={(e) => handlePaste(e, index)}
                    placeholder={`Paste SMS format #${index + 1} here...`}
                    className="min-h-[120px] mb-2"
                    required
                  />
                  {showReplaceButtons[index] && (
                    <Button
                      type="button"
                      onClick={() => handleReplace(index)}
                      className="mt-2"
                    >
                      <Copy className="h-4 w-4 mr-2" />
                      Auto-replace OTP With <code>{'{otp}'}</code>
                    </Button>
                  )}
                  <div className="flex flex-wrap gap-2 mt-3">
                    {placeholders.map((ph) => (
                      <Button
                        key={ph}
                        type="button"
                        onClick={() => insertPlaceholder(index, ph)}
                        size="sm"
                        variant="outline"
                      >
                        {ph === "{otp}" && <HashIcon className="h-3 w-3 mr-1" />}
                        {ph === "{random}" && <Hash className="h-3 w-3 mr-1" />}
                        {ph === "{date}" && <Calendar className="h-3 w-3 mr-1" />}
                        {ph === "{time}" && <Clock className="h-3 w-3 mr-1" />}
                        {ph}
                      </Button>
                    ))}
                  </div>
                  {smsFormats.length > 1 && (
                    <Button
                      type="button"
                      onClick={() => removeFormat(index)}
                      variant="destructive"
                      size="sm"
                      className="absolute bottom-3 right-3"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              ))}
              <Button type="button" onClick={addFormat} variant="outline">
                <Plus className="h-4 w-4 mr-2" />
                Add Another Format
              </Button>
            </div>
          </CardContent>
          <CardFooter className="flex flex-col items-start">
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Submitting...
                </>
              ) : (
                <>
                  <Plus className="h-4 w-4 mr-2" />
                  Create Service
                </>
              )}
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
