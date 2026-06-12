import { afterAll, beforeAll, describe, expect, it } from "bun:test";
import { startLobbyServer } from "./index";
import type { Server } from "bun";

// ── Helpers ────────────────────────────────────────────────────────────────

function ws(url: string): Promise<WebSocket> {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        socket.onopen = () => resolve(socket);
        socket.onerror = (e) => reject(e);
        setTimeout(() => reject(new Error(`ws connect timeout: ${url}`)), 5_000);
    });
}

function buffered(socket: WebSocket) {
    const buf: string[] = [];
    let resolveNext: ((v: string) => void) | null = null;
    socket.addEventListener("message", (e) => {
        const msg = e.data as string;
        if (resolveNext) {
            resolveNext(msg);
            resolveNext = null;
        } else {
            buf.push(msg);
        }
    });
    return {
        next(timeout = 3_000): Promise<string> {
            if (buf.length > 0) return Promise.resolve(buf.shift()!);
            return new Promise((resolve, reject) => {
                resolveNext = resolve;
                setTimeout(() => reject(new Error("next() timeout")), timeout);
            });
        },
    };
}

function waitMs(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
}

// ── Suite ──────────────────────────────────────────────────────────────────

const PORT = 25566;
let server: Server;

beforeAll(() => {
    server = startLobbyServer(PORT);
});

afterAll(() => {
    server.stop(true);
});

describe("/games subscription", () => {
    it("receives an init message with an empty list on connect", async () => {
        const socket = await ws(`ws://localhost:${PORT}/games`);
        const buf = buffered(socket);
        const raw = await buf.next();
        const msg = JSON.parse(raw);
        expect(msg.type).toBe("games");
        expect(msg.init).toEqual([]);
        socket.close();
    });

    it("receives a game added notification when a control connects", async () => {
        const gamesSocket = await ws(`ws://localhost:${PORT}/games`);
        const buf = buffered(gamesSocket);
        const initRaw = await buf.next();
        const init = JSON.parse(initRaw);
        expect(init.type).toBe("games");

        const ctl = await ws(
            `ws://localhost:${PORT}/control?gameId=g1&hostName=HostA&address=10.0.0.1:25567`
        );
        const raw = await buf.next();
        const msg = JSON.parse(raw);
        expect(msg.type).toBe("games");
        expect(msg.game.hostName).toBe("HostA");
        expect(msg.game.address).toBe("10.0.0.1:25567");
        expect(msg.game.status).toBe("open");
        ctl.close();
        gamesSocket.close();
    });
});

describe("/control", () => {
    it("rejects missing parameters with 400", async () => {
        const resp = await fetch(`http://localhost:${PORT}/control?gameId=x&hostName=y`);
        expect(resp.status).toBe(400);
    });

    it("upgrades and registers the game", async () => {
        const ctl = await ws(
            `ws://localhost:${PORT}/control?gameId=ctrl1&hostName=CtrlHost&address=10.0.0.2:25567`
        );

        const gs = await ws(`ws://localhost:${PORT}/games`);
        const buf = buffered(gs);
        const initRaw = await buf.next();
        const init = JSON.parse(initRaw);
        expect(init.type).toBe("games");
        expect(init.init!.length).toBeGreaterThanOrEqual(1);
        const match = init.init!.find((g: any) => g.gameId === "ctrl1");
        expect(match).toBeDefined();
        expect(match!.hostName).toBe("CtrlHost");

        gs.close();
        ctl.close();
    });
});

