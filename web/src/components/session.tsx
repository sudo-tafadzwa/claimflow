"use client";

import Link from "next/link";
import { createContext, useContext, useEffect, useState } from "react";
import { api, type Me } from "@/lib/api";
import { currentUser, signIn, signOut } from "@/lib/auth";

const STAFF = ["adjuster", "senior-adjuster", "finance-maker", "finance-checker", "admin"];

interface Session {
  me: Me | null;
  loading: boolean;
  isStaff: boolean;
}

const SessionContext = createContext<Session>({ me: null, loading: true, isStaff: false });

export const useSession = () => useContext(SessionContext);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (window.location.pathname === "/callback") {
      setLoading(false);
      return;
    }
    currentUser()
      .then((user) => (user ? api.me().then(setMe) : null))
      .catch(() => setMe(null))
      .finally(() => setLoading(false));
  }, []);

  const isStaff = !!me?.roles.some((r) => STAFF.includes(r));
  return (
    <SessionContext.Provider value={{ me, loading, isStaff }}>
      <TopBar me={me} isStaff={isStaff} />
      {children}
    </SessionContext.Provider>
  );
}

function TopBar({ me, isStaff }: { me: Me | null; isStaff: boolean }) {
  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link href="/" className="brand">Claim<span>Flow</span></Link>
        <nav className="nav">
          {me && !isStaff && <Link href="/claims">My claims</Link>}
          {isStaff && <Link href="/inbox">Inbox</Link>}
          {isStaff && <Link href="/claims/all">All claims</Link>}
        </nav>
        <div className="who">
          {me ? (
            <>
              <span>{me.username} · {me.roles.join(", ")}</span>
              <button className="btn" onClick={() => signOut()}>Sign out</button>
            </>
          ) : (
            <button className="btn btn-primary" onClick={() => signIn()}>Sign in</button>
          )}
        </div>
      </div>
    </header>
  );
}
