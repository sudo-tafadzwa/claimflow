"use client";

import { useEffect, useState } from "react";
import { userManager } from "@/lib/auth";

export default function Callback() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    userManager()
      .signinRedirectCallback()
      .then(() => window.location.replace("/"))
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <main className="page">
      {error ? <p className="error">Sign-in failed: {error}</p> : <p className="muted">Signing you in…</p>}
    </main>
  );
}
