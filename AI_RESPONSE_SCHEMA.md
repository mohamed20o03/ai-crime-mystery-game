# AI Response JSON Schema — 3-Step Pipeline

> Part of [The Crime](README.md) — a real-time multiplayer murder mystery game powered by Gemini AI.

The scenario is generated through **3 sequential Gemini API calls**. Each call returns JSON with `responseMimeType: "application/json"`.  
Every string value is in **Egyptian Arabic dialect (العامية المصرية)**.

---

## Step 1: Foundation Response

The first call generates the complete crime narrative and character profiles.

```json
{
  "fullNarrative":    "string (complete crime story)",
  "setting":          "string",
  "crimeBriefing":    "string (public summary — no spoilers)",
  "groundTruth":      "string (who did it and why)",
  "master_timeline":  [...],
  "players": {
    "محمد": { "role": "innocent", ... },
    "سارة": { "role": "criminal", ... }
  }
}
```

| Field             | Type            | Visible to players?      | Description                                              |
| ----------------- | --------------- | ------------------------ | -------------------------------------------------------- |
| `fullNarrative`   | String          | ❌ Never                 | Complete story — used as input for Steps 2 & 3.          |
| `setting`         | String          | ✅ Everyone              | Short location (e.g. "فندق فاخر في القاهرة").            |
| `crimeBriefing`   | String          | ✅ Everyone              | Public crime summary. Does NOT reveal the criminal.      |
| `groundTruth`     | String          | ❌ Revealed at game over | Full truth: who did it, how, and why.                    |
| `master_timeline` | Array of events | ❌ Never                 | Chronological timeline. Stored server-side for debugging.|
| `players`         | Object map      | ✅ Per-player only       | Player profiles keyed by real player name.               |

### `players` — Player Profile Object

Keys are **exact player names** as provided by the room.  
⚠️ The prompt enforces JSON object format (not array).

```json
{
  "players": {
    "سارة": {
      "role": "innocent",
      "characterDescription": "وصف الشخصية وعلاقتها بالضحية",
      "suspicionReason": "الدافع القوي اللي بيخليه يبان مشبوه",
      "personalSecret": "سر شخصي مش علاقة بالجريمة",
      "alibi": "مع مين كان وقت الجريمة",
      "location": "فين كان وقت الجريمة",
      "blindSpot": "حاجة مقدرش يشوفها"
    },
    "أحمد": {
      "role": "criminal",
      "characterDescription": "...",
      "suspicionReason": "...",
      "coverStory": "الحجة اللي هيقولها",
      "alibiCrack": "الثغرة في الحجة",
      "tacticalNote": "نصيحة تكتيكية",
      "knowledgeAboutOthers": "معلومات عن باقي اللعيبة"
    }
  }
}
```

| Field                  | Type                         | Who gets it        | Description                                             |
| ---------------------- | ---------------------------- | ------------------ | ------------------------------------------------------- |
| `role`                 | `"criminal"` \| `"innocent"` | Everyone (private) | Player's secret role.                                   |
| `characterDescription` | String                       | Everyone (public)  | Character background and relation to victim.            |
| `suspicionReason`      | String                       | Everyone (public)  | Why others might suspect this player.                   |
| `personalSecret`       | String                       | 🔵 Innocent only   | Secret unrelated to crime that adds suspicion.          |
| `alibi`                | String                       | 🔵 Innocent only   | Who they were with during the crime.                    |
| `location`             | String                       | 🔵 Innocent only   | Where they were during the crime.                       |
| `blindSpot`            | String                       | 🔵 Innocent only   | What they couldn't see/hear.                            |
| `coverStory`           | String                       | 🔴 Criminal only   | Plausible alibi to maintain.                            |
| `alibiCrack`           | String                       | 🔴 Criminal only   | Hidden flaw in the cover story.                         |
| `tacticalNote`         | String                       | 🔴 Criminal only   | Strategic advice on casting suspicion.                  |
| `knowledgeAboutOthers` | String                       | 🔴 Criminal only   | What they know about other players' positions.          |

---

## Step 2: Clues Response — Tangible Evidence

The second call extracts **physical, tangible evidence** from the narrative.

```json
{
  "clues": [
    {
      "holder": "محمد",
      "type": "SUSPICION",
      "clue": "لقيت إيصال مطعم مجعد واقع جنب الباب...",
      "targets": "سارة",
      "hook_player": "سارة",
      "hook_sentence": "أنا رحت المطعم الساعة 9",
      "chain_connects_to": "بصمة الروچ",
      "narrative_source": "الجملة من القصة اللي الدليل مبني عليها"
    }
  ]
}
```

