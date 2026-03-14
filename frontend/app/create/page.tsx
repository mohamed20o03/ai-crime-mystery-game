"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { createRoom } from "@/lib/api";

export default function CreateRoom() {
  const router = useRouter();
  const [hostName, setHostName] = useState("");
  const [setting, setSetting] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    const result = await createRoom(
      hostName,
      setting || undefined,
    );

    if (result.error) {
      setError(result.error);
      setLoading(false);
      return;
    }

    if (result.data) {
      // Store player info in sessionStorage
      sessionStorage.setItem("playerId", result.data.playerId);
      sessionStorage.setItem("playerName", result.data.playerName);
      sessionStorage.setItem("isHost", "true");

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

      <h1 className="text-3xl font-bold mb-8 text-crime-accent">
        افتح أوضة جديدة
      </h1>

      <form onSubmit={handleSubmit} className="w-full max-w-md space-y-6">
        <div>
          <label className="block text-sm font-medium mb-2">اسمك</label>
          <input
            type="text"
            value={hostName}
            onChange={(e) => setHostName(e.target.value)}
            placeholder="اكتب اسمك"
            required
            minLength={2}
            maxLength={20}
            className="w-full px-4 py-3 bg-crime-primary border border-crime-light rounded-lg focus:outline-none focus:border-crime-accent text-white placeholder-gray-400"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">
            مكان الجريمة (اختياري)
          </label>
          <input
            type="text"
            value={setting}
            onChange={(e) => setSetting(e.target.value)}
            placeholder="مثلاً: فندق فخم، قصر قديم، مركب"
            maxLength={50}
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
          disabled={loading || hostName.length < 2}
          className="w-full bg-crime-accent hover:bg-red-600 disabled:bg-gray-600 disabled:cursor-not-allowed text-white font-bold py-4 rounded-lg text-xl transition-all"
        >
          {loading ? "بيتعمل..." : "افتح الأوضة"}
        </button>
      </form>
    </div>
  );
}
