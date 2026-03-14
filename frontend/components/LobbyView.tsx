"use client";

import { useState } from "react";
import { GameState } from "@/lib/websocket";

interface LobbyViewProps {
  gameState: GameState | null;
  isHost: boolean;
  playerId: string;
  onStartGame: (criminalCount: number) => void;
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
  const canStart = players.length >= 3; // min 3 players (including host)
  const [criminalCount, setCriminalCount] = useState(1);

  const maxCriminals = Math.max(1, Math.floor(players.length / 2));

  // Available criminal count options based on current player count
  const criminalOptions = Array.from(
    { length: maxCriminals },
    (_, i) => i + 1,
  );

  // Clamp selected criminal count if players leave
  const effectiveCriminalCount = Math.min(criminalCount, maxCriminals);

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
          {Array.from({ length: Math.max(0, 3 - players.length) }).map(
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

      {/* Criminal count selector (host only, shown when enough players) */}
      {isHost && canStart && (
        <div className="w-full max-w-md mb-6">
          <label className="block text-sm font-medium mb-3 text-gray-300">
            🔪 عدد المجرمين
          </label>
          <div className="flex gap-2">
            {criminalOptions.map((count) => (
              <button
                key={count}
                type="button"
                onClick={() => setCriminalCount(count)}
                className={`flex-1 py-3 rounded-lg font-bold text-lg transition-all border-2 ${
                  effectiveCriminalCount === count
                    ? "bg-crime-accent/20 border-crime-accent text-crime-accent"
                    : "bg-crime-primary border-crime-light text-gray-400 hover:border-crime-accent/50"
                }`}
              >
                {count}
              </button>
            ))}
          </div>
          <p className="text-xs text-gray-500 mt-2">
            {players.length} لاعبين في اللعبة — ينفع لحد {maxCriminals} مجرم
          </p>
        </div>
      )}

      {/* Waiting message */}
      {!canStart && (
        <p className="text-gray-400 mb-4">
          لازم يبقى فيه ٣ على الأقل عشان نبدأ
        </p>
      )}

      {/* Start button (host only) */}
      {isHost && (
        <button
          onClick={() => onStartGame(effectiveCriminalCount)}
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
