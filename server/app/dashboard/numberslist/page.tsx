"use client";

import { useState, useEffect } from "react";
import { getCookie } from "@/utils/cookie";
import { formatDistanceToNow } from "date-fns";
import { Lock, Unlock, Trash2, Signal, Search, Phone, RefreshCw, BarChart3, Wifi, Globe, Hash } from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

export default function NumbersGrid() {
  const [numbers, setNumbers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [numberToDelete, setNumberToDelete] = useState<string | null>(null);
  const itemsPerPage = 10;

  const fetchNumbers = async () => {
    try {
      setLoading(true);
      const token = getCookie("token");
      const res = await fetch("/api/numbers/list", {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json();
      if (data.success) setNumbers(data.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNumbers();
  }, []);

  const handleDelete = async () => {
    if (!numberToDelete) return;
    try {
      const token = getCookie("token");
      const res = await fetch(`/api/numbers/delete/${numberToDelete}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json();
      if (data.success) {
        setNumbers((prev) => prev.filter((n) => n._id !== numberToDelete));
        setDeleteDialogOpen(false);
        setNumberToDelete(null);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const openDeleteDialog = (id: string) => {
    setNumberToDelete(id);
    setDeleteDialogOpen(true);
  };

  const renderSignal = (sig: number) => {
    if (!sig || sig === 0)
      return <Badge variant="outline">No Signal</Badge>;
    const variant = sig < 8 ? "destructive" : sig < 12 ? "secondary" : "default";
    return (
      <Badge variant={variant} className="gap-1">
        <Signal size={16} />
        {sig}
      </Badge>
    );
  };

  // Filtering + Sorting + Pagination
  const filteredNumbers = numbers
    .filter((n) => n.number.toString().includes(search))
    .filter((n) => {
      if (filter === "active") return n.active;
      if (filter === "inactive") return !n.active;
      return true;
    })
    .sort((a, b) => (a.active === b.active ? 0 : a.active ? -1 : 1));

  const totalPages = Math.ceil(filteredNumbers.length / itemsPerPage);
  const paginatedNumbers = filteredNumbers.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  // Counters
  const totalCount = numbers.length;
  const activeCount = numbers.filter((n) => n.active).length;
  const inactiveCount = numbers.filter((n) => !n.active).length;

  return (
    <div className="space-y-4 sm:space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl md:text-3xl font-bold tracking-tight flex items-center gap-2">
            <Phone className="h-5 w-5 sm:h-6 sm:w-6 md:h-8 md:w-8" />
            SIM Numbers
          </h1>
          <p className="text-sm md:text-base text-muted-foreground">
            Manage and monitor all your SIM numbers
          </p>
        </div>
        <Button variant="outline" onClick={fetchNumbers} disabled={loading} className="w-full sm:w-auto">
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Hash className="h-4 w-4" />
              Total Numbers
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold flex items-center gap-2">
              <Phone className="h-6 w-6 text-primary" />
              {totalCount}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Wifi className="h-4 w-4" />
              Active Numbers
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-primary flex items-center gap-2">
              <Wifi className="h-6 w-6" />
              {activeCount}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Signal className="h-4 w-4" />
              Inactive Numbers
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-destructive flex items-center gap-2">
              <Signal className="h-6 w-6" />
              {inactiveCount}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Search and Filter */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
          <Input
            placeholder="Search numbers..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
            className="pl-10"
          />
        </div>
        <Select
          value={filter}
          onValueChange={(value) => {
            setFilter(value);
            setCurrentPage(1);
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Filter status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Numbers</SelectItem>
            <SelectItem value="active">Active Only</SelectItem>
            <SelectItem value="inactive">Inactive Only</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Country</TableHead>
                <TableHead>Operator</TableHead>
                <TableHead>Port</TableHead>
                <TableHead>Signal</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last Rotation</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-8">
                    <div className="animate-pulse text-muted-foreground">Loading numbers...</div>
                  </TableCell>
                </TableRow>
              ) : paginatedNumbers.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-8">
                    <div className="text-muted-foreground">No numbers found</div>
                  </TableCell>
                </TableRow>
              ) : (
                paginatedNumbers.map((n) => (
                  <TableRow key={n._id}>
                    <TableCell className="font-medium">{n.number}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {n.countryid?.flag && (
                          <img
                            src={n.countryid.flag}
                            alt={n.countryid.name}
                            className="w-5 h-5 rounded-full"
                          />
                        )}
                        <span>{n.countryid?.name || "N/A"}</span>
                      </div>
                    </TableCell>
                    <TableCell>{n.operator || "Unknown"}</TableCell>
                    <TableCell>{n.port || "-"}</TableCell>
                    <TableCell>{renderSignal(n.signal)}</TableCell>
                    <TableCell>
                      <Badge variant={n.active ? "default" : "destructive"}>
                        {n.active ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {n.lastRotation
                        ? formatDistanceToNow(new Date(n.lastRotation), {
                            addSuffix: true,
                          })
                        : "Never"}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => openDeleteDialog(n._id)}
                      >
                        <Trash2 className="h-4 w-4 mr-1" />
                        Delete
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      {/* Pagination */}
      {!loading && filteredNumbers.length > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            Showing {Math.min(filteredNumbers.length, (currentPage - 1) * itemsPerPage + 1)}-
            {Math.min(currentPage * itemsPerPage, filteredNumbers.length)} of{" "}
            {filteredNumbers.length} numbers
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

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. This will permanently delete the number{" "}
              {numberToDelete && numbers.find(n => n._id === numberToDelete)?.number}.
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
