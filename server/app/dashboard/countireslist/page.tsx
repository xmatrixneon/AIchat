"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Pencil, Trash2, Check, X, Globe, Flag, Hash, Phone, Edit3, Search, RefreshCw, Image as ImageIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { getCookie } from "@/utils/cookie";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

interface Country {
  _id: string;
  name: string;
  code: string;
  dial: number;
  flag: string;
  active: boolean;
}

export default function CountriesPage() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [loading, setLoading] = useState(true);
  const [editId, setEditId] = useState<string | null>(null);
  const [editData, setEditData] = useState<Partial<Country>>({});
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [countryToDelete, setCountryToDelete] = useState<string | null>(null);
  const token = getCookie("token");

  useEffect(() => {
    setLoading(true);
    fetch("/api/countries/all", {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    })
      .then((res) => res.json())
      .then((data) => {
        if (data.success) setCountries(data.countries);
      })
      .finally(() => setLoading(false));
  }, []);

  const startEdit = (country: Country) => {
    setEditId(country._id);
    setEditData({
      name: country.name,
      code: country.code,
      dial: country.dial,
      flag: country.flag,
      active: country.active,
    });
  };

  const cancelEdit = () => {
    setEditId(null);
    setEditData({});
  };

  const handleChange = (field: keyof Country, value: any) => {
    setEditData((prev) => ({ ...prev, [field]: value }));
  };

  const saveEdit = async (id: string) => {
    try {
      const res = await fetch(`/api/countries/edit/${id}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(editData),
      });

      if (!res.ok) throw new Error("Update failed");

      const updated = await res.json();
      if (updated.success) {
        setCountries((prev) =>
          prev.map((c) =>
            c._id === id ? ({ ...c, ...editData } as Country) : c
          )
        );
        cancelEdit();
      } else {
        alert(updated.error || "Update failed");
      }
    } catch (err) {
      alert("Failed to update country");
      console.error(err);
    }
  };

  const handleDelete = async () => {
    if (!countryToDelete) return;

    const res = await fetch(`/api/countries/delete/${countryToDelete}`, {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });
    if (res.ok) {
      setCountries((prev) => prev.filter((c) => c._id !== countryToDelete));
      setDeleteDialogOpen(false);
      setCountryToDelete(null);
    }
  };

  const openDeleteDialog = (id: string) => {
    setCountryToDelete(id);
    setDeleteDialogOpen(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <Globe className="h-6 w-6 md:h-8 md:w-8" />
            All Countries
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            Manage and view all available countries
          </p>
        </div>
        <Button variant="outline" onClick={() => window.location.reload()}>
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="flex items-center gap-2">
            <Flag className="h-5 w-5" />
            Countries List
          </CardTitle>
          <span className="text-sm text-muted-foreground">
            {countries.length} countries
          </span>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-4">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center space-x-4 p-4 border rounded-lg">
                  <div className="w-8 h-6 bg-muted rounded animate-pulse"></div>
                  <div className="space-y-2 flex-1">
                    <div className="h-4 bg-muted rounded animate-pulse w-3/4"></div>
                    <div className="h-3 bg-muted rounded animate-pulse w-1/2"></div>
                  </div>
                  <div className="flex gap-2">
                    <div className="w-8 h-8 bg-muted rounded animate-pulse"></div>
                    <div className="w-8 h-8 bg-muted rounded animate-pulse"></div>
                  </div>
                </div>
              ))}
            </div>
          ) : countries.length === 0 ? (
            <div className="text-center py-16 space-y-4">
              <Globe className="h-16 w-16 mx-auto opacity-50" />
              <p className="text-muted-foreground text-lg">No countries found</p>
              <Button variant="outline" onClick={() => window.location.reload()}>
                <RefreshCw className="h-4 w-4 mr-2" />
                Refresh
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[100px]">Flag</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Code</TableHead>
                  <TableHead>Dial</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {countries.map((country) => {
                  const isEditing = editId === country._id;
                  return (
                    <TableRow key={country._id}>
                      <TableCell>
                        {isEditing ? (
                          <div className="relative">
                            <ImageIcon className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                            <Input
                              type="text"
                              value={editData.flag || ""}
                              onChange={(e) => handleChange("flag", e.target.value)}
                              className="pl-10 w-full"
                              placeholder="Flag URL"
                            />
                          </div>
                        ) : (
                          <img
                            src={country.flag}
                            alt={`${country.name} flag`}
                            className="w-8 h-6 object-cover rounded"
                          />
                        )}
                      </TableCell>

                      <TableCell>
                        {isEditing ? (
                          <div className="relative">
                            <Edit3 className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                            <Input
                              type="text"
                              value={editData.name || ""}
                              onChange={(e) => handleChange("name", e.target.value)}
                              className="pl-10"
                            />
                          </div>
                        ) : (
                          country.name
                        )}
                      </TableCell>

                      <TableCell>
                        {isEditing ? (
                          <div className="relative">
                            <Hash className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                            <Input
                              type="text"
                              value={editData.code || ""}
                              onChange={(e) => handleChange("code", e.target.value)}
                              className="pl-10 uppercase"
                              maxLength={3}
                            />
                          </div>
                        ) : (
                          country.code
                        )}
                      </TableCell>

                      <TableCell>
                        {isEditing ? (
                          <div className="relative">
                            <Phone className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                            <Input
                              type="number"
                              value={editData.dial || ""}
                              onChange={(e) =>
                                handleChange("dial", Number(e.target.value))
                              }
                              className="pl-10"
                            />
                          </div>
                        ) : (
                          `+${country.dial}`
                        )}
                      </TableCell>

                      <TableCell>
                        {isEditing ? (
                          <Select
                            value={editData.active ? "active" : "inactive"}
                            onValueChange={(value) =>
                              handleChange("active", value === "active")
                            }
                          >
                            <SelectTrigger className="w-[120px]">
                              <SelectValue placeholder="Status" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="active">Active</SelectItem>
                              <SelectItem value="inactive">Inactive</SelectItem>
                            </SelectContent>
                          </Select>
                        ) : country.active ? (
                          <Badge variant="default">Active</Badge>
                        ) : (
                          <Badge variant="secondary">Inactive</Badge>
                        )}
                      </TableCell>

                      <TableCell className="text-right">
                        <div className="flex gap-2 justify-end">
                          {isEditing ? (
                            <>
                              <Button
                                size="icon"
                                variant="outline"
                                onClick={() => saveEdit(country._id)}
                                title="Save"
                              >
                                <Check className="w-4 h-4" />
                              </Button>
                              <Button
                                size="icon"
                                variant="destructive"
                                onClick={cancelEdit}
                                title="Cancel"
                              >
                                <X className="w-4 h-4" />
                              </Button>
                            </>
                          ) : (
                            <>
                              <Button
                                size="icon"
                                variant="outline"
                                onClick={() => startEdit(country)}
                                title="Edit"
                              >
                                <Pencil className="w-4 h-4" />
                              </Button>
                              <Button
                                size="icon"
                                variant="destructive"
                                onClick={() => openDeleteDialog(country._id)}
                                title="Delete"
                              >
                                <Trash2 className="w-4 h-4" />
                              </Button>
                            </>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. This will permanently delete the country{" "}
              {countryToDelete && countries.find(c => c._id === countryToDelete)?.name}.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
