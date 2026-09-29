"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

/**
 * Draws the deployed BPMN model (served by the API) and highlights where this claim is.
 * bpmn-js touches the DOM, so it is loaded only in the browser.
 */
export function ProcessView({ elementId }: { elementId: string }) {
  const container = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let viewer: { destroy(): void } | null = null;
    let cancelled = false;
    (async () => {
      try {
        const [{ default: NavigatedViewer }, xml] = await Promise.all([
          import("bpmn-js/lib/NavigatedViewer"),
          api.processModel(),
        ]);
        if (cancelled || !container.current) return;
        const v = new NavigatedViewer({ container: container.current });
        viewer = v;
        await v.importXML(xml);
        const canvas = v.get("canvas") as {
          zoom(level?: string | number): number;
          addMarker(id: string, marker: string): void;
          scrollToElement(el: unknown, padding?: number): void;
        };
        const registry = v.get("elementRegistry") as { get(id: string): unknown };
        const element = registry.get(elementId);
        if (element) canvas.addMarker(elementId, "current");
        // Wait for layout so the container has its real size, fit the model, then bring the current step into view.
        requestAnimationFrame(() => {
          if (cancelled) return;
          canvas.zoom("fit-viewport");
          // A long process fitted to a narrow card is unreadable; keep a readable minimum scale.
          if (canvas.zoom() < 0.6) canvas.zoom(0.6);
          if (element) canvas.scrollToElement(element, 120);
        });
      } catch (e) {
        setError((e as Error).message);
      }
    })();
    return () => {
      cancelled = true;
      viewer?.destroy();
    };
  }, [elementId]);

  if (error) return <p className="small muted">Process diagram unavailable: {error}</p>;
  return <div ref={container} className="process" aria-label="Claim process diagram" />;
}
