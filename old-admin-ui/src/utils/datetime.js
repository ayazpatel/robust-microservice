// Simple date-time formatting utilities

const pad = (n) => n.toString().padStart(2, '0');

/**
 * Format a date/time string into YYYY-MM-DD HH:MM:SS (local time)
 * Accepts ISO string, epoch ms, Date, or falsy.
 */
export const formatDateTime = (value) => {
  if (!value) return '-';
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  const y = d.getFullYear();
  const m = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const h = pad(d.getHours());
  const min = pad(d.getMinutes());
  const s = pad(d.getSeconds());
  return `${y}-${m}-${day} ${h}:${min}:${s}`;
};

export default { formatDateTime };
