"use client";

import { GameState } from "@/lib/websocket";
import Link from "next/link";

interface GameOverViewProps {
  voteResult: any;
  gameState: GameState;
  isHost: boolean;
  onReplay: () => void;
}

export default function GameOverView({
  voteResult,
  gameState,
  isHost,
  onReplay,
}: GameOverViewProps) {
  const isInnocentsWin = voteResult.winner === "innocents";

  return (
    <div className="flex flex-col items-center animate-fade-in">
      {/* Winner announcement */}
      <div
        className={`w-full max-w-lg text-center p-8 rounded-xl border-2 mb-8 ${
          isInnocentsWin
            ? "bg-green-900/30 border-green-500"
            : "bg-red-900/30 border-red-500"
        }`}
      >
        <div className="text-6xl mb-4">{isInnocentsWin ? "🎉" : "💀"}</div>
        <h1 className="text-3xl font-bold mb-2">
          {isInnocentsWin ? "الأبرياء كسبوا!" : "المجرمين كسبوا!"}
        </h1>
        <p className="text-gray-300">
          {isInnocentsWin
            ? "اتكشف كل المجرمين"
            : voteResult.reason || "الأبرياء مقدروش يكشفوا المجرمين"}
        </p>
      </div>

      {/* Ground truth reveal */}
      {voteResult.groundTruth && (
        <div className="w-full max-w-lg bg-crime-primary p-6 rounded-xl border border-crime-light mb-8">
          <h2 className="text-xl font-bold text-crime-accent mb-4">
            🔓 الحقيقة كلها
          </h2>
          <div className="bg-crime-dark p-4 rounded-lg whitespace-pre-wrap text-gray-200 leading-relaxed">
            {voteResult.groundTruth}
          </div>
        </div>
      )}

      {/* Eliminated player info */}
      {voteResult.eliminatedPlayer && (
        <div className="w-full max-w-lg bg-crime-primary/50 p-4 rounded-xl mb-8">
          <p className="text-gray-400">
            اللاعب اللي اتشال:{" "}
            <span className="text-white font-bold">
              {voteResult.eliminatedPlayer}
            </span>
            {" — "}
            <span
              className={
                voteResult.wasCriminal ? "text-red-400" : "text-blue-400"
              }
            >
              {voteResult.wasCriminal ? "المجرم" : "بريء"}
            </span>
          </p>
        </div>
      )}

      {/* Play again */}
      <div className="flex flex-col gap-3 items-center">
        {isHost && (
          <button
            onClick={onReplay}
            className="bg-crime-accent hover:bg-red-600 text-white font-bold py-4 px-12 rounded-lg text-xl transition-all"
          >
            🔄 لعبة جديدة
          </button>
        )}
        {!isHost && (
          <p className="text-gray-400 animate-pulse">
            مستنيين الهوست يبدأ لعبة جديدة...
          </p>
        )}
        <Link
          href="/"
          className="text-gray-400 hover:text-gray-200 text-sm underline transition-all"
        >
          ارجع للصفحة الرئيسية
        </Link>
      </div>
    </div>
  );
}
