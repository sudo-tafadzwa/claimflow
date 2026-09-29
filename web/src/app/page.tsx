"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "@/components/session";
import { signIn } from "@/lib/auth";

export default function Home() {
  const { me, loading, isStaff } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (me) router.replace(isStaff ? "/inbox" : "/claims");
  }, [me, isStaff, router]);

  if (loading || me) return <main className="page muted">Loading…</main>;

  return (
    <main className="hero">
      <h1>Claims, from document to payout</h1>
      <p>
        Upload a claim document. AI reads it and says how sure it is, rules decide the route, people handle the
        exceptions, and every payout needs two people to release.
      </p>
      <p className="small muted">
        A portfolio reference project on fictional data. Demo users are listed in the README (password “demo”).
      </p>
      <p style={{ marginTop: 24 }}>
        <button className="btn btn-primary" onClick={() => signIn()}>Sign in to the demo</button>
      </p>
    </main>
  );
}
