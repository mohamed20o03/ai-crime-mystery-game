import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        arabic: ["Noto Sans Arabic", "Arial", "sans-serif"],
      },
      colors: {
        crime: {
          dark: "#1a1a2e",
          primary: "#16213e",
          accent: "#e94560",
          light: "#0f3460",
        },
      },
    },
  },
  plugins: [],
};
export default config;
