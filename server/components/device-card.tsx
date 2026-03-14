"use client";

import React from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Smartphone,
  Battery,
  Wifi,
  Signal,
  Clock,
  MessageSquare,
  MoreHorizontal,
  Edit,
  Trash2,
  Power,
  PhoneForwarded,
  PhoneOff,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useRouter } from "next/navigation";

interface Device {
  _id: string;
  deviceId: string;
  name: string;
  status: "online" | "offline" | "error";
  lastSeen: string;
  batteryLevel?: number;
  isCharging: boolean;
  signalStrength: number;
  networkType: "wifi" | "mobile" | "none";
  deviceModel?: string;
  manufacturer?: string;
  sims: Array<{
    slot: number;
    phoneNumber?: string;
    carrier?: string;
    isActive: boolean;
    signalStrength?: number; // ← add this
    networkType?: string; // ← add this
    callForwardingActive?: boolean;
    callForwardingTo?: string;
  }>;
  recentMessages: number;
  timeSinceLastSeen: { minutes: number; hours: number; days: number };
}

interface DeviceCardProps {
  device: Device;
  onEdit?: (device: Device) => void;
  onDelete?: (device: Device) => void;
  onToggleStatus?: (device: Device) => void;
  // FIX: removed phoneNumber param — DeviceCard never knows the destination
  // number. The parent (DeviceList) collects it via the dialog before calling
  // sendCallForwardingCommand. DeviceCard only signals intent.
  onCallForwarding?: (
    deviceId: string,
    simSlot: number,
    action: "forward" | "deactivate",
    currentNumber?: string,
  ) => void;
}

