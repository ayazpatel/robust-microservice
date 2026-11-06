// Per-socket send queue with backpressure handling.
// If ws.send() returns false → stop, resume on 'drain'.
export function makeSender({ softLimit = 256 * 1024, hardLimit = 2 * 1024 * 1024 } = {}) {
  return {
    q: [],
    sending: false,
    size() {
      let n = 0;
      for (const b of this.q) n += b.length;
      return n;
    },
    enqueue(ws, buf) {
      const queued = this.size();
      if (queued > hardLimit) {
        // HARD policy: drop new message (or drop oldest non-essential in your app)
        return;
      }
      if (queued > softLimit) {
        // SOFT: you can flag throttling here
        // console.warn("soft backpressure");
      }
      this.q.push(buf);
      this.flush(ws);
    },
    flush(ws) {
      if (this.sending) return;
      this.sending = true;
      while (this.q.length) {
        const ok = ws.send(this.q[0], true); // true=binary (JSON text is fine in binary frame)
        if (!ok) break;
        this.q.shift();
      }
      this.sending = false;
    }
  };
}
