'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Calendar, MessageSquare, Code, Zap } from "lucide-react";

export default function Changelog() {
  const changelogEntries = [
        {
      date: "2025-10-03",
      version: "v1.2.0",
      title: "Double 91 Dialcode Fix",
      description: "Fixed issues with double 91 dialcodes in SMS messages",
      features: [
        "Double 91 dialcode issues fixed",

      ],
      icon: MessageSquare
    },
    {
      date: "2025-09-07",
      version: "v1.2.0",
      title: "SMS Template Generator",
      description: "Added AI-powered SMS to template generator with OpenAI integration",
      features: [
        "Convert SMS messages to regex-compatible templates",
        "Support for {otp}, {time}, {random}, and {any} placeholders",
        "Example messages for quick testing",
        "Sonner toast notifications",
        "Sidebar navigation integration"
      ],
      icon: MessageSquare
    },
    {
      date: "2025-09-06",
      version: "v1.1.0",
      title: "Dashboard Enhancements",
      description: "Improved dashboard functionality and UI",
      features: [
        "Enhanced number management",
        "Better service tracking",
        "UI optimizations"
      ],
      icon: Zap
    },
    {
      date: "2025-09-05",
      version: "v1.0.0",
      title: "Initial Release",
      description: "Launch of CattySMS platform",
      features: [
        "Number activation system",
        "Service management",
        "Basic dashboard",
        "User authentication"
      ],
      icon: Code
    }
  ];

  return (
    <div className="container mx-auto p-6 space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl md:text-3xl font-bold">Changelog</h1>
        <p className="text-sm md:text-base text-muted-foreground">
          Latest features and updates to the CattySMS platform
        </p>
      </div>

      <div className="space-y-8">
        {changelogEntries.map((entry, index) => {
          const IconComponent = entry.icon;
          return (
            <Card key={index} className="border-l-4 border-l-primary">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <IconComponent className="h-6 w-6 text-primary" />
                    <CardTitle className="text-xl">{entry.title}</CardTitle>
                  </div>
                  <div className="text-right text-sm text-muted-foreground">
                    <div className="flex items-center gap-2">
                      <Calendar className="h-4 w-4" />
                      <span>{entry.date}</span>
                    </div>
                    <div className="font-mono text-xs">{entry.version}</div>
                  </div>
                </div>
                <CardDescription>{entry.description}</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-2">
                  {entry.features.map((feature, featureIndex) => (
                    <li key={featureIndex} className="flex items-start gap-2">
                      <div className="h-1.5 w-1.5 rounded-full bg-primary mt-2 flex-shrink-0" />
                      <span className="text-sm">{feature}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
