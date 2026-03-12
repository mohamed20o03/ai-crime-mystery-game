# AI Response JSON Schema

> Part of [The Crime](README.md) — a real-time multiplayer murder mystery game powered by Gemini AI.

The Gemini model returns a single JSON object (no markdown wrapper).  
Every string value is in **Egyptian Arabic dialect (العامية المصرية)**.

---

## Top-Level Structure

```json
{
  "master_timeline": [...],
  "crimeBriefing":   "string",
  "timeline":        ["string", ...],
  "groundTruth":     "string",
  "setting":         "string",
  "packages":        { "PlayerName": { ... }, ... }
}
```

| Field             | Type            | Visible to players?      | Description                                               |
| ----------------- | --------------- | ------------------------ | --------------------------------------------------------- |
| `master_timeline` | Array of events | ❌ Never                 | Full chain-of-thought timeline. Stored server-side only.  |
| `crimeBriefing`   | String          | ✅ Everyone              | Public crime summary. Does NOT reveal the criminal.       |
| `timeline`        | String[]        | ✅ Everyone              | 4–6 short reference events shown on the briefing screen.  |
| `groundTruth`     | String          | ❌ Revealed at game over | Full truth: who did it, how, and why.                     |
| `setting`         | String          | ✅ Everyone              | Short location description (e.g. "فندق فاخر في القاهرة"). |
| `packages`        | Object map      | ✅ Per-player only       | Each player's private role card, testimony, and clues.    |

---

## `master_timeline` — Event Object

The hidden chain-of-thought. Contains 6–10 chronological events.  
Every clue and testimony in `packages` **must** trace back to an event here.

```json
{
  "master_timeline": [
    {
      "event": "لما النور قطع: أحمد كان في المطبخ وسارة كانت على السلم",
      "player_positions": {
        "أحمد": "في المطبخ جنب أوضة الضحية — يقدر يسمع أي حاجة من جوا",
        "سارة": "على السلم — تقدر تسمع خطوات من ناحية المطبخ",
        "كريم": "في الصالة مع محمد — شاف ضل بيتحرك في الممر",
        "محمد": "في الصالة مع كريم — سمع صوت حاجة اتكسرت"
      }
    }
  ]
}
```

| Field              | Type         | Description                                                           |
| ------------------ | ------------ | --------------------------------------------------------------------- |
| `event`            | String       | Human-readable description of what happened at this moment.           |
| `player_positions` | Object (map) | Key = player name. Value = where they were, what they could see/hear. |

---

## `packages` — Per-Player Package Object

Keys are **exact player names** as provided to the AI.  
Each player receives their own package privately over WebSocket.

```json
{
  "packages": {
    "سارة": {
      "role": "innocent",
      "characterDescription": "string",
      "suspicionReason": "string",
      "mustSayTestimony": "string",
      "privateClues": ["string", "string", "string", "string"],
      "coverStory": null,
      "physicalState": null,
      "tacticalNote": null,
      "location": "string",
      "blindSpot": "string",
      "knowledgeAboutOthers": null
    },
    "أحمد": {
      "role": "criminal",
      "characterDescription": "string",
      "suspicionReason": "string",
      "mustSayTestimony": "string",
      "privateClues": ["string", "string", "string", "string"],
      "coverStory": "string",
      "physicalState": "string",
      "tacticalNote": "string",
      "location": null,
      "blindSpot": null,
      "knowledgeAboutOthers": "string"
    }
  }
}
```

### Package Fields

| Field                  | Type                         | Who gets it        | Description                                                                                                                                       |
| ---------------------- | ---------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `role`                 | `"criminal"` \| `"innocent"` | Everyone (private) | Player's role.                                                                                                                                    |
| `characterDescription` | String                       | Everyone (private) | Who this character is, their relation to the victim, why they're a suspect.                                                                       |
| `suspicionReason`      | String                       | Everyone (private) | The specific reason others might suspect this player.                                                                                             |
| `mustSayTestimony`     | String                       | Everyone (private) | 3–4 sentences the player **must say out loud** during discussion. Derived from `master_timeline`.                                                 |
| `privateClues`         | String[]                     | Everyone (private) | 3–4 circumstantial clues. One new clue revealed per round. All written in **first person** ("شفت…", "سمعت…"). All derived from `master_timeline`. |
| `coverStory`           | String                       | 🔴 Criminal only   | Plausible alibi the criminal should stick to.                                                                                                     |
| `physicalState`        | String                       | 🔴 Criminal only   | Guidance on body language / composure.                                                                                                            |
| `tacticalNote`         | String                       | 🔴 Criminal only   | Strategic advice: how to cast suspicion on others.                                                                                                |
| `location`             | String                       | 🔵 Innocent only   | Where this innocent was during the crime (from timeline).                                                                                         |
| `blindSpot`            | String                       | 🔵 Innocent only   | What this innocent couldn't see/hear and why.                                                                                                     |
| `knowledgeAboutOthers` | String                       | 🔴 Criminal only   | What the criminal knows about other players' positions.                                                                                           |

> **Note:** Criminal fields (`coverStory`, `physicalState`, `tacticalNote`, `knowledgeAboutOthers`) are `null` for innocent players.  
> Innocent fields (`location`, `blindSpot`) are `null` for the criminal.

---

## Clue Rules (enforced via prompt)

| Rule                      | Details                                                                                       |
| ------------------------- | --------------------------------------------------------------------------------------------- |
| **Timeline-derived only** | Every clue must reference a fact present in `master_timeline`. No invented details.           |
| **No smoking guns**       | No clue directly shows the murder, the murder weapon in the criminal's hand, or a confession. |
| **Circumstantial only**   | Heard a sound, saw someone running, noticed something out of place, smelled something odd.    |
| **First-person only**     | All clues written as "شفت…" / "سمعت…" / "لاحظت…" — never "حد شافك" or third-person.           |
| **No self-referencing**   | A player never receives a clue about themselves from the outside.                             |
| **Distributed puzzle**    | No single clue is enough to identify the criminal alone. Players must combine clues.          |

---

## Clue Reveal Schedule

Clues in `privateClues` are revealed **one per round**, in order:

| Round | Clues revealed per player |
| ----- | ------------------------- |
| 1     | `privateClues[0]`         |
| 2     | `privateClues[0–1]`       |
| 3     | `privateClues[0–2]`       |
| 4+    | `privateClues[0–3]` (all) |

---

## What the Server Strips Before Sending

The backend (`GameService.sendPlayerPackage`) **replaces** criminal clues with a misleading message and **never** sends `master_timeline` or `groundTruth` to clients during the game:

| Field                  | Sent to criminal?                | Sent to innocent?    |
| ---------------------- | -------------------------------- | -------------------- |
| `role`                 | ✅                               | ✅                   |
| `characterDescription` | ✅                               | ✅                   |
| `mustSayTestimony`     | ✅                               | ✅                   |
| `privateClues`         | ❌ Replaced with mislead message | ✅ Real clues        |
| `fellowCriminals`      | ✅ (names of co-criminals)       | ❌ null              |
| `master_timeline`      | ❌ Never                         | ❌ Never             |
| `groundTruth`          | ❌ Only at GAME_OVER             | ❌ Only at GAME_OVER |
