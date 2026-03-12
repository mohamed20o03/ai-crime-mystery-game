import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

export type MessageHandler = (message: WebSocketMessage) => void;

export interface WebSocketMessage {
  type: string;
  payload?: any;
  message?: string;
}

export interface GameState {
  roomCode: string;
  phase: string;
  currentRound: number;
  players: PlayerInfo[];
  setting?: string;
  crimeBriefing?: string;
  roundClueRevealed?: boolean;
  tiedPlayerIds?: string[];
  eliminatedPlayerName?: string;
  eliminatedPlayerRole?: string;
  message?: string;
}

export interface PlayerInfo {
  id: string;
  name: string;
  characterDescription?: string;
  suspicionReason?: string;
  isHost: boolean;
  isEliminated: boolean;
  hasVoted: boolean;
}

export interface PlayerPackage {
  role: "CRIMINAL" | "INNOCENT";
  characterDescription?: string;
  mustSayTestimony: string;
  privateClues: string[];
  totalClueCount: number;
  fellowCriminals?: string[];
}

class GameWebSocket {
  private client: Client | null = null;
  private roomCode: string = "";
  private playerId: string = "";
  private messageHandlers: Set<MessageHandler> = new Set();
  private connected: boolean = false;

  connect(roomCode: string, playerId: string): Promise<void> {
    return new Promise((resolve, reject) => {
      this.roomCode = roomCode;
      this.playerId = playerId;

      this.client = new Client({
        webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log("WebSocket connected");
          this.connected = true;
          this.subscribeToRoom();
          this.sendJoin();
          resolve();
        },
        onStompError: (frame) => {
          console.error("STOMP error:", frame);
          reject(new Error("WebSocket connection failed"));
        },
        onDisconnect: () => {
          console.log("WebSocket disconnected");
          this.connected = false;
        },
      });

      this.client.activate();
    });
  }

  private subscribeToRoom() {
    if (!this.client || !this.connected) return;

    this.client.subscribe(
      `/topic/room/${this.roomCode}`,
      (message: IMessage) => {
        this.handleMessage(message);
      },
    );

    this.client.subscribe(
      `/topic/room/${this.roomCode}/player/${this.playerId}`,
      (message: IMessage) => {
        this.handleMessage(message);
      },
    );
  }

  private handleMessage(message: IMessage) {
    try {
      const parsed: WebSocketMessage = JSON.parse(message.body);
      this.messageHandlers.forEach((handler) => handler(parsed));
    } catch (e) {
      console.error("Error parsing message:", e);
    }
  }

  private sendJoin() {
    this.send(`/app/room/${this.roomCode}/join`, { playerId: this.playerId });
  }

  send(destination: string, body: any) {
    if (!this.client || !this.connected) {
      console.error("WebSocket not connected");
      return;
    }
    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }

  // Game actions
  startGame() {
    this.send(`/app/room/${this.roomCode}/start`, { playerId: this.playerId });
  }

  confirmBriefing() {
    this.send(`/app/room/${this.roomCode}/briefing/confirm`, {
      playerId: this.playerId,
    });
  }

  confirmSuspects() {
    this.send(`/app/room/${this.roomCode}/suspects/confirm`, {
      playerId: this.playerId,
    });
  }

  startRound() {
    this.send(`/app/room/${this.roomCode}/round/start`, {
      playerId: this.playerId,
    });
  }

  startVoting() {
    this.send(`/app/room/${this.roomCode}/vote/start`, {
      playerId: this.playerId,
    });
  }

  castVote(targetPlayerId: string) {
    this.send(`/app/room/${this.roomCode}/vote`, {
      playerId: this.playerId,
      targetPlayerId,
    });
  }

  resolveTieBreak(targetPlayerId: string) {
    this.send(`/app/room/${this.roomCode}/tiebreak/resolve`, {
      playerId: this.playerId,
      targetPlayerId,
    });
  }

  continueAfterElimination() {
    this.send(`/app/room/${this.roomCode}/elimination/continue`, {
      playerId: this.playerId,
    });
  }

  kickPlayer(targetPlayerId: string) {
    this.send(`/app/room/${this.roomCode}/kick`, {
      playerId: this.playerId,
      targetPlayerId,
    });
  }

  replayGame() {
    this.send(`/app/room/${this.roomCode}/replay`, {
      playerId: this.playerId,
    });
  }

  onMessage(handler: MessageHandler) {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }

  isConnected() {
    return this.connected;
  }

  getPlayerId() {
    return this.playerId;
  }
}

export const gameWebSocket = new GameWebSocket();
