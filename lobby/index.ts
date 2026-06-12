import type {Server, ServerWebSocket} from "bun";

process.env.HTTP_PROXY = "http://localhost:5559";
process.env.HTTPS_PROXY = "http://localhost:5559";

interface d1 {
    type: "control" | "relay-client" | "relay-server";
    gameId?: string;
    hostName?: string;
    address?: string;
    clientId?: string;
}
interface d2 {
    type: "games";
    clientId?: string;
}
type WebSocketData = d1 | d2
// const debugRelay = (process.env.DEBUG_RELAY ?? "false").toLowerCase() === "true";
function log(msg: string): void {
    if (true) {
        console.log(`[RelayDebug][Lobby] ${msg}`);
    }
}
interface game  {
    hostName: string;
    address: string;
    controlWs: ServerWebSocket<WebSocketData>;
    status: "open" | "playing";
}

export function startLobbyServer(port = process.env.PORT ? parseInt(process.env.PORT) : 25565): Server<WebSocketData> {
    const games = new Map<string, game>();
    const subscribers: Map<string, ServerWebSocket<WebSocketData>> = new Map();
    const oldSet = games.set.bind(games);
    const oldDelete = games.delete.bind(games);
    games.set = (key: string, value: game) => {
        log("sending games: " + JSON.stringify(value))
        const notification = { ...value };
        delete (notification as any).controlWs;
        subscribers.forEach((v) => {
            v.send(JSON.stringify({ type: "games", game: notification }));
        });
        return oldSet(key, value);
    };
    games.delete = (key: string): boolean => {
        const id = key;
        subscribers.forEach((v) => {
            if (v.readyState === 1) {
                v.send(JSON.stringify({ type: "games", delete: id }));
            }
        });
        return oldDelete(key);
    }



    const pendingClients = new Map<string, ServerWebSocket<WebSocketData>>();
    let server: Server<WebSocketData>;

    const middleware = async (req: Request) => {
        const url = new URL(req.url);
        if (url.pathname === "/games" && req.method === "GET") {
            if (server.upgrade(req, {
                data: {
                    type: "games",
                    clientId: url.searchParams.get("clientId")!
                }
            }))
                return
            return new Response("Upgrade failed", {status:500})
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

    server = Bun.serve<WebSocketData>({
        port,
        development: true,
        async fetch(req) {
            const res = await middleware(req);
            res?.headers.set("Access-Control-Allow-Origin", "*");
            return res;
        },
        websocket: {
            open(ws) {
                const data = ws.data;
                if ("gameId" in data) {
                    log(`open type=${data.type} gameId=${data.gameId ?? "-"} clientId=${data.clientId ?? "-"}`)
                }
                if (data.type === "control") {
                    const gameId = data.gameId!;
                    games.set(gameId, {
                        hostName: data.hostName!,
                        address: data.address!,
                        controlWs: ws,
                        status: "open",
                    });
                    log(gameId)
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
                    log(`relay-client waiting gameId=${gameId} clientId=${clientId}`)
                } else if (data.type === "relay-server") {
                    const clientId = data.clientId!;
                    const clientWs = pendingClients.get(clientId);
                    if (!clientWs) {
                        log(`relay-server missing client clientId=${clientId}`)
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
                        const notification = { ...game };
                        delete (notification as any).controlWs;
                        subscribers.forEach((v) => {
                            if (v.readyState === 1) {
                                v.send(JSON.stringify({ type: "games", game: notification }));
                            }
                        });
                    }
                    log(`relay pair established gameId=${gameId} clientId=${clientId}`)
                }
                else if (data.type === "games") {
                    const openGames = Array.from(games.entries())
                        .filter(([_, game]) => game.status === "open")
                        .map(([gameId, game]) => ({
                            gameId,
                            hostName: game.hostName,
                            address: game.address,
                        }));
                    ws.send(JSON.stringify({
                        type: "games",
                        init: openGames
                    }));
                    subscribers.set(data.clientId!, ws);
                }
            },
            message(ws, message) {
                const peer = (ws as any).peer;
                const data = ws.data;
                if ("gameId" in data) {
                    log(`message from=${data.type} gameId=${data.gameId ?? "-"} clientId=${data.clientId ?? "-"} bytes=${message.toString().length} peerOpen=${peer?.readyState === 1}`)
                }
                if (peer && peer.readyState === 1) {
                    peer.send(message);
                } else {
                    log(`drop message from=${data.type} reason=no-open-peer`)
                }
            },
            close(ws, code, reason) {
                const data = ws.data;
                if ("gameId" in data) {
                    log(`close type=${data.type} gameId=${data.gameId ?? "-"} clientId=${data.clientId ?? "-"} code=${code} reason='${reason}'`)
                }
                if (data.type === "control") {
                    const gameId = data.gameId!;
                    games.delete(gameId);
                } else if (data.type === "relay-client") {
                    if (data.clientId) {
                        pendingClients.delete(data.clientId);
                    }
                    const peer = (ws as any).peer;
                    if (peer) {
                        log(`close relay-client -> closing peer code=${code}`)
                        peer.close(code, reason);
                    }
                } else if (data.type === "relay-server") {
                    const peer = (ws as any).peer;
                    if (peer) {
                        log(`close relay-server -> closing peer code=${code}`)
                        peer.close(code, reason);
                    }
                }
            }
        }
    });

    return server;
}

if (import.meta.main) {
    startLobbyServer();
}
