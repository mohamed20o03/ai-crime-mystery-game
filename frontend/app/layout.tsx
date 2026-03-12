import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "الجريمة - لعبة لغز القتل",
  description: "لعبة ألغاز جماعية حيث يحاول اللاعبون كشف المجرم",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ar" dir="rtl">
      <body className="min-h-screen bg-crime-dark text-white">
        <main className="container mx-auto px-4 py-8 max-w-4xl">
          {children}
        </main>
      </body>
    </html>
  );
}
