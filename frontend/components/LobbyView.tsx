"use client";

import { GameState } from "@/lib/websocket";

interface LobbyViewProps {
  gameState: GameState | null;
  isHost: boolean;
  playerId: string;
  onStartGame: () => void;
  onKickPlayer: (targetPlayerId: string) => void;
}

export default function LobbyView({
  gameState,
  isHost,
  playerId,
  onStartGame,
  onKickPlayer,
}: LobbyViewProps) {
  const players = gameState?.players || [];
  const canStart = players.length >= 2;

  return (
    <div className="flex flex-col items-center">
      <h2 className="text-2xl font-bold mb-8 text-crime-accent">
        أوضة الاستنى
      </h2>

      {/* Player list */}
      <div className="w-full max-w-md mb-8">
        <h3 className="text-lg font-medium mb-4 text-gray-300">
          اللعيبة ({players.length}/8)
        </h3>
        <div className="space-y-2">
          {players.map((player) => (
            <div
              key={player.id}
              className="flex items-center justify-between bg-crime-primary px-4 py-3 rounded-lg border border-crime-light"
            >
              <span className="font-medium">{player.name}</span>
              <div className="flex items-center gap-2">
                {player.isHost && (
                  <span className="text-xs bg-crime-accent px-2 py-1 rounded">
                    الهوست
                  </span>
                )}
                {isHost && player.id !== playerId && (
                  <button
                    onClick={() => onKickPlayer(player.id)}
                    className="text-xs bg-red-800 hover:bg-red-700 px-2 py-1 rounded text-red-200 transition-all"
                    title="شيل اللاعب"
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>
          ))}

          {/* Empty slots */}
          {Array.from({ length: Math.max(0, 2 - players.length) }).map(
            (_, i) => (
              <div
                key={`empty-${i}`}
                className="flex items-center justify-center bg-crime-primary/30 px-4 py-3 rounded-lg border border-crime-light/30 border-dashed"
              >
                <span className="text-gray-500">مستنيين لاعب...</span>
              </div>
            ),
          )}
        </div>
      </div>

      {/* Waiting message */}
      {!canStart && (
        <p className="text-gray-400 mb-4">
          لازم يبقى فيه لاعبين على الأقل عشان نبدأ
        </p>
      )}

      {/* Start button (host only) */}
      {isHost && (
        <button
          onClick={onStartGame}
          disabled={!canStart}
          className="bg-crime-accent hover:bg-red-600 disabled:bg-gray-600 disabled:cursor-not-allowed text-white font-bold py-4 px-12 rounded-lg text-xl transition-all"
        >
          ابدأ اللعبة
        </button>
      )}

      {!isHost && canStart && (
        <p className="text-gray-400 animate-pulse">
          مستنيين الهوست يبدأ اللعبة...
        </p>
      )}
    </div>
  );
}