### Clue Fields

| Field               | Type   | Description                                                         |
| ------------------- | ------ | ------------------------------------------------------------------- |
| `holder`            | String | Player name who receives this clue. Must be a real player name.     |
| `type`              | Enum   | `CRIMINAL`, `SUSPICION`, `ALIBI`, or `RED_HERRING`                  |
| `clue`              | String | First-person description of tangible evidence ("لقيت...", "شفت...") |
| `targets`           | String | Player name this clue points toward.                                |
| `hook_player`       | String | Player whose testimony activates this clue.                         |
| `hook_sentence`     | String | Exact sentence that must appear in `hook_player`'s testimony.       |
| `chain_connects_to` | String | Name of related clue that adds meaning when combined.               |
| `narrative_source`  | String | Quote from the story this evidence was extracted from.              |

### Clue Type Classification

| Type          | Purpose                                             | Example                                |
| ------------- | --------------------------------------------------- | -------------------------------------- |
| `CRIMINAL`    | Points toward the actual criminal                   | Blood stain near criminal's seat       |
| `SUSPICION`   | Makes an innocent look suspicious                   | Threatening message from innocent      |
| `ALIBI`       | Clears an innocent's SUSPICION clue                 | Security cam showing they were away    |
| `RED_HERRING` | Misleading evidence that wastes investigation time  | Unrelated item found at crime scene    |

### Distribution Rules

- Every innocent has at least 1 SUSPICION clue
- Every SUSPICION clue is paired with an ALIBI clue held by a different player
- At least 3 CRIMINAL clues, not all revealed in round 1
- Suspicion distributed across all players from round 1 — no single person targeted early

---

## Step 3: Testimonies Response

The third call generates first-person witness statements with embedded hooks.

```json
{
  "testimonies": {
    "محمد": "أنا كنت في المطبخ الساعة 10... أنا رحت المطعم الساعة 9...",
    "سارة": "أنا كنت في أوضتي...",
    "أحمد": "أنا كنت مع كريم في الصالة..."
  }
}
```

| Field          | Type         | Description                                        |
| -------------- | ------------ | -------------------------------------------------- |
| `testimonies`  | Object (map) | Key = player name, Value = testimony string.       |

### Testimony Requirements

Each testimony (3-4 sentences) must contain:
- **Alibi** — where they were during the crime
- **Relationship** — their connection to the victim
- **Contradiction** — subtle "half-lie" that cross-references another testimony or clue
- **Hook sentences** — exact phrases from Step 2's `hook_sentence` fields (copy-pasted)

---

## How the Backend Assembles Packages

```
Step 1 JSON ──► players{} → role, characterDescription, alibi, etc.
Step 2 JSON ──► clues[] → matched to players by `holder` name (fuzzy matching)
Step 3 JSON ──► testimonies{} → matched to players by name
                    │
                    ▼
              PlayerPackage per player
              (stored in-memory, never fully sent to clients)
```

### Clue Matching Logic

The backend uses **fuzzy matching** for clue `holder` → player:
1. Exact string match
2. Contains-based fallback (handles "أحمد" vs "أحمد المصري")
3. Array index fallback (when LLM returns nameless array)

---

## What the Server Sends to Clients

The backend (`GameService.sendPlayerPackage`) filters data before sending:

| Field                  | Sent to criminal?                         | Sent to innocent?    |
| ---------------------- | ----------------------------------------- | -------------------- |
| `role`                 | ✅                                        | ✅                   |
| `characterDescription` | ✅                                        | ✅                   |
| `mustSayTestimony`     | ✅                                        | ✅                   |
| `privateClues`         | ❌ Replaced with misleading message       | ✅ Real clues        |
| `totalClueCount`       | ✅ (shows X of Y)                         | ✅                   |
| `fellowCriminals`      | ✅ (names of co-criminals)                | ❌ null              |
| `master_timeline`      | ❌ Never                                  | ❌ Never             |
| `groundTruth`          | ❌ Only at GAME_OVER                      | ❌ Only at GAME_OVER |

### Clue Reveal Schedule

Clues are revealed **one per round** via `revealNextClue()`:

| Round | Clues revealed per player |
| ----- | ------------------------- |
| 1     | 1st clue                  |
| 2     | 1st + 2nd clue            |
| 3     | 1st + 2nd + 3rd clue      |
| 4+    | All clues                 |
