"use client";

import { GameState } from "@/lib/websocket";

interface SuspectsViewProps {
  gameState: GameState;
  isHost: boolean;
  onConfirm: () => void;
}

export default function SuspectsView({
  gameState,
  isHost,
  onConfirm,
}: SuspectsViewProps) {
  const players = gameState.players.filter((p) => !p.isEliminated);

  return (
    <div className="flex flex-col items-center animate-fade-in">
      <div className="w-full max-w-lg">
        <div className="text-center mb-6">
          <div className="text-5xl mb-3">🕵️</div>
          <h2 className="text-2xl font-bold text-crime-accent">المشتبه فيهم</h2>
          <p className="text-gray-400 text-sm mt-2">
            اتعرفوا على الشخصيات اللي في الموضوع
          </p>
        </div>

        {/* Suspects grid */}
        <div className="space-y-3 mb-8">
          {players.map((player) => (
            <div
              key={player.id}
              className="bg-crime-primary p-4 rounded-xl border border-crime-light hover:border-crime-accent/50 transition-all"
            >
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-full bg-crime-accent/20 border-2 border-crime-accent flex items-center justify-center flex-shrink-0">
                  <span className="text-crime-accent text-lg font-bold">
                    {player.name.charAt(0)}
                  </span>
                </div>
                <div className="flex-1">
                  <p className="font-bold text-white text-lg">{player.name}</p>
                  {player.characterDescription && (
                    <p className="text-gray-400 text-sm mt-1">
                      {player.characterDescription}
                    </p>
                  )}
                  {player.suspicionReason && (
                    <p className="text-red-400/80 text-xs mt-1 flex items-center gap-1">
                      <span>⚠️</span> {player.suspicionReason}
                    </p>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Continue button (host only) */}
        {isHost ? (
          <button
            onClick={onConfirm}
            className="w-full bg-crime-accent hover:bg-red-600 text-white font-bold py-4 rounded-lg text-xl transition-all"
          >
            كمّل → ابدأ اللعبة
          </button>
        ) : (
          <p className="text-center text-gray-400 animate-pulse">
            مستنيين الهوست يكمّل...
          </p>
        )}
      </div>
    </div>
  );
}