describe("/relay client-server pairing", () => {
    it("pairs a relay-client with a relay-server and forwards messages", async () => {
        const ctl = await ws(
            `ws://localhost:${PORT}/control?gameId=relay1&hostName=RelayHost&address=10.0.0.3:25567`
        );

        const client = await ws(`ws://localhost:${PORT}/relay?role=client&gameId=relay1`);

        const ctlRaw = await new Promise<string>((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error("timeout waiting for CONNECT_RELAY")), 3_000);
            ctl.onmessage = (e) => { clearTimeout(timer); resolve(e.data as string); };
        });
        const ctlMsg = JSON.parse(ctlRaw);
        expect(ctlMsg.type).toBe("CONNECT_RELAY");
        const clientId = ctlMsg.clientId;

        const srv = await ws(
            `ws://localhost:${PORT}/relay?role=server&gameId=relay1&clientId=${clientId}`
        );

        const payload = JSON.stringify({ type: "HELLO", payload: "{}" });
        client.send(payload);
        const srvRaw = await new Promise<string>((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error("timeout")), 3_000);
            srv.onmessage = (e) => { clearTimeout(timer); resolve(e.data as string); };
        });
        expect(srvRaw).toBe(payload);

        const payload2 = JSON.stringify({ type: "PONG", payload: "{}" });
        srv.send(payload2);
        const clientRaw = await new Promise<string>((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error("timeout")), 3_000);
            client.onmessage = (e) => { clearTimeout(timer); resolve(e.data as string); };
        });
        expect(clientRaw).toBe(payload2);

        srv.close();
        client.close();
        ctl.close();
    });

    it("closes relay-client with 4001 when gameId does not exist", async () => {
        const client = await ws(`ws://localhost:${PORT}/relay?role=client&gameId=nonexistent`);
        const { code, reason } = await new Promise<{ code: number; reason: string }>((resolve) => {
            client.onclose = (e) => resolve({ code: e.code, reason: e.reason });
        });
        expect(code).toBe(4001);
        expect(reason).toBe("Game not found");
    });

    it("closes relay-server with 4002 when clientId is not found", async () => {
        const srv = await ws(
            `ws://localhost:${PORT}/relay?role=server&gameId=relay1&clientId=no-such-client`
        );
        const { code, reason } = await new Promise<{ code: number; reason: string }>((resolve) => {
            srv.onclose = (e) => resolve({ code: e.code, reason: e.reason });
        });
        expect(code).toBe(4002);
        expect(reason).toBe("Client not found or timed out");
    });

    it("rejects relay with unknown role", async () => {
        const resp = await fetch(`http://localhost:${PORT}/relay?gameId=x&role=unknown`);
        expect(resp.status).toBe(500);
    });
});

describe("status transition to playing", () => {
    it("marks the game as playing after a relay pair is established", async () => {
        const ctl = await ws(
            `ws://localhost:${PORT}/control?gameId=status1&hostName=StatusHost&address=10.0.0.5:25567`
        );

        const gs = await ws(`ws://localhost:${PORT}/games`);
        const buf = buffered(gs);
        await buf.next(); // discard init

        const client = await ws(`ws://localhost:${PORT}/relay?role=client&gameId=status1`);
        const ctlRaw = await new Promise<string>((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error("timeout")), 3_000);
            ctl.onmessage = (e) => { clearTimeout(timer); resolve(e.data as string); };
        });
        const clientId = JSON.parse(ctlRaw).clientId;

        await ws(
            `ws://localhost:${PORT}/relay?role=server&gameId=status1&clientId=${clientId}`
        );

        const raw = await buf.next();
        const msg = JSON.parse(raw);
        expect(msg.type).toBe("games");
        if (msg.game) {
            expect(msg.game.status).toBe("playing");
        }

        gs.close();
        client.close();
        ctl.close();
    });
});

describe("cleanup on close", () => {
    it("removes control game when control WS closes", async () => {
        const ctl = await ws(
            `ws://localhost:${PORT}/control?gameId=clean1&hostName=CleanHost&address=10.0.0.6:25567`
        );

        const gs = await ws(`ws://localhost:${PORT}/games`);
        const buf = buffered(gs);
        const initRaw = await buf.next();
        const init = JSON.parse(initRaw);
        expect(init.type).toBe("games");
        const game = init.init!.find((g: any) => g.gameId === "clean1");
        expect(game).toBeDefined();

        ctl.close();
        await waitMs(300);

        const gs2 = await ws(`ws://localhost:${PORT}/games`);
        const buf2 = buffered(gs2);
        const initRaw2 = await buf2.next();
        const init2 = JSON.parse(initRaw2);
        const game2 = init2.init!.find((g: any) => g.gameId === "clean1");
        expect(game2).toBeUndefined();

        gs.close();
        gs2.close();
    });
});

describe("relay missing parameters", () => {
    it("returns 400 when gameId is missing on /relay", async () => {
        const resp = await fetch(`http://localhost:${PORT}/relay?role=client`);
        expect(resp.status).toBe(400);
    });

    it("returns 400 when role is missing on /relay", async () => {
        const resp = await fetch(`http://localhost:${PORT}/relay?gameId=x`);
        expect(resp.status).toBe(400);
    });

    it("returns 400 when clientId is missing for relay-server", async () => {
        const resp = await fetch(`http://localhost:${PORT}/relay?role=server&gameId=x`);
        expect(resp.status).toBe(400);
    });
});

describe("404 for unknown paths", () => {
    it("returns 404 for /unknown", async () => {
        const resp = await fetch(`http://localhost:${PORT}/unknown`);
        expect(resp.status).toBe(404);
    });
});
