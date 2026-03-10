"use client"

import { useState } from "react"
import { getCookie } from "@/utils/cookie"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { toast } from "sonner"
import { Globe, Image, Hash, Phone, Check, Plus } from "lucide-react"

export default function AddCountryForm() {
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setLoading(true)

    const form = e.currentTarget
    const token = getCookie("token")
    const formData = new FormData(form)
    
    const body = {
      name: formData.get('name') as string,
      flag: formData.get('flag') as string,
      code: formData.get('code') as string,
      dial: parseInt(formData.get('dial') as string),
      active: (formData.get('active') as string) === 'on',
    }

    try {
      const res = await fetch("/api/countries/add", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      })

      const result = await res.json()
      if (result.success) {
        toast.success("Country added successfully.")
        form.reset()
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

  return (
    <div className="w-full">
      <form onSubmit={handleSubmit} className="w-full">
        <Card className="w-full">
          <CardHeader className="flex flex-row items-center gap-2">
            <Globe className="h-5 w-5 md:h-6 md:w-6" />
            <CardTitle className="text-lg md:text-xl">Add Country</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-6">
            <div className="grid gap-2">
              <Label htmlFor="name">Country Name</Label>
              <div className="relative">
                <Globe className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="name" name="name" placeholder="e.g., India" className="pl-10" required />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="flag">Flag URL</Label>
              <div className="relative">
                <Image className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input
                  id="flag"
                  name="flag"
                  type="url"
                  placeholder="https://example.com/flag.png"
                  className="pl-10"
                  required
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="code">Country Code</Label>
              <div className="relative">
                <Hash className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="code" name="code" placeholder="e.g., 22" className="pl-10" required />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="dial">Dial Code</Label>
              <div className="relative">
                <Phone className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="dial" name="dial" type="number" placeholder="e.g., 91" className="pl-10" required />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Switch id="active" name="active" defaultChecked />
              <Label htmlFor="active">Active</Label>
            </div>
          </CardContent>
          <CardFooter className="flex flex-col items-start">
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? (
                <>
                  <Check className="h-4 w-4 mr-2" />
                  Submitting...
                </>
              ) : (
                <>
                  <Plus className="h-4 w-4 mr-2" />
                  Create Country
                </>
              )}
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
