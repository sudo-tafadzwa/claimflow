"use client";

import { UserManager, WebStorageStateStore, type User } from "oidc-client-ts";
import { config } from "./config";

let manager: UserManager | null = null;

/** Authorization code flow with PKCE against Keycloak. Tokens stay in session storage, never in cookies. */
export function userManager(): UserManager {
  if (!manager) {
    manager = new UserManager({
      authority: config.oidcAuthority,
      client_id: config.oidcClientId,
      redirect_uri: `${window.location.origin}/callback`,
      post_logout_redirect_uri: window.location.origin,
      response_type: "code",
      scope: "openid profile email",
      automaticSilentRenew: true,
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    });
  }
  return manager;
}

export async function currentUser(): Promise<User | null> {
  const user = await userManager().getUser();
  return user && !user.expired ? user : null;
}

export const signIn = () => userManager().signinRedirect();
export const signOut = () => userManager().signoutRedirect();
