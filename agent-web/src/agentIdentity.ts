/** Persist agent identity across queue → call navigation. */
const KEY = 'vb.agent.identity';
const NAME_KEY = 'vb.agent.name';

export function getAgentIdentity(): string {
  let id = sessionStorage.getItem(KEY);
  if (!id) {
    id = `agent-${crypto.randomUUID().slice(0, 8)}`;
    sessionStorage.setItem(KEY, id);
  }
  return id;
}

export function getAgentDisplayName(): string {
  return sessionStorage.getItem(NAME_KEY) ?? 'TuanNT10';
}

export function setAgentDisplayName(name: string) {
  sessionStorage.setItem(NAME_KEY, name);
}

export function initialsFromName(name: string | null | undefined): string {
  if (!name?.trim()) return '?';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function formatWaitDuration(createdAt: string): string {
  const ms = Date.now() - new Date(createdAt).getTime();
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
