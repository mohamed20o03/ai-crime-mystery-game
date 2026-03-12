"use client";

import Link from "next/link";

export default function Home() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[80vh] text-center">
      <h1 className="text-5xl font-bold mb-4 text-crime-accent">الجريمة</h1>
      <p className="text-xl text-gray-300 mb-12 max-w-md">
        لعبة ألغاز جماعية لازم اللعيبة يشتغلوا مع بعض عشان يكشفوا المجرم قبل ما
        يفوت الأوان
      </p>

      <div className="flex flex-col gap-4 w-full max-w-xs">
        <Link
          href="/create"
          className="bg-crime-accent hover:bg-red-600 text-white font-bold py-4 px-8 rounded-lg text-xl transition-all hover:scale-105"
        >
          افتح أوضة جديدة
        </Link>

        <Link
          href="/join"
          className="bg-crime-light hover:bg-crime-primary text-white font-bold py-4 px-8 rounded-lg text-xl transition-all hover:scale-105 border border-crime-accent"
        >
          ادخل أوضة
        </Link>
      </div>

      <div className="mt-16 text-gray-400 text-sm">
        <p>٤-٨ لاعبين • جولات كتير • مجرم واحد</p>
      </div>
    </div>
  );
}
