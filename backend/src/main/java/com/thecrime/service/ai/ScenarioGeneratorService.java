package com.thecrime.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecrime.domain.enums.PlayerRole;
import com.thecrime.domain.model.GameRoom;
import com.thecrime.domain.model.Player;
import com.thecrime.domain.model.PlayerClue;
import com.thecrime.domain.model.PlayerPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioGeneratorService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}") private String apiKey;
    @Value("${gemini.model}")   private String model;

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final Map<String, Object> GENERATION_CONFIG = Map.of(
        "temperature",      0.5,
        "topK",             40,
        "topP",             0.95,
        "maxOutputTokens",  65536,
        "responseMimeType", "application/json"
    );

    private static final String SYSTEM_PROMPT = """
        You are an interactive Murder Mystery Game Master.
        Your task is to author a highly detailed crime scenario, suspect characters, clues, and testimonies.

        ═══════════════════════════════════════════════════════════
        ## 🇪🇬 ABSOLUTE CRITICAL CONSTRAINT: Egyptian Arabic Only!
        ═══════════════════════════════════════════════════════════
        - Every single output string (the story, testimonies, clues, reasons) MUST be written in 100% natural Egyptian Arabic dialect (العامية المصرية).
        - 🚨 EXCEPTION FOR NAMES 🚨: The player names provided to you will be English markers like "PLAYER_A", "PLAYER_B". You MUST use these EXACT English markers as their names in the Arabic text. DO NOT translate the markers into Arabic!
        - Modern Standard Arabic (الفصحى) is STRICTLY FORBIDDEN.
        - Use daily, convincing Egyptian vocabulary and slang.
        - Examples: use "شاف" not "رأى", "راح" not "ذهب", "عشان" not "لأن".

        ═══════════════════════════════════════════════════════════
        ## 🎩 Agatha Christie & Sherlock Holmes Style — Mandatory!
        ═══════════════════════════════════════════════════════════

        ### 1. Everyone Looks Suspicious:
        - Every innocent player MUST have a real, strong motive that makes others suspect them.
        - However, there must also be a clue that clears them if players deduce it correctly.
        - The criminal should appear liked and calm — their true motives buried deep beneath the surface.

        ### 2. Deduction Chain — Dynamic by Player Count:
        - Number of rounds = number of innocents + 1 (the final round is always for the criminal).
        - Round 1 always: Every player holds a clue pointing to a different person — everyone is a suspect.
        - Middle rounds (if any): Innocence clues emerge, clearing innocents one by one.
        - Final round always: The criminal's alibi collapses and evidence against them accumulates.
        - Example for 3 players (2 innocents): Round 1 distribute suspicion → Round 2 clear an innocent → Round 3 criminal.
        - Example for 6 players (5 innocents): Round 1 distribute suspicion → Rounds 2-5 clear innocents one by one → Round 6 criminal.

        ### 3. Small Tangible Evidence:
        - Every clue must be extracted from an event that actually occurred in the story.
        - Inventing clues that have no origin in the narrative is strictly forbidden.
        - Examples: a specific time, a specific place, a spoken sentence, something seen.

        ### 4. Personal Secrets:
        - Every innocent has a personal secret unrelated to the crime that makes them look more suspicious.
        - Examples: petty theft, a secret relationship, covering a debt.

        ### 5. The Alibi Puzzle:
        - The criminal has an alibi that seems strong — but it has a single flaw that only becomes visible when comparing two testimonies.
        """;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void generateScenario(GameRoom room, List<String> playerNames, Consumer<Boolean> callback) {
        // ── 0. Create English Markers Mapping ────────────────────────
        Map<String, String> markerToName = new java.util.LinkedHashMap<>();
        List<String> markerNames = new java.util.ArrayList<>();
        char letter = 'A';
        for (String name : playerNames) {
            String marker = "PLAYER_" + letter++;
            markerToName.put(marker, name);
            markerNames.add(marker);
        }
        log.info("Mapped players to markers: {}", markerToName);

        // ── Reactive chain: Foundation → Clues → Testimonies ─────────
        log.info("[Step 1/3] Generating full narrative story...");

        callGeminiReactive(buildFoundationPrompt(room, markerNames))
            .map(this::parse)
            .doOnNext(f -> log.info("[Step 1/3] Foundation generated."))
            .flatMap(foundationWithMarkers -> {
                log.info("[Step 2/3] Extracting clues from story...");
                return callGeminiReactive(buildCluesPrompt(foundationWithMarkers, markerNames))
                    .map(this::parse)
                    .doOnNext(c -> log.info("[Step 2/3] Clues generated."))
                    .map(cluesWithMarkers -> new JsonNode[]{ foundationWithMarkers, cluesWithMarkers });
            })
            .flatMap(pair -> {
                JsonNode foundationWithMarkers = pair[0];
                JsonNode cluesWithMarkers = pair[1];
                log.info("[Step 3/3] Generating testimonies...");
                return callGeminiReactive(buildTestimoniesPrompt(foundationWithMarkers, cluesWithMarkers))
                    .map(this::parse)
                    .doOnNext(t -> log.info("[Step 3/3] Testimonies generated."))
                    .map(testimoniesNode -> new JsonNode[]{ foundationWithMarkers, cluesWithMarkers, testimoniesNode });
            })
            .subscribe(
                results -> {
                    try {
                        JsonNode foundationWithMarkers = results[0];
                        JsonNode cluesWithMarkers = results[1];
                        JsonNode testimoniesWithMarkers = results[2];

                        // ── Reverse map markers back to Arabic names ─────
                        String foundationRaw = foundationWithMarkers.toString();
                        String cluesRaw = cluesWithMarkers.toString();
                        String testimoniesRaw = testimoniesWithMarkers.toString();

                        for (Map.Entry<String, String> entry : markerToName.entrySet()) {
                            foundationRaw = foundationRaw.replace(entry.getKey(), entry.getValue());
                            cluesRaw = cluesRaw.replace(entry.getKey(), entry.getValue());
                            testimoniesRaw = testimoniesRaw.replace(entry.getKey(), entry.getValue());
                        }

                        JsonNode foundation = objectMapper.readTree(foundationRaw);
                        JsonNode clues = objectMapper.readTree(cluesRaw);
                        JsonNode testimonies = objectMapper.readTree(testimoniesRaw);

                        log.info("===== FOUNDATION JSON =====\n{}", foundation.toPrettyString());
                        log.info("===== CLUES JSON =====\n{}", clues.toPrettyString());
                        log.info("===== TESTIMONIES JSON =====\n{}", testimonies.toPrettyString());

                        // ── Assemble everything into PlayerPackages ──────
                        assemblePackages(room, foundation, clues, testimonies);

                        // ── Validate hooks (Soft Validation) ─────────────
                        List<String> violations = validateHooks(room);
                        if (!violations.isEmpty()) {
                            violations.forEach(v -> log.warn("⚠️ Hook Warning (game continues): {}", v));
                        }

                        // ── Check no player is missing a package ─────────
                        List<String> missing = room.getAllPlayers().stream()
                            .filter(p -> p.getPlayerPackage() == null)
                            .map(Player::getName).toList();
                        if (!missing.isEmpty()) {
                            log.error("Missing packages for: {}", missing);
                            callback.accept(false);
                            return;
                        }

                        log.info("Scenario generation complete for room {}", room.getRoomCode());
                        callback.accept(true);

                    } catch (Exception e) {
                        log.error("Error assembling scenario", e);
                        callback.accept(false);
                    }
                },
                error -> {
                    log.error("Error generating scenario", error);
                    callback.accept(false);
                }
            );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt Builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildFoundationPrompt(GameRoom room, List<String> playerNames) {
        String criminalNote = room.getCriminalCount() > 1
            ? String.format("عدد المجرمين: %d (بيشتغلوا مع بعض)", room.getCriminalCount())
            : "عدد المجرمين: ١";

        return String.format("""
            Write a complete murder mystery story in Agatha Christie style for these details:
            - Players (THESE ARE SURVIVING SUSPECTS, DO NOT KILL THEM): %s
            - The Crime Place / Setting: %s
            - %s

            ## Mandatory Story Rules:

            ### First — Narrative Story (Scenario Refinement & Timeline):
            - Write a complete chronological narrative story, like a short novel.
            - 🚨 INVENT a fictional Non-Player Character (NPC) to be the victim. DO NOT kill any of the provided players.
            - 🚨 ALL provided players must be alive, must be suspects, and MUST be included in the `players` JSON object.
            - Fill in any logical loopholes in the story so it is completely watertight.
            - Every event must include: the exact minute, the location, who was where, and exactly what happened.
            - The story must fully explain the motives of every person.
            - Every innocent player must have:
              * A real, strong motive that makes them look suspicious (something that actually happens in the story).
              * A solid alibi that clears them if players connect the clues correctly.
              * A personal secret completely unrelated to the crime but adds suspicion.

            ### Second — Player Data (players):
            For each player:
            - role: exactly "criminal" or "innocent"
            - characterDescription: character's description and relationship to the victim
            - suspicionReason: the strong motive or personal secret making them look suspicious
            - personalSecret: the additional personal secret (for innocents)
            - alibi: who they were with and when (for innocents) — this must exist in the story
            - For criminals: coverStory, alibiCrack, tacticalNote, knowledgeAboutOthers
            - For innocents: location (at the time of the crime), blindSpot (something they couldn't see)

            ### Third — Secret Game Master Summary (master_timeline):
            - Explain the exact chronological sequence of the crime step-by-step in minutes.

            Return ONLY JSON matching this EXACT structure:
            {
              "fullNarrative": "...",
              "setting": "...",
              "crimeBriefing": "...",
              "groundTruth": "...",
              "master_timeline": [
                {
                  "event": "description of what happened",
                  "player_positions": {
                    "player1": "what they were doing/seeing",
                    "player2": "what they were doing/seeing"
                  }
                }
              ],
              "players": {
                "%s": {
                  "role": "...",
                  "characterDescription": "...",
                  "suspicionReason": "...",
                  "personalSecret": "...",
                  "alibi": "...",
                  "coverStory": "...",
                  "alibiCrack": "...",
                  "tacticalNote": "...",
                  "knowledgeAboutOthers": "...",
                  "location": "...",
                  "blindSpot": "..."
                }
              }
            }

            ⚠️ CRITICAL RULES BEFORE YOU RESPOND:
            1. All text values (story, motives, secrets) MUST BE WRITTEN IN PURE EGYPTIAN ARABIC (العامية المصرية).
            2. Using Modern Standard Arabic (الفصحى) will cause an IMMEDIATE FAILURE.
            3. The `players` field MUST be a JSON object where each key is the EXACT player name. Do NOT use arrays for players.
            """,
            String.join("، ", playerNames),
            playerNames.get(0),
            room.getSetting() != null ? room.getSetting() : "فندق فاخر في القاهرة",
            criminalNote
        );
    }

   
    private String buildCluesPrompt(JsonNode foundation, List<String> playerNames) { 
        String playersStr = String.join("، ", playerNames);
        
        return String.format("""
            Based on this story:
            %s

            ## Task: Extract Tangible Physical Evidence from the story.
            ⚠️ DO NOT invent any information that does not explicitly exist in the narrative.

            ### Mandatory Rules for Clues:
            - The `type` classification is mandatory: CRIMINAL, SUSPICION, ALIBI, or RED_HERRING.
            - Create 3 to 5 tangible physical clues (e.g., a restaurant receipt, mud on a shoe, a burnt text message, a piece of fabric, a lipstick smudge).
            - Do not make the clues expose the killer directly or easily.
            - Every physical clue must link a specific character to the crime scene or disprove their alibi.
            - The text inside the `clue` field MUST explain where the evidence was found and its hidden significance.

            ### Distribution Rules:
            - Every innocent must have at least 1 SUSPICION clue pointing to them.
            - Every SUSPICION clue must be countered by an ALIBI clue that clears its subject.
            - The ALIBI clue must be held by a different player (not the owner of the SUSPICION clue).
            - There must be at least 3 CRIMINAL clues, and they cannot all appear in the first round.
            - 🚨 🚨 CRITICAL SURVIVAL RULE: Any clue held by the actual Criminal MUST be a fake, fabricated clue (type: RED_HERRING) specifically designed to confuse others and frame innocent players. The Criminal must NEVER hold a true CRIMINAL clue that incriminates themselves. True CRIMINAL clues MUST be held by the innocent players!

            ### ⚠️ Interactive Distribution Rules (Mandatory - Spirit of the Game):
            1. It is STRICTLY FORBIDDEN for the `holder` to be the same as the `hook_player` for the same clue.
            2. The goal is for a player to find a clue that concerns another player or intersects with their testimony to confront them.
            3. Every clue must force communication and discussion between at least two different people.
            4. Clues must be distributed among different players from round 1 — the first clues cannot all point to the same person.
            5. Every player finding a clue must suspect a different person than another player finding their clue.
            6. Every SUSPICION clue for an innocent must be countered by an ALIBI clue held by another player that clears them.

            ### Mandatory Fields for Each Clue:
            - holder: The actual name of the player who will find this clue (Must be one of the players: %s).
            - type: CRIMINAL / SUSPICION / ALIBI / RED_HERRING
            - clue: The text of the physical evidence in the FIRST PERSON perspective (Example: "I found a crumpled restaurant receipt dropped near the door saying...").
            - targets: The name of the player this clue concerns or exposes as lying.
            - hook_player: 🚨🚨 SEVERE WARNING: The name of the player whose testimony will support or contradict this clue.
              ⚠️ MANDATORY AND CRITICAL: The name must EXCLUSIVELY be one of these players only: [%s].
              It is strictly forbidden to use any secondary characters, servants, workers, or any name not in the previous list.
            - hook_sentence: The exact sentence that will be present in the `hook_player`'s testimony (⚠️ Mandatory: Must be written in first-person "I" like: "I went to...", not "He went to...").
            - chain_connects_to: The name of another related clue.
            - narrative_source: The exact sentence from the story that this clue was extracted from.

            Return ONLY JSON matching this EXACT structure:
            {
              "clues": [
                {
                  "holder": "...",
                  "type": "...",
                  "clue": "...",
                  "targets": "...",
                  "hook_player": "...",
                  "hook_sentence": "...",
                  "chain_connects_to": "...",
                  "narrative_source": "..."
                }
              ]
            }

            ⚠️ CRITICAL RULES BEFORE YOU RESPOND:
            1. Every single clue description MUST BE WRITTEN IN PURE EGYPTIAN ARABIC (العامية المصرية).
            2. Using Modern Standard Arabic (الفصحى) will cause an IMMEDIATE FAILURE.
            """,
            foundation.path("fullNarrative").asText(foundation.toString()),
            playersStr, playersStr 
        );
    }

    private String buildTestimoniesPrompt(JsonNode foundation, JsonNode clues) {
        return String.format("""
            Based on the story and these physical clues:

            Full Story:
            %s

            Required Clues and Hooks:
            %s

            ## Task: Write the Witness Testimonies for the suspects.

            ### Mandatory Rules for Every Testimony:
            - Write a short formal testimony spoken by each player (in the first person) — exactly 3-4 sentences long.
            - Every testimony MUST absolutely contain:
              a) Their Alibi at the time of the crime.
              b) Their relationship to the victim.
              c) A slight contradiction or "half-truth" that intersects with another character's testimony or a physical clue, to arouse suspicion.
            - Every testimony MUST contain EXACTLY (Copy & Paste) all the requested `hook_sentences` for this player.
            - The testimony must sound natural and convincing.
            - The criminal's testimony must contain a tight cover story with a single hidden flaw.

            ⚠️ IMPORTANT: Use the exact real player names exactly as they are in the story.

            Return ONLY JSON matching this EXACT structure:
            {
              "testimonies": {
                "Player Name 1": "The testimony...",
                "Player Name 2": "The testimony..."
              }
            }

            ⚠️ CRITICAL RULES BEFORE YOU RESPOND:
            1. Every single testimony MUST BE WRITTEN IN PURE EGYPTIAN ARABIC (العامية المصرية).
            2. Using Modern Standard Arabic (الفصحى) will cause an IMMEDIATE FAILURE.
            """,
            foundation.path("fullNarrative").asText(foundation.toString()),
            clues.toString()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assembly
    // ─────────────────────────────────────────────────────────────────────────

    private void assemblePackages(GameRoom room, JsonNode foundation,
                                   JsonNode clues, JsonNode testimonies) {
        room.setGroundTruth(foundation.path("groundTruth").asText());
        room.setCrimeBriefing(foundation.path("crimeBriefing").asText());
        if (foundation.has("setting"))
            room.setSetting(foundation.path("setting").asText());
        if (foundation.has("master_timeline")) {
            room.setMasterTimeline(foundation.path("master_timeline").toString());
            log.info("Master timeline stored — {} events", foundation.path("master_timeline").size());
        }
        if (foundation.has("fullNarrative")) {
            room.setFullNarrative(foundation.path("fullNarrative").asText());
            log.info("Full narrative stored");
        }

        // ── Handle both array and object formats for "players" ──
        JsonNode playersNode = foundation.path("players");
        log.info("Players node type: {} (size: {})", playersNode.getNodeType(), playersNode.size());

        // Log clue holders & room players for debugging
        JsonNode cluesArray = clues.path("clues");
        log.info("Clues count: {}", cluesArray.size());
        List<String> roomPlayerNames = room.getAllPlayers().stream().map(Player::getName).toList();
        log.info("Room players: {}", roomPlayerNames);

        if (playersNode.isObject()) {
            // Preferred format: {"محمد": {...}, "أحمد": {...}}
            playersNode.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode info = entry.getValue();
                processPlayer(room, name, info, clues, testimonies);
            });
        } else if (playersNode.isArray()) {
            // Fallback: array — try "name" field first, else map by index to room players
            int idx = 0;
            for (JsonNode info : playersNode) {
                String name = info.path("name").asText("").trim();
                if (name.isEmpty() && idx < roomPlayerNames.size()) {
                    // No name field — map by array position to room player order
                    name = roomPlayerNames.get(idx);
                    log.info("Array player [{}] has no 'name' — mapped to room player '{}'", idx, name);
                }
                if (!name.isEmpty()) {
                    processPlayer(room, name, info, clues, testimonies);
                } else {
                    log.warn("Skipping player entry [{}] — no name and no room player at this index", idx);
                }
                idx++;
            }
        } else {
            log.error("'players' field is neither an array nor an object! Type: {}", playersNode.getNodeType());
        }

        // Log final summary
        room.getAllPlayers().forEach(p -> {
            if (p.getPlayerPackage() == null) {
                log.warn("Player '{}' received no package", p.getName());
            } else {
                int clueCount = p.getPlayerPackage().getPrivateClues() != null
                    ? p.getPlayerPackage().getPrivateClues().size() : 0;
                log.info("Player '{}' — role: {}, clues: {}", p.getName(), p.getRole(), clueCount);
            }
        });
    }

    private void processPlayer(GameRoom room, String name, JsonNode info,
                                JsonNode clues, JsonNode testimonies) {
        // Collect this player's clues from Call 2
        List<PlayerClue> playerClues = new ArrayList<>();
        clues.path("clues").forEach(c -> {
            String holder = c.path("holder").asText("").trim();
            // Fuzzy match: exact or contains
            if (holder.equals(name.trim())
                || holder.contains(name.trim())
                || name.trim().contains(holder)) {
                playerClues.add(PlayerClue.builder()
                    .clue(c.path("clue").asText())
                    .type(c.path("type").asText(null))
                    .targets(c.path("targets").asText(null))
                    .hookPlayer(c.path("hook_player").asText(null))
                    .hookSentence(c.path("hook_sentence").asText(null))
                    .chainConnectsTo(c.path("chain_connects_to").asText(null))
                    .narrativeSource(c.path("narrative_source").asText(null))
                    .build());
            }
        });
        log.info("Clues for '{}': {}", name, playerClues.size());

        // Testimony from Call 3 — handle both object and array
        String testimony = "";
        JsonNode testimonyNode = testimonies.path("testimonies");
        if (testimonyNode.isObject()) {
            testimony = testimonyNode.path(name).asText("");
        } else if (testimonyNode.isArray()) {
            for (JsonNode t : testimonyNode) {
                if (t.path("name").asText("").trim().equals(name.trim())) {
                    testimony = t.path("testimony").asText(t.path("text").asText(""));
                    break;
                }
            }
        }

        final String finalTestimony = testimony;
        findPlayer(room, name).ifPresentOrElse(
            player -> assignPackage(player, info, playerClues, finalTestimony),
            () -> log.warn("Unknown player '{}' — skipping. Known: {}",
                name, room.getAllPlayers().stream().map(Player::getName).toList())
        );
    }

    private void assignPackage(Player player, JsonNode info,
                                List<PlayerClue> clues, String testimony) {
        PlayerRole role = info.path("role").asText().toLowerCase().contains("criminal")
            ? PlayerRole.CRIMINAL : PlayerRole.INNOCENT;
        player.setRole(role);

        if (role == PlayerRole.CRIMINAL && clues != null) {
            String warning = "⚠️ تنبيه: الدليل متفبرك، القرار ليك إنك تستخدمه أو تخترع واحد.\n\n";
            clues.forEach(c -> c.setClue(warning + c.getClue()));
        }

        player.setPlayerPackage(PlayerPackage.builder()
            .role(role)
            .characterDescription(info.path("characterDescription").asText(null))
            .suspicionReason(info.path("suspicionReason").asText(null))
            .personalSecret(info.path("personalSecret").asText(null))
            .alibi(info.path("alibi").asText(null))
            .mustSayTestimony(testimony)
            .privateClues(clues)
            .coverStory(info.path("coverStory").asText(null))
            .tacticalNote(info.path("tacticalNote").asText(null))
            .location(info.path("location").asText(null))
            .blindSpot(info.path("blindSpot").asText(null))
            .knowledgeAboutOthers(info.path("knowledgeAboutOthers").asText(null))
            .alibiCrack(info.path("alibiCrack").asText(null))
            .build());

        log.info("Assigned {} package to '{}' (clues: {})", role, player.getName(), clues.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hook Validation
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> validateHooks(GameRoom room) {
        List<String> violations = new ArrayList<>();

        room.getAllPlayers().forEach(player -> {
            if (player.getPlayerPackage() == null) return;

            player.getPlayerPackage().getPrivateClues().forEach(clue -> {
                if (clue.getHookPlayer() == null || clue.getHookSentence() == null) return;

                findPlayer(room, clue.getHookPlayer()).ifPresentOrElse(hookPlayer -> {
                    if (hookPlayer.getPlayerPackage() == null) return;
                    String testimony = hookPlayer.getPlayerPackage().getMustSayTestimony();

                    if (!normalize(testimony).contains(normalize(clue.getHookSentence()))) {
                        violations.add(String.format(
                            "Hook broken | clue at '%s': [%s] | expected in '%s' testimony: [%s]",
                            player.getName(), clue.getClue(),
                            clue.getHookPlayer(), clue.getHookSentence()
                        ));
                    }
                }, () -> violations.add(
                    String.format("hook_player '%s' not found in game", clue.getHookPlayer())
                ));
            });
        });

        return violations;
    }

    private String normalize(String s) {
        return s.replaceAll("[\\s\\p{Punct}،؟!.]", "").toLowerCase();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gemini API (Reactive)
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<String> callGeminiReactive(String userPrompt) {
        Map<String, Object> body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
            "contents",           List.of(Map.of("role", "user",
                                                 "parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig",   GENERATION_CONFIG
        );

        return geminiWebClient.post()
            .uri("/models/{m}:generateContent?key={k}", model, apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .map(this::extractText);
    }

    private JsonNode parse(String raw) {
        try {
            return objectMapper.readTree(extractJson(raw));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response as JSON", e);
        }
    }

    private String extractText(String response) {
        try {
            return objectMapper.readTree(response)
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
        } catch (Exception e) {
            log.error("Failed to extract text from Gemini response", e);
            return "";
        }
    }

    private String extractJson(String text) {
        Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
        if (m.find()) return m.group(1).trim();

        Matcher open = Pattern.compile("```(?:json)?\\s*([\\s\\S]*)").matcher(text);
        if (open.find()) return open.group(1).trim();

        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text.trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String normalizeArabic(String s) {
        if (s == null) return "";
        return s.replaceAll("[أإآ]", "ا")
                .replaceAll("ة", "ه")
                .replaceAll("ى", "ي")
                .replaceAll("[\\p{Mn}]", "") // Remove diacritics
                .toLowerCase();
    }

    private Optional<Player> findPlayer(GameRoom room, String name) {
        String key = name.trim().replaceAll("\\s+", " ");
        // Exact match first
        Optional<Player> exact = room.getAllPlayers().stream()
            .filter(p -> p.getName().trim().replaceAll("\\s+", " ").equals(key))
            .findFirst();
        if (exact.isPresent()) return exact;

        // Fuzzy: contains-based fallback with Arabic normalization
        String normalizedKey = normalizeArabic(key);
        return room.getAllPlayers().stream()
            .filter(p -> {
                String pName = p.getName().trim().replaceAll("\\s+", " ");
                String normalizedPName = normalizeArabic(pName);
                return normalizedPName.contains(normalizedKey) || normalizedKey.contains(normalizedPName);
            })
            .findFirst();
    }
}