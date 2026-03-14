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
        إنت مدير لعبة لغز الجريمة على طراز أجاثا كريستي وشيرلوك هولمز.
        شغلتك إنك تعمل سيناريو جريمة محكم وتوزع المعلومات على اللعيبة.

        ## ⚠️ مهم جداً — اللهجة:
        كل الكلام لازم يبقى بالعامية المصرية. متستخدمش فصحى أبداً.
        أمثلة: "شاف" مش "رأى"، "راح" مش "ذهب"، "عشان" مش "لأن"

        ═══════════════════════════════════════════════════════════
        ## 🎩 أسلوب أجاثا كريستي وشيرلوك هولمز — إلزامي!
        ═══════════════════════════════════════════════════════════

        ### ١. كل شخص يبان مشبوه:
        - كل بريء لازم عنده دافع حقيقي وقوي يخلي الناس يشكوا فيه
        - بس في نفس الوقت في دليل بيبرئه لو الناس فكروا صح
        - المجرم يبان محبوب وهادي — دوافعه الحقيقية مدفونة تحت السطح

        ### ٢. سلسلة الاستنتاج — ديناميكية حسب عدد اللاعبين:
        - عدد الجولات = عدد الأبرياء + ١ (الأخيرة دايماً للمجرم)
        - الجولة الأولى دايماً: كل لاعب عنده دليل بيشاور على شخص مختلف — الكل مشكوك فيه
        - الجولات الوسطى (لو موجودة): أدلة البراءة بتبرئ الأبرياء واحد واحد
        - الجولة الأخيرة دايماً: الـ alibi بتاع المجرم بينهار والأدلة عليه تتجمع
        - مثال ٣ لاعبين (٢ بريء): جولة ١ توزيع الشبهة → جولة ٢ البريء يتبرأ → جولة ٣ المجرم
        - مثال ٦ لاعبين (٥ بريء): جولة ١ توزيع → جولات ٢-٥ برايا واحد واحد → جولة ٦ المجرم

        ### ٣. الأدلة المادية الصغيرة:
        - كل دليل لازم يكون مستخرج من حدث حصل فعلاً في القصة
        - ممنوع أي دليل يكون مش موجود أصله في السرد
        - أمثلة: وقت محدد، مكان محدد، جملة اتقالت، حاجة اتشافت

        ### ٤. الأسرار الشخصية:
        - كل بريء عنده سر شخصي مش علاقة له بالجريمة بيخليه يبان أكتر مشبوهية
        - أمثلة: بيسرق فلوس صغيرة، علاقة سرية، بيغطي دين

        ### ٥. لغز الـ Alibi:
        - المجرم عنده alibi يبدو قوي — بس فيه ثغرة واحدة بتظهر بس بمقارنة شهادتين
        """;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void generateScenario(GameRoom room, List<String> playerNames, Consumer<Boolean> callback) {
        try {
            // ── Call 1: Full narrative story ────────────────────────────
            log.info("[Step 1/3] Generating full narrative story...");
            JsonNode foundation = callAndParse(buildFoundationPrompt(room, playerNames));
            log.info("[Step 1/3] Foundation generated.");
            log.info("===== FOUNDATION JSON =====\n{}", foundation.toPrettyString());

            // ── Call 2: Extract clues FROM the story ────────────────────
            log.info("[Step 2/3] Extracting clues from story...");
            JsonNode clues = callAndParse(buildCluesPrompt(foundation, playerNames)); 
            log.info("[Step 2/3] Clues generated.");
            log.info("===== CLUES JSON =====\n{}", clues.toPrettyString());

            // ── Call 3: Testimonies built around hooks ───────────────────
            log.info("[Step 3/3] Generating testimonies...");
            JsonNode testimonies = callAndParse(buildTestimoniesPrompt(foundation, clues));
            log.info("[Step 3/3] Testimonies generated.");
            log.info("===== TESTIMONIES JSON =====\n{}", testimonies.toPrettyString());

            // ── Assemble everything into PlayerPackages ──────────────────
            assemblePackages(room, foundation, clues, testimonies);

            // ── Validate hooks (Soft Validation) ─────────────────────────
            List<String> violations = validateHooks(room);
            if (!violations.isEmpty()) {
                violations.forEach(v -> log.warn("⚠️ Hook Warning (game continues): {}", v));
            }

            // ── Check no player is missing a package ─────────────────────
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
            log.error("Error generating scenario", e);
            callback.accept(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt Builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildFoundationPrompt(GameRoom room, List<String> playerNames) {
        String criminalNote = room.getCriminalCount() > 1
            ? String.format("عدد المجرمين: %d (بيشتغلوا مع بعض)", room.getCriminalCount())
            : "عدد المجرمين: ١";

        return String.format("""
            اكتب قصة جريمة قتل كاملة بالعامية المصرية بأسلوب أجاثا كريستي للبيانات دي:
            - اللعيبة: %s
            - المكان: %s
            - %s

            ## القواعد الإلزامية للقصة:

            ### أولاً — القصة السردية (Scenario Refinement & Timeline):
            - اكتب قصة سردية كاملة بالتسلسل الزمني، زي رواية قصيرة
            - سد أي ثغرات منطقية قد تكون موجودة في القصة عشان تكون محكمة تماماً
            - كل حدث فيه: الوقت بالدقيقة، المكان، مين كان فين، إيه اللي حصل بالظبط
            - القصة لازم تشرح الدوافع الكاملة لكل شخص
            - كل لاعب بريء لازم يكون عنده:
              * دافع حقيقي وقوي يخليه يبان مشبوه (حاجة حصلت فعلاً في القصة)
              * alibi صلب يبرئه لو الناس ربطوا الأدلة صح
              * سر شخصي مش علاقة له بالجريمة بس بيضيف شبهة

            ### تانياً — بيانات اللاعبين (players):
            لكل لاعب:
            - role: "criminal" أو "innocent"
            - characterDescription: وصف الشخصية وعلاقتها بالضحية
            - suspicionReason: الدافع القوي اللي بيخليه يبان مشبوه أو السر الشخصي
            - personalSecret: السر الشخصي الزيادة (للأبرياء)
            - alibi: مين وأمتى كان معاه (للأبرياء) — لازم يكون في القصة
            - للمجرم: coverStory, alibiCrack, tacticalNote, knowledgeAboutOthers
            - للأبرياء: location (وقت الجريمة), blindSpot (حاجة مش شافها)

            ### تالتاً — ملخص سري للـ Game Master (master_timeline):
            - يوضح التسلسل الزمني الدقيق للجريمة خطوة بخطوة بالدقائق

            الرد JSON بس، بالعامية المصرية، بدون أي نص زيادة.
            الـ JSON لازم يحتوي على: fullNarrative, setting, crimeBriefing, groundTruth, master_timeline, players

            ⚠️ مهم جداً: الـ players لازم يكون JSON object — كل مفتاح هو اسم اللاعب بالظبط:
            مثال:
            "players": {
                "%s": {"role": "...", "characterDescription": "...", ...}
            }
            ممنوع تستخدم array أو أسماء تانية زي "اللاعب الأول" — لازم اسم اللاعب الحقيقي.
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
            بناءً على القصة دي:
            %s

            ## المطلوب: استخرج الأدلة المادية الملموسة (Tangible Evidence) من القصة.
            ⚠️ ممنوع تخترع أي معلومة مش موجودة فعلاً في السرد.

            ### القواعد الإلزامية للأدلة:
            - كل تصنيف (type) إلزامي: CRIMINAL أو SUSPICION أو ALIBI أو RED_HERRING.
            - ابتكر من 3 إلى 5 أدلة مادية ملموسة (مثلاً إيصال مطعم، طين على حذاء، رسالة نص محترقة، قطعة قماش، بصمة روچ).
            - لا تجعل الأدلة تفضح القاتل بشكل مباشر وسهل.
            - كل دليل مادي لازم يربط شخصية معينة بمسرح الجريمة أو ينفي عنها حجة غيابها (Alibi).
            - النص جوا الدليل (clue) لازم يشرح الدليل اتمسك فين، وإيه الدلالة الخفية بتاعته.

            ### قواعد التوزيع:
            - كل بريء لازم عنده SUSPICION clue واحد على الأقل
            - كل SUSPICION clue لازم يقابله ALIBI clue يبرئ صاحبه
            - الـ ALIBI لازم يكون عند لاعب تاني (مش صاحب الـ SUSPICION)
            - الأدلة CRIMINAL لازم ٣ على الأقل ومش بتظهر كلها من أول جولة

            ### ⚠️ قاعدة التوزيع الإلزامية — تنوع الشبهة:
            - الأدلة لازم تتوزع من أول جولة على لاعبين مختلفين — ممنوع الأدلة الأولى كلها تشاور على نفس الشخص
            - كل لاعب يلاقي دليله لازم يشك في شخص مختلف عن اللاعب التاني
            - كل SUSPICION clue لبريء لازم يقابله ALIBI clue مع لاعب تاني يبرئه

            ### الحقول الإلزامية لكل دليل:
            - holder: اسم اللاعب اللي هيلاقي الدليل ده (لازم يكون من اللعيبة: %s)
            - type: CRIMINAL / SUSPICION / ALIBI / RED_HERRING
            - clue: نص الدليل المادي بضمير المتكلم (مثال: "لقيت إيصال مطعم مجعد واقع جنب الباب مكتوب عليه كذا...")
            - targets: اسم اللاعب اللي الدليل ده بيخصه أو بيكشف كذبته
            - hook_player: 🚨🚨 تنبيه شديد اللهجة: اسم اللاعب اللي الشهادة بتاعته هتدعم أو تكذب الدليل ده.
              ⚠️ إلزامي وحتمي: يجب أن يكون الاسم حصرياً واحداً من هؤلاء اللاعبين فقط: [%s].
              ممنوع نهائياً وباتاً استخدام أي شخصيات ثانوية، خدم، عمال، أو أي اسم غير موجود في القائمة السابقة.
            - hook_sentence: الجملة اللي هتكون موجودة في شهادة الـ hook_player (⚠️ إلزامي: لازم تتكتب بصيغة المتكلم "أنا" زي: "أنا رحت كذا" مش "هو راح").
            - chain_connects_to: اسم الدليل التاني المرتبط بيه
            - narrative_source: الجملة من القصة اللي استخرجت منها الدليل ده

            الرد JSON بس: {"clues": [...]}
            """,
            foundation.path("fullNarrative").asText(foundation.toString()),
            playersStr, playersStr // نمرر الأسماء مرتين
        );
    }

    private String buildTestimoniesPrompt(JsonNode foundation, JsonNode clues) {
        return String.format("""
            بناءً على القصة والأدلة المادية دول:

            القصة الكاملة:
            %s

            الأدلة والهوكات المطلوبة:
            %s

            ## المطلوب: اكتب شهادات المشتبه بهم (Witness Testimonies) بالعامية المصرية.

            ### قواعد إلزامية لكل شهادة:
            - اكتب شهادة رسمية قصيرة على لسان كل لاعب (بصيغة المتكلم) 3-4 جمل بس.
            - كل شهادة لازززززم تحتوي على:
              أ) حجة غيابهم (Alibi) وقت الجريمة.
              ب) علاقتهم بالضحية.
              ج) تناقض بسيط أو "نصف كذبة" تتقاطع مع شهادة شخصية تانية أو دليل مادي، لتثير الشك.
            - كل شهادة لازم تحتوي بالنص (Copy & Paste) على الـ hook_sentences المطلوبة من اللاعب ده.
            - الشهادة تبان طبيعية ومقنعة.
            - المجرم شهادته تحتوي على cover story محكم مع ثغرة مخفية واحدة.

            ⚠️ مهم: استخدم أسماء اللعيبة الحقيقية بالظبط زي ما هي في القصة.

            الرد JSON بس: {"testimonies": {"اسم اللاعب": "الشهادة..."}}
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
    // Gemini API
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode callAndParse(String userPrompt) throws Exception {
        return objectMapper.readTree(extractJson(callGeminiApi(userPrompt)));
    }

    private String callGeminiApi(String userPrompt) {
        Map<String, Object> body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
            "contents",           List.of(Map.of("role", "user",
                                                 "parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig",   GENERATION_CONFIG
        );

        String raw = geminiWebClient.post()
            .uri("/models/{m}:generateContent?key={k}", model, apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return extractText(raw);
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

    private Optional<Player> findPlayer(GameRoom room, String name) {
        String key = name.trim().replaceAll("\\s+", " ");
        // Exact match first
        Optional<Player> exact = room.getAllPlayers().stream()
            .filter(p -> p.getName().trim().replaceAll("\\s+", " ").equals(key))
            .findFirst();
        if (exact.isPresent()) return exact;

        // Fuzzy: contains-based fallback
        return room.getAllPlayers().stream()
            .filter(p -> {
                String pName = p.getName().trim().replaceAll("\\s+", " ");
                return pName.contains(key) || key.contains(pName);
            })
            .findFirst();
    }
}