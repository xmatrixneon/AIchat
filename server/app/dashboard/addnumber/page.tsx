"use client"

import { useEffect, useState } from "react"
import { getCookie } from "@/utils/cookie"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { toast } from "sonner"
import { Phone, Globe, Repeat, Clock, Check, Plus, Loader2, Hash } from "lucide-react"

export default function AddNumberForm() {
  const [countries, setCountries] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [multiuse, setMultiuse] = useState(false)
  const [selectedCountry, setSelectedCountry] = useState("")

  useEffect(() => {
    const loadCountries = async () => {
      try {
        const res = await fetch("/api/countries/all")
        const data = await res.json()
        if (data.success) {
          setCountries(data.countries)
        }
      } catch (error) {
        console.error("Failed to load countries:", error)
      }
    }
    loadCountries()
  }, [])

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setLoading(true)

    const form = e.currentTarget
    const token = getCookie("token")
    const formData = new FormData(form)

    const gapInHours = Number(formData.get('multigap')) || 0
    const gapInSeconds = gapInHours * 3600

    const body = {
      number: Number(formData.get('number')),
      countryid: selectedCountry,
      multiuse: multiuse,
      multigap: multiuse ? gapInSeconds : 0,
      active: formData.get('active') === 'on',
    }

    try {
      const res = await fetch("/api/numbers/add", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      })

      const result = await res.json()
      if (result.success) {
        toast.success("Number added successfully.")
        form.reset()
        setSelectedCountry("")
        setMultiuse(false)
      } else {
        toast.error(result.error || "Something went wrong.")
      }
    } catch (error) {
      console.error("Network error:", error)
      toast.error("Network error")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="w-full">
      <form onSubmit={handleSubmit}>
        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <Phone className="h-5 w-5 md:h-6 md:w-6" />
            <CardTitle className="text-lg md:text-xl">Add Number</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-6">
            <div className="grid gap-2">
              <Label htmlFor="number">Phone Number Without Dialcode</Label>
              <div className="relative">
                <Hash className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input id="number" name="number" type="number" placeholder="Enter phone number" className="pl-10" required />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="countryid">Country</Label>
              <div className="relative">
                <Globe className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Select value={selectedCountry} onValueChange={setSelectedCountry} required>
                  <SelectTrigger id="countryid" className="pl-10">
                    <SelectValue placeholder="Select Country" />
                  </SelectTrigger>
                  <SelectContent>
                    {countries.map((c) => (
                      <SelectItem key={c._id} value={c._id}>
                        {c.name} ({c.dial})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex items-center gap-4">
              <Repeat className="h-4 w-4" />
              <Label htmlFor="multiuse">Multiuse</Label>
              <Switch id="multiuse" checked={multiuse} onCheckedChange={setMultiuse} />
            </div>

            {multiuse && (
              <div className="grid gap-2">
                <Label htmlFor="multigap">Gap (in hours)</Label>
                <div className="relative">
                  <Clock className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="multigap"
                    name="multigap"
                    type="number"
                    step="0.1"
                    placeholder="e.g. 1 for 1 hour"
                    className="pl-10"
                    required={multiuse}
                  />
                </div>
              </div>
            )}

            <div className="flex items-center gap-4">
              <Check className="h-4 w-4" />
              <Label htmlFor="active">Active</Label>
              <Switch id="active" name="active" defaultChecked />
            </div>
          </CardContent>
          <CardFooter>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Submitting...
                </>
              ) : (
                <>
                  <Plus className="h-4 w-4 mr-2" />
                  Add Number
                </>
              )}
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
