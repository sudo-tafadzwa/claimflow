import type { Metadata } from "next";
import "./globals.css";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import { SessionProvider } from "@/components/session";

export const metadata: Metadata = {
  title: "ClaimFlow",
  description: "Insurance claims orchestration on Camunda 8, with AI document intake and human review.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <SessionProvider>{children}</SessionProvider>
      </body>
    </html>
  );
}
