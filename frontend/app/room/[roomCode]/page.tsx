"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  gameWebSocket,
  GameState,
  PlayerPackage,
  WebSocketMessage,
} from "@/lib/websocket";
import LobbyView from "@/components/LobbyView";
import CrimeBriefingView from "@/components/CrimeBriefingView";
import SuspectsView from "@/components/SuspectsView";
import GameView from "@/components/GameView";
import GameOverView from "@/components/GameOverView";

export default function GameRoom() {
  const params = useParams();
  const router = useRouter();
  const roomCode = params.roomCode as string;

  const [gameState, setGameState] = useState<GameState | null>(null);
  const [playerPackage, setPlayerPackage] = useState<PlayerPackage | null>(
    null,
  );
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState("");
  const [gameOverData, setGameOverData] = useState<any>(null);
  const [voteUpdate, setVoteUpdate] = useState<{
    votedCount: number;
    totalPlayers: number;
  } | null>(null);
  const [eliminationReveal, setEliminationReveal] = useState<any>(null);
  const [showPlayerPanel, setShowPlayerPanel] = useState(false);
  const [showBriefing, setShowBriefing] = useState(false);

  const playerId =
    typeof window !== "undefined" ? sessionStorage.getItem("playerId") : null;
  const playerName =
    typeof window !== "undefined" ? sessionStorage.getItem("playerName") : null;

  const isHost = gameState
    ? gameState.players.some((p) => p.id === playerId && p.isHost)
    : typeof window !== "undefined"
      ? sessionStorage.getItem("isHost") === "true"
      : false;

  const handleMessage = useCallback((msg: WebSocketMessage) => {
    switch (msg.type) {
      case "GAME_STATE":
        const state = msg.payload as GameState;
        setGameState(state);
        // Reset vote update when leaving voting
        if (state.phase !== "VOTING") {
          setVoteUpdate(null);
        }
        // Clear elimination reveal when moving to next round
        if (state.phase === "GAME_ROUND") {
          setEliminationReveal(null);
        }
        break;
      case "YOUR_PACKAGE":
        setPlayerPackage(msg.payload as PlayerPackage);
        break;
      case "VOTE_UPDATE":
        setVoteUpdate(msg.payload);
        break;
      case "VOTE_RESULT":
        // Vote results are shown inline in GameView
        break;
      case "ELIMINATION_REVEAL":
        setEliminationReveal(msg.payload);
        break;
      case "GAME_OVER":
        setGameOverData(msg.payload);
        break;
      case "KICKED":
        gameWebSocket.disconnect();
        sessionStorage.clear();
        router.push("/?kicked=true");
        break;
      case "ROOM_RESET":
        setPlayerPackage(null);
        setGameOverData(null);
        setVoteUpdate(null);
        setEliminationReveal(null);
        setError("");
        break;
      case "ERROR":
        setError(msg.message || "حصلت مشكلة");
        setTimeout(() => setError(""), 5000);
        break;
    }
  }, []);

  useEffect(() => {
    if (!playerId) {
      router.push("/");
      return;
    }

    gameWebSocket
      .connect(roomCode, playerId)
      .then(() => setConnected(true))
      .catch((err) => {
        setError("مقدرش يتوصل بالسيرفر");
        console.error(err);
      });

    const unsubscribe = gameWebSocket.onMessage(handleMessage);

    return () => {
      unsubscribe();
      gameWebSocket.disconnect();
    };
  }, [roomCode, playerId, router, handleMessage]);

  if (!playerId) return null;

  if (!connected) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[80vh]">
        <div className="animate-pulse text-2xl text-crime-accent">
          بيتوصل...
        </div>
      </div>
    );
  }

  // Phases that use the persistent GameView
  const isGamePhase =
    gameState &&
    (gameState.phase === "GAME_ROUND" ||
      gameState.phase === "VOTING" ||
      gameState.phase === "TIE_BREAK" ||
      gameState.phase === "ELIMINATION_REVEAL");

  return (
    <div className="min-h-[80vh]">
      {/* Error banner */}
      {error && (
        <div className="bg-red-900/50 border border-red-500 text-red-200 px-4 py-3 rounded-lg mb-4 animate-fade-in">
          {error}
        </div>
      )}

      {/* Room code header */}
      {gameState && (
        <div className="flex justify-between items-center mb-6">
          <div>
            <span className="text-gray-400 text-sm">كود الأوضة:</span>
            <span
              className="text-crime-accent font-mono text-lg mr-2 tracking-wider"
              style={{ direction: "ltr" }}
            >
              {roomCode}
            </span>
            {playerName && (
              <span className="text-gray-300 text-sm mr-3">
                👤 {playerName}
              </span>
            )}
          </div>
          <div className="flex items-center gap-3">
            {gameState.currentRound > 0 && (
              <span className="text-sm text-gray-400">
                الجولة {gameState.currentRound}
              </span>
            )}
            {isHost &&
              gameState.phase !== "LOBBY" &&
              gameState.phase !== "GENERATING_SCENARIO" && (
                <button
                  onClick={() => setShowPlayerPanel((v) => !v)}
                  className="text-xs bg-crime-primary hover:bg-crime-light border border-crime-light px-3 py-1 rounded transition-all"
                >
                  👥 اللعيبة
                </button>
              )}
          </div>
        </div>
      )}

      {/* Host in-game player management panel */}
      {showPlayerPanel &&
        isHost &&
        gameState &&
        gameState.phase !== "LOBBY" && (
          <div className="w-full max-w-sm mb-4 bg-crime-primary border border-crime-light rounded-xl p-4 animate-fade-in">
            <h3 className="text-sm font-bold text-crime-accent mb-3">
              إدارة اللعيبة
            </h3>
            <div className="space-y-2">
              {gameState.players
                .filter((p) => p.id !== playerId)
                .map((player) => (
                  <div
                    key={player.id}
                    className="flex items-center justify-between bg-crime-dark px-3 py-2 rounded-lg"
                  >
                    <span
                      className={`text-sm ${player.isEliminated ? "text-gray-500 line-through" : "text-gray-200"}`}
                    >
                      {player.name}
                    </span>
                    {!player.isEliminated && (
                      <button
                        onClick={() => {
                          gameWebSocket.kickPlayer(player.id);
                          setShowPlayerPanel(false);
                        }}
                        className="text-xs bg-red-800 hover:bg-red-700 px-2 py-1 rounded text-red-200 transition-all"
                      >
                        شيل
                      </button>
                    )}
                  </div>
                ))}
            </div>
          </div>
        )}

      {/* Persistent crime briefing (collapsible, visible after briefing phase) */}
      {gameState?.crimeBriefing && isGamePhase && (
        <div className="w-full mb-4">
          <button
            onClick={() => setShowBriefing((v) => !v)}
            className="w-full flex items-center justify-between bg-crime-primary/80 hover:bg-crime-primary border border-crime-light/50 px-4 py-2 rounded-lg transition-all text-sm"
          >
            <span className="text-crime-accent font-bold">📋 ملخص الجريمة</span>
            <span className="text-gray-400 text-xs">
              {showBriefing ? "▲ خبّي" : "▼ وري"}
            </span>
          </button>
          {showBriefing && (
            <div className="bg-crime-primary border border-t-0 border-crime-light/50 rounded-b-lg px-4 py-3 animate-fade-in">
              <p className="text-gray-200 text-sm leading-relaxed whitespace-pre-wrap">
                {gameState.crimeBriefing}
              </p>
            </div>
          )}
        </div>
      )}

      {/* ===== Phase-specific views ===== */}

      {(!gameState || gameState.phase === "LOBBY") && (
        <LobbyView
          gameState={gameState}
          isHost={isHost}
          playerId={playerId}
          onStartGame={() => gameWebSocket.startGame()}
          onKickPlayer={(targetId) => gameWebSocket.kickPlayer(targetId)}
        />
      )}

      {gameState?.phase === "GENERATING_SCENARIO" && (
        <div className="flex flex-col items-center justify-center min-h-[60vh]">
          <div className="animate-pulse-glow w-24 h-24 rounded-full bg-crime-accent/20 flex items-center justify-center mb-8">
            <span className="text-crime-accent text-4xl">🔍</span>
          </div>
          <p className="text-xl text-gray-300">
            {gameState.message || "بيتعمل السيناريو..."}
          </p>
        </div>
      )}

      {gameState?.phase === "CRIME_BRIEFING" && (
        <CrimeBriefingView
          gameState={gameState}
          isHost={isHost}
          onContinue={() => gameWebSocket.confirmBriefing()}
        />
      )}

      {gameState?.phase === "SUSPECTS" && (
        <SuspectsView
          gameState={gameState}
          isHost={isHost}
          onConfirm={() => gameWebSocket.confirmSuspects()}
        />
      )}

      {/* Persistent game page: GAME_ROUND, VOTING, TIE_BREAK, ELIMINATION_REVEAL */}
      {isGamePhase && (
        <GameView
          gameState={gameState}
          playerPackage={playerPackage}
          playerId={playerId}
          playerName={playerName}
          isHost={isHost}
          voteUpdate={voteUpdate}
          eliminationReveal={eliminationReveal}
        />
      )}

      {gameState?.phase === "GAME_OVER" && gameOverData && (
        <GameOverView
          voteResult={gameOverData}
          gameState={gameState}
          isHost={isHost}
          onReplay={() => gameWebSocket.replayGame()}
        />
      )}
    </div>
  );
}
