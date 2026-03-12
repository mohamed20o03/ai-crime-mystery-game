"use client";

import { GameState } from "@/lib/websocket";

interface CrimeBriefingViewProps {
  gameState: GameState;
  isHost: boolean;
  onContinue: () => void;
}

export default function CrimeBriefingView({
  gameState,
  isHost,
  onContinue,
}: CrimeBriefingViewProps) {
  return (
    <div className="flex flex-col items-center animate-fade-in">
      <div className="w-full max-w-lg">
        {/* Header */}
        <div className="text-center mb-6">
          <div className="text-5xl mb-3">🔎</div>
          <h2 className="text-2xl font-bold text-crime-accent">حصلت جريمة!</h2>
        </div>

        {/* Setting */}
        {gameState.setting && (
          <div className="bg-crime-primary/60 px-4 py-3 rounded-lg border border-crime-light/40 mb-4 text-center">
            <span className="text-gray-300 text-sm">{gameState.setting}</span>
          </div>
        )}

        {/* Crime briefing */}
        <div className="bg-crime-primary p-6 rounded-xl border border-crime-accent/30 mb-6">
          <p className="leading-relaxed text-gray-200 text-lg">
            {gameState.crimeBriefing}
          </p>
        </div>

        {/* Instructions */}
        <p className="text-gray-400 text-center text-sm mb-6">
          اقروا الكلام ده مع بعض. وبعدها كل لاعب هياخد معلوماته الخاصة.
        </p>

        {/* Continue button (host only) */}
        {isHost ? (
          <button
            onClick={onContinue}
            className="w-full bg-crime-accent hover:bg-red-600 text-white font-bold py-4 rounded-lg text-xl transition-all"
          >
            كمّل → المشتبه فيهم
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
