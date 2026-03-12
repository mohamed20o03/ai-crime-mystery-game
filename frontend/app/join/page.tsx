"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { joinRoom } from "@/lib/api";

export default function JoinRoom() {
  const router = useRouter();
  const [playerName, setPlayerName] = useState("");
  const [roomCode, setRoomCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    const result = await joinRoom(roomCode.toUpperCase(), playerName);

    if (result.error) {
      setError(result.error);
      setLoading(false);
      return;
    }

    if (result.data) {
      // Store player info in sessionStorage
      sessionStorage.setItem("playerId", result.data.playerId);
      sessionStorage.setItem("playerName", result.data.playerName);
      sessionStorage.setItem("isHost", "false");

      router.push(`/room/${result.data.roomCode}`);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[80vh]">
      <Link
        href="/"
        className="absolute top-8 right-8 text-gray-400 hover:text-white"
      >
        → ارجع
      </Link>

      <h1 className="text-3xl font-bold mb-8 text-crime-accent">ادخل أوضة</h1>

      <form onSubmit={handleSubmit} className="w-full max-w-md space-y-6">
        <div>
          <label className="block text-sm font-medium mb-2">كود الأوضة</label>
          <input
            type="text"
            value={roomCode}
            onChange={(e) => setRoomCode(e.target.value.toUpperCase())}
            placeholder="اكتب كود الأوضة (٦ حروف)"
            required
            maxLength={6}
            className="w-full px-4 py-3 bg-crime-primary border border-crime-light rounded-lg focus:outline-none focus:border-crime-accent text-white placeholder-gray-400 text-center text-2xl tracking-widest font-mono"
            style={{ direction: "ltr" }}
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">اسمك</label>
          <input
            type="text"
            value={playerName}
            onChange={(e) => setPlayerName(e.target.value)}
            placeholder="اكتب اسمك"
            required
            minLength={2}
            maxLength={20}
            className="w-full px-4 py-3 bg-crime-primary border border-crime-light rounded-lg focus:outline-none focus:border-crime-accent text-white placeholder-gray-400"
          />
        </div>

        {error && (
          <div className="bg-red-900/50 border border-red-500 text-red-200 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading || roomCode.length !== 6 || playerName.length < 2}
          className="w-full bg-crime-accent hover:bg-red-600 disabled:bg-gray-600 disabled:cursor-not-allowed text-white font-bold py-4 rounded-lg text-xl transition-all"
        >
          {loading ? "بتدخل..." : "ادخل"}
        </button>
      </form>
    </div>
  );
}
