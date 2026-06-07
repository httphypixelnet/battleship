import {
  deserializePacket,
  serializePacket,
  type Packet,
} from "./types";
import {sameVersion, VERSION} from "./buildConstants.ts";

type PendingRequest = {
  resolve: (p: Packet) => void;
  reject: (err: Error) => void;
};

export class ServerConnection {
  private readonly queue: PendingRequest[] = [];
  private stateUpdateListener: ((p: Packet) => void) | null = null;
  private closeListener: (() => void) | null = null;
  private readonly ws: WebSocket;

  constructor(ws: WebSocket) {
    this.ws = ws;
    ws.addEventListener("message", (ev) => this.handleMessage(ev.data as string));
    ws.addEventListener("close", () => {
      for (const pending of this.queue) {
        pending.reject(new Error("Connection closed"));
      }
      this.queue.length = 0;
      this.closeListener?.();
    });
    ws.addEventListener("error", () => {
      for (const pending of this.queue) {
        pending.reject(new Error("WebSocket error"));
      }
      this.queue.length = 0;
    });
    ws.send(serializePacket({ type: "VERSION", payload: VERSION }));
  }

  private handleMessage(raw: string): void {
    const packet = deserializePacket(raw);

    if (packet.type === "VERSION" && sameVersion(packet.payload!) == 0) {
      alert("Server version mismatch! Try a different server or report this bug.")
      this.ws.close(67, "Version mismatch");
    }

    if (packet.type === "STATE_UPDATE") {
      this.stateUpdateListener?.(packet);
      return;
    }

    const pending = this.queue.shift();
    if (pending) {
      pending.resolve(packet);
    } else {
      console.warn("[ServerConnection] Received unexpected packet:", packet);
    }
  }

  /**
   * Send a request packet and wait for the response packet.
   */
  request(type: string, payload?: unknown): Promise<Packet> {
    return new Promise((resolve, reject) => {
      if (this.ws.readyState !== WebSocket.OPEN) {
        reject(new Error("WebSocket is not open"));
        return;
      }
      this.queue.push({ resolve, reject });
      const packet: Packet = { type, payload: payload !== undefined ? JSON.stringify(payload) : null };
      this.ws.send(serializePacket(packet));
    });
  }

  onStateUpdate(listener: ((p: Packet) => void) | null): void {
    this.stateUpdateListener = listener;
  }

  onClose(listener: (() => void) | null): void {
    this.closeListener = listener;
  }

  close(): void {
    this.ws.close();
  }

  get isOpen(): boolean {
    return this.ws.readyState === WebSocket.OPEN;
  }
}