export function DeviceCard({
  device,
  onEdit,
  onDelete,
  onToggleStatus,
  onCallForwarding,
}: DeviceCardProps) {
  const router = useRouter();

  const getStatusColor = (status: string) => {
    switch (status) {
      case "online":
        return "bg-green-500";
      case "offline":
        return "bg-gray-500";
      case "error":
        return "bg-red-500";
      default:
        return "bg-gray-500";
    }
  };

  const getStatusBadgeVariant = (
    status: string,
  ): "default" | "secondary" | "destructive" => {
    switch (status) {
      case "online":
        return "default";
      case "offline":
        return "secondary";
      case "error":
        return "destructive";
      default:
        return "secondary";
    }
  };

  const getBatteryColor = (level?: number) => {
    if (level === undefined || level === null) return "text-gray-400";
    if (level > 60) return "text-green-500";
    if (level > 20) return "text-yellow-500";
    return "text-red-500";
  };

  const getSignalBars = (strength: number) =>
    Array.from({ length: 4 }, (_, i) => (
      <div
        key={i}
        className={`w-1 rounded-full ${i < strength ? "bg-green-500" : "bg-gray-300"}`}
        style={{ height: `${(i + 1) * 3}px` }}
      />
    ));

  const formatTimeSince = ({
    minutes,
    hours,
    days,
  }: Device["timeSinceLastSeen"]) => {
    if (days > 0) return `${days} day${days > 1 ? "s" : ""} ago`;
    if (hours > 0) return `${hours} hour${hours > 1 ? "s" : ""} ago`;
    if (minutes > 0) return `${minutes} minute${minutes > 1 ? "s" : ""} ago`;
    return "Just now";
  };

  const getNetworkIcon = (type: string) => {
    switch (type) {
      case "wifi":
        return <Wifi className="h-4 w-4" />;
      case "mobile":
        return <Signal className="h-4 w-4" />;
      default:
        return <Signal className="h-4 w-4 opacity-30" />;
    }
  };

  const isOnline = device.status === "online";

  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div
              className={`w-3 h-3 rounded-full ${getStatusColor(device.status)}`}
            />
            <CardTitle className="text-lg">{device.name}</CardTitle>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {isOnline && onCallForwarding && device.sims[0] && (
                <>
                  <DropdownMenuItem
                    onClick={() =>
                      onCallForwarding(
                        device.deviceId,
                        0,
                        "forward",
                        device.sims[0].callForwardingTo,
                      )
                    }
                  >
                    <PhoneForwarded className="h-4 w-4 mr-2" />
                    {device.sims[0].callForwardingActive
                      ? "Edit SIM 1"
                      : "Forward SIM 1"}
                    {device.sims[0].callForwardingActive && (
                      <span className="ml-auto text-xs text-muted-foreground">
                        → {device.sims[0].callForwardingTo?.replace("+91 ", "")}
                      </span>
                    )}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() =>
                      onCallForwarding(device.deviceId, 0, "deactivate")
                    }
                    className="text-red-600"
                  >
                    <PhoneOff className="h-4 w-4 mr-2" />
                    Unforward SIM 1
                  </DropdownMenuItem>
                </>
              )}
              {isOnline &&
                onCallForwarding &&
                device.sims.length > 1 &&
                device.sims[1] && (
                  <>
                    <DropdownMenuItem
                      onClick={() =>
                        onCallForwarding(
                          device.deviceId,
                          1,
                          "forward",
                          device.sims[1].callForwardingTo,
                        )
                      }
                    >
                      <PhoneForwarded className="h-4 w-4 mr-2" />
                      {device.sims[1].callForwardingActive
                        ? "Edit SIM 2"
                        : "Forward SIM 2"}
                      {device.sims[1].callForwardingActive && (
                        <span className="ml-auto text-xs text-muted-foreground">
                          →{" "}
                          {device.sims[1].callForwardingTo?.replace("+91 ", "")}
                        </span>
                      )}
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={() =>
                        onCallForwarding(device.deviceId, 1, "deactivate")
                      }
                      className="text-red-600"
                    >
                      <PhoneOff className="h-4 w-4 mr-2" />
                      Unforward SIM 2
                    </DropdownMenuItem>
                  </>
                )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <div className="text-sm text-muted-foreground">
          ID: {device.deviceId.slice(-8)}
          {device.deviceModel && (
            <span className="ml-2">
              {device.manufacturer} {device.deviceModel}
            </span>
          )}
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {/* Status indicators */}
        <div className="flex items-center gap-4 text-sm">
          <div className="flex items-center gap-1">
            <Battery
              className={`h-4 w-4 ${getBatteryColor(device.batteryLevel)}`}
            />
            <span>{device.batteryLevel ?? "N/A"}%</span>
            {device.isCharging && (
              <div className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse" />
            )}
          </div>
          <div className="flex items-center gap-1">
            {getNetworkIcon(device.networkType)}
            <span className="capitalize">{device.networkType}</span>
          </div>
          <div className="flex items-end gap-0.5">
            {getSignalBars(device.signalStrength)}
          </div>
        </div>

        {/* SIM cards */}
        {device.sims.length > 0 && (
          <div className="space-y-2">
            <div className="text-xs font-medium text-muted-foreground">
              SIM cards
            </div>
            <div className="space-y-1">
              {device.sims.map((sim) => (
                <div
                  key={sim.slot}
                  className="flex items-center justify-between gap-2 p-2 rounded-md border bg-card"
                >
                  <div className="flex items-center gap-2">
                    <Badge
                      variant={sim.isActive ? "default" : "outline"}
                      className="text-xs"
                    >
                      SIM {sim.slot}
                      {sim.phoneNumber && `: ${sim.phoneNumber.slice(-4)}`}
                    </Badge>
                    {sim.callForwardingActive && (
                      <Badge
                        variant="secondary"
                        className="text-xs gap-1 cursor-pointer hover:bg-blue-100 transition-colors"
                        onClick={() =>
                          onCallForwarding?.(
                            device.deviceId,
                            sim.slot - 1,
                            "forward",
                            sim.callForwardingTo,
                          )
                        }
                      >
                        <PhoneForwarded className="h-3 w-3" />→{" "}
                        {sim.callForwardingTo || "Unknown"}
                      </Badge>
                    )}
                  </div>

                  {/* FIX: inline Forward/Disable buttons also go through
                      onCallForwarding without a phone number so DeviceList
                      opens the dialog for 'forward'. */}
                  {isOnline && sim.isActive && onCallForwarding && (
                    <div className="flex items-center gap-1">
                      {sim.callForwardingActive ? (
                        <>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 text-xs text-blue-600 hover:text-blue-700 hover:bg-blue-50"
                            onClick={() =>
                              onCallForwarding(
                                device.deviceId,
                                sim.slot - 1,
                                "forward",
                                sim.callForwardingTo,
                              )
                            }
                          >
                            <PhoneForwarded className="h-3 w-3 mr-1" />
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
                            onClick={() =>
                              onCallForwarding(
                                device.deviceId,
                                sim.slot - 1,
                                "deactivate",
                              )
                            }
                          >
                            <PhoneOff className="h-3 w-3 mr-1" />
                            Unforward
                          </Button>
                        </>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-7 text-xs text-blue-600 hover:text-blue-700 hover:bg-blue-50"
                          onClick={() =>
                            onCallForwarding(
                              device.deviceId,
                              sim.slot - 1,
                              "forward",
                            )
                          }
                        >
                          <PhoneForwarded className="h-3 w-3 mr-1" />
                          Forward
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Activity */}
        <div className="flex items-center justify-between text-sm">
          <div className="flex items-center gap-2">
            <MessageSquare className="h-4 w-4 text-muted-foreground" />
            <span>{device.recentMessages} messages (24h)</span>
          </div>
          <div className="flex items-center gap-1 text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span>{formatTimeSince(device.timeSinceLastSeen)}</span>
          </div>
        </div>

        {device.lastSeen && (
          <div className="text-xs text-muted-foreground">
            Last seen: {new Date(device.lastSeen).toLocaleString()}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
