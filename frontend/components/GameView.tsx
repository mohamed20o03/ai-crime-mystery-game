"use client";

import { useState, useEffect } from "react";
import { GameState, PlayerPackage, gameWebSocket } from "@/lib/websocket";

interface GameViewProps {
  gameState: GameState;
  playerPackage: PlayerPackage | null;
  playerId: string;
  playerName: string | null;
  isHost: boolean;
  voteUpdate: { votedCount: number; totalPlayers: number } | null;
  eliminationReveal: any;
}

export default function GameView({
  gameState,
  playerPackage,
  playerId,
  playerName,
  isHost,
  voteUpdate,
  eliminationReveal,
}: GameViewProps) {
  const [selectedVoteTarget, setSelectedVoteTarget] = useState("");
  const [hasVoted, setHasVoted] = useState(false);

  const phase = gameState.phase;

  // Reset vote state when entering a new voting phase (fixes round 2+ voting)
  useEffect(() => {
    if (phase === "VOTING") {
      setHasVoted(false);
      setSelectedVoteTarget("");
    }
  }, [phase]);

  const activePlayers = gameState.players.filter(
    (p) => !p.isEliminated && p.id !== playerId,
  );

  const handleVote = () => {
    if (selectedVoteTarget) {
      gameWebSocket.castVote(selectedVoteTarget);
      setHasVoted(true);
    }
  };

  const isCriminal = playerPackage?.role === "CRIMINAL";

  // Check if the current player has been eliminated
  const isEliminated =
    gameState.players.find((p) => p.id === playerId)?.isEliminated ?? false;

  return (
    <div className="flex flex-col items-center animate-fade-in">
      <div className="w-full max-w-lg space-y-6">
        {/* ===== ROLE SECTION ===== */}
        <div
          className={`p-5 rounded-xl border-2 ${
            isCriminal
              ? "bg-red-950/40 border-red-700"
              : "bg-blue-950/40 border-blue-700"
          }`}
        >
          <div className="flex items-center gap-3 mb-3">
            <span className="text-3xl">{isCriminal ? "🔪" : "🔍"}</span>
            <div>
              <h3
                className={`text-xl font-bold ${isCriminal ? "text-red-400" : "text-blue-400"}`}
              >
                {isCriminal ? "إنت المجرم" : "إنت بريء"}
              </h3>
              {playerName && (
                <p className="text-white font-semibold text-base">
                  {playerName}
                </p>
              )}
              {playerPackage?.characterDescription && (
                <p className="text-gray-400 text-sm">
                  {playerPackage.characterDescription}
                </p>
              )}
            </div>
          </div>

          {/* Fellow criminals */}
          {isCriminal &&
            playerPackage?.fellowCriminals &&
            playerPackage.fellowCriminals.length > 0 && (
              <div className="bg-red-900/30 px-3 py-2 rounded-lg mt-2 mb-3">
                <p className="text-red-300 text-sm">
                  🤝 شركاك في الجريمة:{" "}
                  <span className="font-bold">
                    {playerPackage.fellowCriminals.join("، ")}
                  </span>
                </p>
              </div>
            )}
        </div>

        {/* ===== MANDATORY TESTIMONY ===== */}
        {playerPackage?.mustSayTestimony && (
          <div className="bg-amber-950/30 border border-amber-600/50 p-4 rounded-xl">
            <h3 className="text-amber-400 font-bold mb-2 flex items-center gap-2">
              <span>📜</span> شهادتك اللي لازم تقولها
            </h3>
            <p className="text-gray-200 leading-relaxed text-sm bg-crime-dark/50 p-3 rounded-lg">
              {playerPackage.mustSayTestimony}
            </p>
            <p className="text-amber-500/70 text-xs mt-2">
              لازم تقول الشهادة دي بصوت عالي وإنتو بتتكلموا
            </p>
          </div>
        )}

        {/* ===== ACCUMULATED CLUES ===== */}
        <div className="bg-crime-primary p-4 rounded-xl border border-crime-light">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-crime-accent font-bold flex items-center gap-2">
              <span>🔎</span> الأدلة بتاعتك
            </h3>
            {playerPackage && (
              <span className="text-xs text-gray-400 bg-crime-dark px-2 py-1 rounded">
                {playerPackage.privateClues?.length || 0} /{" "}
                {playerPackage.totalClueCount}
              </span>
            )}
          </div>

          {!playerPackage?.privateClues ||
          playerPackage.privateClues.length === 0 ? (
            <p className="text-gray-500 text-sm text-center py-4">
              {gameState.currentRound === 0
                ? "الدليل الأول هيظهر لما الجولة الأولى تبدأ"
                : "لسه مفيش أدلة"}
            </p>
          ) : (
            <div className="space-y-2">
              {playerPackage.privateClues.map((clue, index) => (
                <div
                  key={index}
                  className="bg-crime-dark p-3 rounded-lg border-r-4 border-crime-accent/60"
                >
                  <div className="flex items-start gap-2">
                    <span className="text-crime-accent text-xs font-bold mt-0.5 bg-crime-accent/20 px-1.5 py-0.5 rounded">
                      {index + 1}
                    </span>
                    <p className="text-gray-200 text-sm leading-relaxed">
                      {clue}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* ===== HOST CONTROLS: Start Round / Start Vote (always visible for host) ===== */}
        {phase === "GAME_ROUND" && isHost && (
          <div className="space-y-3">
            {isEliminated && (
              <div className="bg-gray-800/50 p-3 rounded-xl border border-gray-600/50 text-center mb-2">
                <p className="text-gray-400 text-sm">
                  👻 إنت اتشلت — بس لسه بتدير اللعبة
                </p>
              </div>
            )}

            {!gameState.roundClueRevealed && (
              <button
                onClick={() => gameWebSocket.startRound()}
                className="w-full bg-crime-accent hover:bg-red-600 text-white font-bold py-4 rounded-lg text-xl transition-all animate-pulse"
              >
                🎯 ابدأ الجولة {(gameState.currentRound || 0) + 1} — كشف دليل
              </button>
            )}

            {gameState.roundClueRevealed && (
              <button
                onClick={() => gameWebSocket.startVoting()}
                className="w-full bg-amber-700 hover:bg-amber-600 text-white font-bold py-4 rounded-lg text-xl transition-all"
              >
                🗳️ ابدأ التصويت
              </button>
            )}

            {gameState.message && gameState.roundClueRevealed && (
              <p className="text-center text-green-400 text-sm animate-fade-in">
                {gameState.message}
              </p>
            )}
          </div>
        )}

        {/* ===== GHOST MESSAGE (eliminated non-host) ===== */}
        {phase === "GAME_ROUND" && isEliminated && !isHost && (
          <div className="bg-gray-800/50 p-4 rounded-xl border border-gray-600/50 text-center">
            <p className="text-gray-400">
              👻 إنت اتشلت — بتتفرج على باقي اللعيبة وبتشارك أدلتك
            </p>
          </div>
        )}

        {/* ===== NON-HOST, NON-ELIMINATED WAITING TEXT ===== */}
        {phase === "GAME_ROUND" && !isEliminated && !isHost && (
          <div className="space-y-3">
            {!gameState.roundClueRevealed && (
              <p className="text-center text-gray-400 animate-pulse">
                {gameState.currentRound === 0
                  ? "مستنيين الهوست يبدأ الجولة الأولى..."
                  : `مستنيين الهوست يبدأ الجولة ${(gameState.currentRound || 0) + 1}...`}
              </p>
            )}

            {gameState.roundClueRevealed && (
              <p className="text-center text-gray-400 text-sm">
                اتكلموا عن الأدلة... الهوست هيبدأ التصويت لما تبقوا جاهزين
              </p>
            )}

            {gameState.message && gameState.roundClueRevealed && (
              <p className="text-center text-green-400 text-sm animate-fade-in">
                {gameState.message}
              </p>
            )}
          </div>
        )}

        {/* ===== VOTING PHASE ===== */}
        {phase === "VOTING" && isEliminated && (
          <div className="bg-gray-800/50 p-5 rounded-xl border-2 border-gray-600/50 text-center">
            <p className="text-2xl mb-2">👻</p>
            <p className="text-gray-400 font-bold">إنت اتشلت — بتتفرج بس</p>
            {voteUpdate && (
              <p className="text-gray-500 text-sm mt-2">
                {voteUpdate.votedCount} / {voteUpdate.totalPlayers} صوّتوا
              </p>
            )}
          </div>
        )}

        {phase === "VOTING" && !isEliminated && (
          <div className="bg-crime-primary p-5 rounded-xl border-2 border-amber-600/50">
            <h3 className="text-xl font-bold text-amber-400 text-center mb-4">
              🗳️ وقت التصويت
            </h3>
            <p className="text-gray-400 text-center text-sm mb-4">
              صوّت لمين تفتكر إنه المجرم
            </p>

            {!hasVoted ? (
              <div className="space-y-2">
                {activePlayers.map((player) => (
                  <button
                    key={player.id}
                    onClick={() => setSelectedVoteTarget(player.id)}
                    className={`w-full flex items-center justify-between p-3 rounded-lg border-2 transition-all ${
                      selectedVoteTarget === player.id
                        ? "bg-crime-accent/20 border-crime-accent"
                        : "bg-crime-dark border-crime-light hover:border-crime-accent/50"
                    }`}
                  >
                    <span className="font-medium">{player.name}</span>
                    {selectedVoteTarget === player.id && (
                      <span className="text-crime-accent">✓</span>
                    )}
                  </button>
                ))}

                <button
                  onClick={handleVote}
                  disabled={!selectedVoteTarget}
                  className="w-full bg-crime-accent hover:bg-red-600 disabled:bg-gray-600 text-white font-bold py-3 rounded-lg text-lg transition-all mt-3"
                >
                  أكّد التصويت
                </button>
              </div>
            ) : (
              <div className="text-center py-6">
                <p className="text-xl text-green-400">✓ اتصوّت</p>
                <p className="text-gray-400 mt-2 animate-pulse">
                  مستنيين باقي اللعيبة...
                  {voteUpdate && (
                    <span className="block text-xs mt-1">
                      {voteUpdate.votedCount} / {voteUpdate.totalPlayers}
                    </span>
                  )}
                </p>
              </div>
            )}
          </div>
        )}

        {/* ===== TIE BREAK PHASE ===== */}
        {phase === "TIE_BREAK" && (
          <div className="bg-yellow-950/30 border-2 border-yellow-600 p-5 rounded-xl">
            <h3 className="text-xl font-bold text-yellow-400 text-center mb-4">
              ⚖️ تعادل في التصويت!
            </h3>
            <p className="text-gray-300 text-center text-sm mb-4">
              {isHost
                ? "اختار مين فيهم يتشال"
                : "الهوست بيختار مين فيهم يتشال..."}
            </p>

            {isHost && gameState.tiedPlayerIds && (
              <div className="space-y-2">
                {gameState.tiedPlayerIds.map((tiedId) => {
                  const player = gameState.players.find((p) => p.id === tiedId);
                  return player ? (
                    <button
                      key={tiedId}
                      onClick={() => gameWebSocket.resolveTieBreak(tiedId)}
                      className="w-full bg-yellow-900/50 hover:bg-yellow-800/50 border border-yellow-600 text-white font-bold py-3 rounded-lg transition-all"
                    >
                      شيل {player.name}
                    </button>
                  ) : null;
                })}
              </div>
            )}

            {!isHost && (
              <p className="text-center text-gray-400 animate-pulse">
                مستنيين الهوست يقرر...
              </p>
            )}
          </div>
        )}

        {/* ===== ELIMINATION REVEAL ===== */}
        {phase === "ELIMINATION_REVEAL" && (
          <div className="space-y-4">
            {eliminationReveal && (
              <div
                className={`p-6 rounded-xl text-center border-2 ${
                  eliminationReveal.wasCriminal
                    ? "bg-green-900/30 border-green-500"
                    : "bg-red-900/30 border-red-500"
                }`}
              >
                <div className="text-4xl mb-3">
                  {eliminationReveal.wasCriminal ? "🎉" : "💀"}
                </div>
                <p className="text-2xl font-bold mb-2">
                  اتشال: {eliminationReveal.eliminatedPlayer}
                </p>
                <p
                  className={`text-xl ${
                    eliminationReveal.wasCriminal
                      ? "text-green-400"
                      : "text-red-400"
                  }`}
                >
                  {eliminationReveal.wasCriminal
                    ? "كان مجرم! 🎯"
                    : "كان بريء 💀"}
                </p>
                {eliminationReveal.characterDescription && (
                  <p className="text-gray-400 text-sm mt-2">
                    {eliminationReveal.characterDescription}
                  </p>
                )}
              </div>
            )}

            {!eliminationReveal && gameState.eliminatedPlayerName && (
              <div
                className={`p-6 rounded-xl text-center border-2 ${
                  gameState.eliminatedPlayerRole === "CRIMINAL"
                    ? "bg-green-900/30 border-green-500"
                    : "bg-red-900/30 border-red-500"
                }`}
              >
                <p className="text-2xl font-bold mb-2">
                  اتشال: {gameState.eliminatedPlayerName}
                </p>
                <p
                  className={`text-xl ${
                    gameState.eliminatedPlayerRole === "CRIMINAL"
                      ? "text-green-400"
                      : "text-red-400"
                  }`}
                >
                  {gameState.eliminatedPlayerRole === "CRIMINAL"
                    ? "كان مجرم! 🎯"
                    : "كان بريء 💀"}
                </p>
              </div>
            )}

            {isHost ? (
              <button
                onClick={() => {
                  setHasVoted(false);
                  setSelectedVoteTarget("");
                  gameWebSocket.continueAfterElimination();
                }}
                className="w-full bg-crime-accent hover:bg-red-600 text-white font-bold py-4 rounded-lg text-xl transition-all"
              >
                كمّل ←
              </button>
            ) : (
              <p className="text-center text-gray-400 animate-pulse">
                مستنيين الهوست يكمّل...
              </p>
            )}
          </div>
        )}

        {/* ===== ELIMINATED PLAYERS ===== */}
        {gameState.players.some((p) => p.isEliminated) && (
          <div className="bg-crime-primary/50 p-4 rounded-xl border border-crime-light/30">
            <h3 className="text-gray-400 text-sm font-bold mb-2">
              🪦 اللعيبة اللي اتشالوا
            </h3>
            <div className="space-y-1">
              {gameState.players
                .filter((p) => p.isEliminated)
                .map((player) => (
                  <div
                    key={player.id}
                    className="flex items-center gap-2 text-gray-500 text-sm"
                  >
                    <span>💀</span>
                    <span className="line-through">{player.name}</span>
                  </div>
                ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
