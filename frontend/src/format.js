const rupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export const money = (amount) => rupees.format(Number(amount));

/** The API sends LocalDateTime strings with up to 9 fractional digits; browsers only reliably parse 3. */
export const parseLocal = (value) => new Date(String(value).replace(/(\.\d{3})\d+/, '$1'));

export const formatShow = (value) =>
  parseLocal(value).toLocaleString('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });

export const formatClock = (totalSeconds) => {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
};

/** One key per payment attempt; the server uses it to make retries safe. */
export const newIdempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
