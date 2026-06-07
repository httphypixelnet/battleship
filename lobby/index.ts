import type {ServerWebSocket} from "bun";

process.env.HTTP_PROXY = "http://localhost:5559";
process.env.HTTPS_PROXY = "http://localhost:5559";

interface WebSocketData {
    type: "control" | "relay-client" | "relay-server";
    gameId?: string;
    hostName?: string;
    address?: string;
    clientId?: string;
}

const games = new Map<string, {
    hostName: string;
    address: string;
    controlWs: ServerWebSocket<WebSocketData>;
    status: "open" | "playing";
}>();

const pendingClients = new Map<string, ServerWebSocket<WebSocketData>>();
const middleware = async (req: Request) => {
    const url = new URL(req.url);
    console.log(`Fetching ${url.pathname}`);
    if (url.pathname === "/shake" && req.method === "POST") {
        const json = await req.json()
    }
    if (url.pathname === "/games" && req.method === "GET") {
        const openGames = Array.from(games.entries())
            .filter(([_, game]) => game.status === "open")
            .map(([gameId, game]) => ({
                gameId,
                hostName: game.hostName,
                address: game.address,
            }));
        console.log(openGames);
        return Response.json(openGames);
    }

    if (url.pathname === "/control") {
        const gameId = url.searchParams.get("gameId");
        const hostName = url.searchParams.get("hostName");
        const address = url.searchParams.get("address");
        if (!gameId || !hostName || !address) {
            return new Response("Missing parameters", {status: 400});
        }
        if (server.upgrade(req, {
            data: {type: "control", gameId, hostName, address}
        })) {
            return;
        }
        return new Response("Upgrade failed", {status: 500});
    }

    if (url.pathname === "/relay") {
        const gameId = url.searchParams.get("gameId");
        const role = url.searchParams.get("role");
        if (!gameId || !role) {
            return new Response("Missing parameters", {status: 400});
        }

        if (role === "client") {
            if (server.upgrade(req, {
                data: {type: "relay-client", gameId}
            })) {
                return;
            }
        } else if (role === "server") {
            const clientId = url.searchParams.get("clientId");
            if (!clientId) {
                return new Response("Missing clientId", {status: 400});
            }
            if (server.upgrade(req, {
                data: {type: "relay-server", gameId, clientId}
            })) {
                return;
            }
        }
        return new Response("Upgrade failed", {status: 500});
    }

    return new Response("Not Found", {status: 404});
}

const server = Bun.serve<WebSocketData>({
    port: process.env.PORT ? parseInt(process.env.PORT) : 25565,
    development: true,
    async fetch(req, server) {
        const res = await middleware(req);
        res?.headers.set("Access-Control-Allow-Origin", "*");
        return res;
    },
    websocket: {
        open(ws) {
            console.log("Opened websocket")
            const data = ws.data;
            if (data.type === "control") {
                const gameId = data.gameId!;
                games.set(gameId, {
                    hostName: data.hostName!,
                    address: data.address!,
                    controlWs: ws,
                    status: "open",
                });
                console.log("hihi")
            } else if (data.type === "relay-client") {
                const gameId = data.gameId!;
                const controlGame = games.get(gameId);
                if (!controlGame) {
                    ws.close(4001, "Game not found");
                    return;
                }
                const clientId = crypto.randomUUID();
                data.clientId = clientId;
                pendingClients.set(clientId, ws);

                controlGame.controlWs.send(JSON.stringify({
                    type: "CONNECT_RELAY",
                    clientId,
                }));
            } else if (data.type === "relay-server") {
                const clientId = data.clientId!;
                const clientWs = pendingClients.get(clientId);
                if (!clientWs) {
                    ws.close(4002, "Client not found or timed out");
                    return;
                }
                pendingClients.delete(clientId);

                (ws as any).peer = clientWs;
                (clientWs as any).peer = ws;

                const gameId = data.gameId!;
                const game = games.get(gameId);
                if (game) {
                    game.status = "playing";
                }
            }
        },
        message(ws, message) {
            const peer = (ws as any).peer;
            if (peer && peer.readyState === 1) {
                peer.send(message);
            }
        },
        close(ws, code, reason) {
            const data = ws.data;
            if (data.type === "control") {
                const gameId = data.gameId!;
                games.delete(gameId);
            } else if (data.type === "relay-client") {
                if (data.clientId) {
                    pendingClients.delete(data.clientId);
                }
                const peer = (ws as any).peer;
                if (peer) {
                    peer.close(code, reason);
                }
            } else if (data.type === "relay-server") {
                const peer = (ws as any).peer;
                if (peer) {
                    peer.close(code, reason);
                }
            }
        }
    }
});