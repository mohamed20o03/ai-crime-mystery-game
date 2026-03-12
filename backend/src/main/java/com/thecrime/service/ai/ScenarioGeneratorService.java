package com.thecrime.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thecrime.domain.enums.PlayerRole;
import com.thecrime.domain.model.GameRoom;
import com.thecrime.domain.model.Player;
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

/**
 * Service for generating game scenarios using Google Gemini AI.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioGeneratorService {
    
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;
    
    @Value("${gemini.api-key}")
    private String apiKey;
    
    @Value("${gemini.model}")
    private String model;
    
    private static final String SYSTEM_PROMPT = """
        إنت مدير لعبة لغز الجريمة. شغلتك إنك تعمل سيناريو جريمة وتوزع المعلومات على اللعيبة.
        
        ## ⚠️ مهم جداً — اللهجة:
        كل الكلام لازم يبقى بالعامية المصرية (اللهجة المصرية). متستخدمش فصحى أبداً.
        اكتب زي ما المصريين بيتكلموا في حياتهم اليومية.
        أمثلة: "شاف" مش "رأى"، "راح" مش "ذهب"، "عشان" مش "لأن"، "كان عايز" مش "كان يريد"، "مكانش" مش "لم يكن"
        
        ═══════════════════════════════════════════════════════════
        ## 🧠 الخطوة ١: تسلسل الجريمة الكامل (Master Timeline) — مهم جداً!
        ═══════════════════════════════════════════════════════════
        
        قبل ما تعمل أي شهادات أو أدلة، لازم الأول تبني "تسلسل الجريمة الكامل" (master_timeline).
        ده زي القصة السرية الكاملة اللي كل حاجة في اللعبة هتتبني عليها.
        
        ### التسلسل لازم يحتوي على:
        - ٦-١٠ أحداث مرتبة بالزمن
        - كل حدث لازم يقول بوضوح:
          * فين كل لاعب كان في اللحظة دي
          * مين كان مع مين
          * إيه اللي كل واحد فيهم كان بيعمله
          * إيه اللي كل واحد ممكن يكون شافه أو سمعه من مكانه
        - التسلسل لازم يوضح بالظبط:
          * إمتى وفين الجريمة حصلت
          * إيه اللي المجرم عمله قبل وبعد الجريمة
          * مين كان قريب ومين كان بعيد
          * أنهي لاعب ممكن يكون شاف أو سمع إيه (من مكانه الفعلي)
        
        ### مثال على حدث في التسلسل:
        "لما النور قطع: كريم ومحمد كانوا في الصالة بيتكلموا. سارة كانت نازلة السلم.
        أحمد (المجرم) كان في المطبخ قريب من أوضة الضحية. نور كانت في البلكونة لوحدها.
        من مكان سارة على السلم، كانت تقدر تسمع صوت خطوات سريعة من ناحية المطبخ.
        كريم من الصالة شاف ضل حد بيتحرك في الممر."
        
        ### ليه التسلسل مهم؟
        - كل الشهادات والأدلة لازم تتسحب من التسلسل ده بس — مفيش اختراع من الفراغ
        - لو التسلسل بيقول إن كريم ومحمد كانوا مع بعض، يبقى شهادة كريم لازم تذكر محمد وشهادة محمد لازم تذكر كريم
        - لو سارة كانت على السلم، أدلتها لازم تبقى حاجات ممكن تشوفها أو تسمعها من السلم بس
        
        ═══════════════════════════════════════════════════════════
        ## 🔗 الخطوة ٢: استخراج مترابط (ترابط الأدلة)
        ═══════════════════════════════════════════════════════════
        
        بعد ما تبني التسلسل، استخرج كل الشهادات والأدلة منه بالقواعد دي:
        
        ### قاعدة التوافق المتبادل:
        - لو التسلسل بيقول إن لاعب "أ" ولاعب "ب" كانوا في نفس المكان في نفس الوقت:
          * شهادة "أ" لازم تذكر إنه شاف "ب"
          * شهادة "ب" لازم تذكر إنه شاف "أ"
        - لو لاعب كان لوحده، شهادته تقول كده وأدلته تبقى حاجات سمعها أو لاحظها وهو لوحده
        
        ### قاعدة التقاطع:
        - لو لاعبين مختلفين شافوا نفس الحدث من أماكن مختلفة:
          * كل واحد يوصف اللي شافه من زاويته هو بس
          * مثال: لاعب في الصالة سمع صوت حاجة اتكسرت + لاعب في الممر شاف حد بيجري — دول بيوصفوا نفس اللحظة بس من مكانين مختلفين
        
        ### قاعدة عدم الاختراع (إلزامية — بدون استثناء):
        - كل دليل (privateClues) وكل شهادة (mustSayTestimony) لازم يكون مبني على حدث موجود فعلاً في master_timeline
        - لو دليل أو شهادة بيذكر حقيقة مش موجودة في أي حدث من التسلسل → امسحه فوراً وابدله بحاجة من التسلسل
        - متديش أي لاعب دليل عن حاجة مكانش يقدر يشوفها أو يسمعها من مكانه في التسلسل
        - مثال: لو لاعب كان في البلكونة، ميقدرش يدعي إنه سمع همس في القبو — إلا لو التسلسل بيقول إنه نزل هناك
        - ممنوع تخترع تفاصيل جديدة (أشياء، أصوات، أحداث، مواقف) مش موجودة في master_timeline
        - لو محتاج دليل إضافي، ارجع للتسلسل وشوف إيه اللي ممكن اللاعب يكون لاحظه من مكانه — متأَلفش من دماغك
        
        ### اختبار التحقق لكل دليل:
        - اسأل نفسك: "الدليل ده مبني على أنهي حدث في master_timeline؟"
        - لو مش عارف تحدد الحدث → الدليل مش صالح → غيره
        
        ═══════════════════════════════════════════════════════════
        ## 🚫 الخطوة ٣: ممنوع الأدلة المباشرة (No Smoking Guns)
        ═══════════════════════════════════════════════════════════
        
        ### ممنوع تماماً:
        - محدش يشوف القتل نفسه أو العنف المباشر اللي أدى للقتل
        - محدش يشوف المجرم وهو بيعمل الجريمة
        - محدش يلاقي سلاح الجريمة في إيد المجرم
        - محدش يسمع اعتراف صريح
        - مفيش دليل واحد يكفي لوحده يكشف المجرم
        
        ### الأدلة المسموح بيها (ظرفية بس):
        - سمع خناقة أو صوت عالي من ورا حيطة أو باب
        - شاف حد بيجري أو بيمشي بسرعة وشكله متوتر
        - لاحظ إن حاجة مش في مكانها (كرسي مقلوب، باب مفتوح، حاجة ناقصة)
        - شاف حد دخل مكان معين قبل ما النور يقطع أو قبل ما الجريمة تتاكتشف
        - لاحظ إن حد كان لابس حاجة مختلفة أو شكله متغير
        - سمع خطوات أو صوت باب بيتقفل
        - شم ريحة غريبة أو لاحظ حاجة مش طبيعية
        
        ### مثال على أدلة ظرفية كويسة:
        ✅ "سمعت صوتين بيتخانقوا من ورا الباب بس مقدرتش أعرف مين"
        ✅ "شفت حد طالع من الممر بسرعة بس النور كان ضعيف ومعرفتش مين"
        ✅ "لاحظت إن الكرسي في الأوضة كان مقلوب لما دخلت"
        ✅ "لقيت منديل عليه بقعة غريبة جنب الباب"
        
        ### أمثلة على أدلة ممنوعة (Smoking Guns):
        ❌ "شفت أحمد بيخنق الضحية"
        ❌ "لقيت السكينة في شنطة سارة وعليها دم"
        ❌ "سمعت أحمد بيقول 'خلاص الموضوع خلص'"
        ❌ "شفت أحمد طالع من أوضة الضحية وإيده فيها دم"
        
        ═══════════════════════════════════════════════════════════
        ## 👁️ الخطوة ٤: قاعدة الرؤية الشخصية (First-Person Clue Rule) — إلزامية!
        ═══════════════════════════════════════════════════════════
        
        ### القاعدة الذهبية:
        كل دليل لازم يتكتب من وجهة نظر الشخص اللي بيستلمه — يعني "أنا شفت..."، "أنا سمعت..."، "أنا لاحظت..."
        
        ### ممنوع تماماً:
        - متديش لاعب دليل بيتكلم عنه هو نفسه من بره
        - متديش لاعب دليل بيقول "حد شافك" أو "إنت اتشفت" أو "لوحظ عليك"
        
        ### أمثلة:
        لو في التسلسل: "سارة شافت أحمد بيدخل المطبخ"
        ✅ صح — الدليل يروح لسارة: "شفت أحمد داخل المطبخ قبل ما النور يقطع"
        ❌ غلط — الدليل يروح لأحمد: "سارة شافتك داخل المطبخ"
        ❌ غلط — الدليل يروح لأحمد: "اتشاف أحمد وهو داخل المطبخ"
        
        لو في التسلسل: "كريم سمع خطوات من ناحية أوضة سارة"
        ✅ صح — الدليل يروح لكريم: "سمعت خطوات جاية من ناحية أوضة سارة"
        ❌ غلط — الدليل يروح لسارة: "كريم سمع خطواتك"
        
        ### اختبار بسيط قبل ما تبعت أي دليل:
        ١. الدليل بيذكر اسم اللاعب اللي هياخده؟ → لو أيوه، انقله للاعب تاني
        ٢. الدليل بيقول "حد شافك" أو "إنت اتلاحظت"؟ → لو أيوه، اقلبه لوجهة نظر الشخص اللي شاف
        ٣. الدليل بيتكلم عن اللاعب من بره؟ → لو أيوه، غيره لوجهة نظر أولى (أنا شفت، أنا سمعت)
        
        ═══════════════════════════════════════════════════════════
        ## ⚠️ قواعد تصميم المشتبه فيهم
        ═══════════════════════════════════════════════════════════
        
        كل المشتبه فيهم لازم يبقى عندهم أسباب مقنعة للاشتباه فيهم.
        اللعبة عمرها ما تعمل مشتبه فيه باين إنه بريء من الأول.
        
        كل مشتبه فيه لازم يبقى عنده حاجة واحدة على الأقل من دول:
        - خناقة أو مشكلة جديدة مع الضحية
        - خلاف مالي أو دين
        - منافسة في الشغل
        - غيرة أو توتر شخصي
        - اتشاف قريب من مكان الجريمة (حسب التسلسل)
        - عنده مصلحة من موت الضحية
        - يقدر يوصل لمكان الجريمة (حسب التسلسل)
        
        ### توازن الشبهات:
        - على الأقل ٣ مشتبه فيهم لازم يبقى عندهم دوافع مقنعة
        - المجرم الحقيقي ميبقاش الشخصية المشبوهة الوحيدة
        - المشتبه فيهم الأبرياء لازم يبقى عندهم كمان صراعات أو دوافع تخليهم يبانوا مذنبين
        - الهدف: حالة حقيقية من الشك والتوتر
        
        ### ابعد عن الأدوار الواضحة:
        - متعملش شخصيتين واضح إنهم مش مرتبطين بالضحية
        - متخليش شخصية واحدة بس تبان مشبوهة
        - كل المشتبه فيهم لازم يحسوا إنهم مرشحين معقولين للجريمة
        
        ═══════════════════════════════════════════════════════════
        ## 🧩 تصميم الأدلة والصعوبة التدريجية
        ═══════════════════════════════════════════════════════════
        
        - الأدلة لازم تبقى مقسمة ومش مباشرة — كلها ظرفية
        - وزع أجزاء اللغز على كذا لاعب — لازم يتعاونوا عشان يجمعوا الصورة
        - مفيش لاعب لوحده يقدر يحل اللغز
        
        ### ترتيب الصعوبة:
        - الدليل الأول: غامض أوي، ممكن ينطبق على كذا حد (مثال: "سمعت صوت خطوات تقيلة")
        - الدليل التاني: أكتر تحديداً بس لسه مش حاسم (مثال: "لاحظت إن باب المطبخ كان مفتوح")
        - الدليل التالت: بيضيّق الشبهة بس محتاج ربط بأدلة تانية (مثال: "شفت منديل مرمي في الممر عليه ريحة عطر مميزة")
        - الدليل الرابع: الأوضح بس لسه محتاج تجميع مع أدلة قبله (مثال: "لقيت خاتم الضحية تحت كرسي في المطبخ")
        
        ### مثال على تقسيم كويس:
        لاعب ١: "سمعت صوت خطوات تقيلة قبل ما النور يقطع"
        لاعب ٢: "لاحظت إن جزمة حد كانت متوسخة بالطين"
        لاعب ٣: "شفت بقعة طين على سجادة الممر اللي بيوصل للأوضة"
        → لما يجمعوا الأدلة دي مع بعض، هيقدروا يحددوا مين اللي كان ماشي في الطين
        
        ═══════════════════════════════════════════════════════════
        ## 📋 تنسيق الرد (JSON بس — بدون أي كلام تاني):
        ═══════════════════════════════════════════════════════════
        
        {
            "master_timeline": [
                {
                    "event": "وصف الحدث — بالعامية المصرية",
                    "player_positions": {
                        "اسم_لاعب_١": "فين كان وإيه اللي كان بيعمله ومين كان معاه وإيه اللي ممكن يكون شافه/سمعه",
                        "اسم_لاعب_٢": "فين كان وإيه اللي كان بيعمله ومين كان معاه وإيه اللي ممكن يكون شافه/سمعه"
                    }
                }
            ],
            "crimeBriefing": "القصة المعروفة للكل — بالعامية المصرية — فقرة أو اتنين — متقولش مين المجرم",
            "timeline": ["حدث ١ مرجعي", "حدث ٢", "حدث ٣", "حدث ٤"],
            "groundTruth": "الحقيقة الكاملة — بالعامية المصرية",
            "setting": "وصف مختصر للمكان",
            "packages": {
                "اسم_اللاعب": {
                    "role": "criminal أو innocent",
                    "characterDescription": "وصف الشخصية + علاقته بالضحية + سبب الاشتباه — بالعامية المصرية",
                    "suspicionReason": "السبب المحدد للاشتباه — بالعامية المصرية",
                    "mustSayTestimony": "٣-٤ جمل مستخرجة من التسلسل: فين كان + مين شاف + إيه لاحظ — بالعامية المصرية",
                    "privateClues": ["دليل ١ ظرفي من التسلسل", "دليل ٢", "دليل ٣", "دليل ٤"],
                    "coverStory": "للمجرم بس أو null",
                    "physicalState": "للمجرم بس أو null",
                    "tacticalNote": "للمجرم بس أو null",
                    "location": "للأبرياء بس أو null",
                    "blindSpot": "للأبرياء بس أو null",
                    "knowledgeAboutOthers": "للمجرم بس أو null"
                }
            }
        }
        
        ═══════════════════════════════════════════════════════════
        ## ✅ شيكليست نهائية — اتأكد من كل حاجة قبل ما تبعت:
        ═══════════════════════════════════════════════════════════
        
        ١. master_timeline موجود وفيه ٦-١٠ أحداث وكل حدث فيه مكان كل لاعب؟
        ٢. كل شهادة ودليل مستخرج فعلاً من التسلسل — ومفيش حقيقة جديدة مش موجودة في التسلسل؟
        ٣. لو لاعبين كانوا مع بعض في التسلسل، شهاداتهم بتأكد ده؟
        ٤. مفيش دليل مباشر (smoking gun) — كل الأدلة ظرفية؟
        ٥. مفيش لاعب عنده دليل بيتكلم عنه هو من بره (قاعدة الرؤية الشخصية)؟
        ٦. كل الأدلة مكتوبة بضمير المتكلم (أنا شفت، أنا سمعت)؟
        ٧. مفيش دليل واحد كافي لوحده يكشف المجرم؟
        ٨. على الأقل ٣ شخصيات عندهم دوافع مقنعة؟
        ٩. كل الكلام بالعامية المصرية؟
        ١٠. الرد JSON بس من غير أي كلام زيادة؟
        """;
    
    /**
     * Generate a complete scenario for the game
     */
    public void generateScenario(GameRoom room, List<String> playerNames, Consumer<Boolean> callback) {
        int criminalCount = room.getCriminalCount();
        String criminalInstruction = criminalCount > 1 
            ? String.format("- اختار %d مجرمين عشوائي من اللعيبة (المجرمين بيشتغلوا مع بعض)", criminalCount)
            : "- اختار مجرم واحد بس عشوائي";
        
        String userPrompt = String.format("""
            اعمل سيناريو جريمة قتل للعيبة دول: %s
            
            المكان/الموضوع: %s
            عدد المجرمين: %d
            اللغة: العامية المصرية (مهم جداً — كل الكلام لازم يبقى بالعامية المصرية)
            
            ═══════════════════════════════════
            🧠 خطوة ١ — ابني التسلسل الأول:
            ═══════════════════════════════════
            ابدأ بإنك تبني master_timeline كامل (٦-١٠ أحداث).
            كل حدث لازم يحدد فين كل لاعب كان، ومين كان معاه، وإيه اللي ممكن يكون شافه أو سمعه.
            التسلسل ده هو الأساس — كل الشهادات والأدلة لازم تتسحب منه.
            
            ═══════════════════════════════════
            🔗 خطوة ٢ — استخرج من التسلسل:
            ═══════════════════════════════════
            - لو لاعبين كانوا مع بعض، شهاداتهم لازم تأكد ده
            - كل دليل لازم يبقى حاجة اللاعب فعلاً يقدر يشوفها/يسمعها من مكانه في التسلسل
            - متخترعش أدلة من الفراغ — كل حاجة من التسلسل
            - ⛔ ممنوع أي دليل أو شهادة تذكر حقيقة مش موجودة في master_timeline
            - لو دليل بيتكلم عن شيء/صوت/حدث/مكان مش موجود في التسلسل → غيره فوراً
            
            ═══════════════════════════════════
            🚫 خطوة ٣ — ممنوع الأدلة المباشرة:
            ═══════════════════════════════════
            - محدش يشوف القتل نفسه
            - محدش يلاقي سلاح الجريمة في إيد المجرم
            - مفيش دليل واحد كافي لكشف المجرم
            - كل الأدلة لازم تبقى ظرفية (سمع صوت، شاف حد بيجري، لاحظ حاجة غريبة)
            - اللعيبة لازم يجمعوا كذا دليل مع بعض عشان يعرفوا الحقيقة
            
            ═══════════════════════════════════
            👁️ خطوة ٤ — قاعدة الرؤية الشخصية:
            ═══════════════════════════════════
            - كل دليل يتكتب بضمير المتكلم: "شفت..."، "سمعت..."، "لاحظت..."
            - متديش لاعب دليل بيتكلم عنه من بره أبداً
            - لو سارة شافت أحمد، الدليل يروح لسارة مش لأحمد
            
            ═══════════════════════════════════
            ⚠️ قواعد المشتبه فيهم:
            ═══════════════════════════════════
            - كل لاعب لازم يبقى عنده سبب مقنع للاشتباه فيه
            - على الأقل ٣ شخصيات عندهم دوافع قوية
            - متخليش المجرم الحقيقي هو الشخصية الوحيدة المشبوهة
            - وزع الشبهات بالتساوي
            
            اتأكد كمان من:
            %s
            - كل شهادة ٣-٤ جمل مستخرجة من التسلسل — بالعامية المصرية
            - كل لاعب ياخد ٣-٤ أدلة ظرفية من التسلسل — بالعامية المصرية
            - شهادة المجرم فيها ثغرة واحدة مخبية (بس مش واضحة أوي)
            - اعمل master_timeline من ٦-١٠ أحداث مع مكان كل لاعب
            - اعمل timeline عام من ٤-٦ أحداث مرجعية
            - ابعت crimeBriefing يوصف قصة الجريمة — بالعامية المصرية
            
            ═══════════════════════════════════
            ✅ تأكيد أخير قبل ما تبعت:
            ═══════════════════════════════════
            ١. كل دليل وشهادة مطلعين من master_timeline — ومفيش حقيقة جديدة مش موجودة في التسلسل؟
            ٢. لو لاعبين كانوا مع بعض في التسلسل، شهاداتهم بتأكد ده؟
            ٣. مفيش دليل مباشر (smoking gun)؟
            ٤. مفيش لاعب عنده دليل بيتكلم عنه من بره؟
            ٥. كل الأدلة بضمير المتكلم (أنا شفت، أنا سمعت)؟
            ٦. كل الكلام بالعامية المصرية?
            ٧. لو حذفت master_timeline، هل كل دليل لسه ليه مرجع في التسلسل الأصلي؟ لو لأ → غيره
            """,
            String.join("، ", playerNames),
            room.getSetting() != null ? room.getSetting() : "فندق فاخر في القاهرة",
            criminalCount,
            criminalInstruction
        );
        
        try {
            String response = callGeminiApi(userPrompt);
            parseAndAssignPackages(room, response);
            
            // Validate that every player got a package — if any is missing, fail back to LOBBY
            List<String> missing = room.getAllPlayers().stream()
                    .filter(p -> p.getPlayerPackage() == null)
                    .map(Player::getName)
                    .toList();
            
            if (!missing.isEmpty()) {
                log.error("Scenario generation incomplete — missing packages for players: {}. " +
                          "This usually means the AI response was truncated (too many players) " +
                          "or the AI changed a player name.", missing);
                callback.accept(false);
                return;
            }
            
            callback.accept(true);
        } catch (Exception e) {
            log.error("Error generating scenario", e);
            callback.accept(false);
        }
    }
    
    private String callGeminiApi(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("role", "user", "parts", List.of(
                    Map.of("text", SYSTEM_PROMPT + "\n\n" + userPrompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.9,
                "topK", 40,
                "topP", 0.95,
                "maxOutputTokens", 65536,
                "responseMimeType", "application/json"
            )
        );
        
        String response = geminiWebClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        return extractTextFromResponse(response);
    }
    
    private String extractTextFromResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
        }
        return "";
    }
    
    private void parseAndAssignPackages(GameRoom room, String response) {
        try {
            // Extract JSON from response (may have markdown code blocks)
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            
            // Set ground truth and crime briefing
            room.setGroundTruth(root.path("groundTruth").asText());
            room.setCrimeBriefing(root.path("crimeBriefing").asText());
            
            // Store master timeline (hidden — never shown to players)
            if (root.has("master_timeline")) {
                room.setMasterTimeline(root.path("master_timeline").toString());
                log.info("Master timeline stored for room {} ({} events)", 
                        room.getRoomCode(), root.path("master_timeline").size());
            }
            
            // Set setting if present
            if (root.has("setting")) {
                room.setSetting(root.path("setting").asText());
            }
            
            // Parse packages
            JsonNode packages = root.path("packages");
            Iterator<Map.Entry<String, JsonNode>> fields = packages.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String playerName = entry.getKey().trim();
                JsonNode pkg = entry.getValue();
                
                // Find player by name — try exact match first, then normalized whitespace match
                Optional<Player> playerOpt = room.getAllPlayers().stream()
                        .filter(p -> p.getName().trim().equals(playerName))
                        .findFirst();
                
                // Fallback: collapse internal whitespace and compare
                if (playerOpt.isEmpty()) {
                    String normalizedKey = playerName.replaceAll("\\s+", " ");
                    playerOpt = room.getAllPlayers().stream()
                            .filter(p -> p.getName().trim().replaceAll("\\s+", " ").equals(normalizedKey))
                            .findFirst();
                }
                
                if (playerOpt.isEmpty()) {
                    log.warn("AI returned package for unknown player name '{}' — skipping. Known players: {}",
                            playerName,
                            room.getAllPlayers().stream().map(Player::getName).toList());
                }
                
                if (playerOpt.isPresent()) {
                    Player player = playerOpt.get();
                    
                    String roleStr = pkg.path("role").asText().toLowerCase();
                    PlayerRole role = roleStr.contains("criminal") ? PlayerRole.CRIMINAL : PlayerRole.INNOCENT;
                    player.setRole(role);
                    
                    List<String> privateClues = new ArrayList<>();
                    JsonNode cluesNode = pkg.path("privateClues");
                    if (cluesNode.isArray()) {
                        for (JsonNode clue : cluesNode) {
                            privateClues.add(clue.asText());
                        }
                    }
                    
                    PlayerPackage playerPackage = PlayerPackage.builder()
                            .role(role)
                            .characterDescription(pkg.path("characterDescription").asText(null))
                            .suspicionReason(pkg.path("suspicionReason").asText(null))
                            .mustSayTestimony(pkg.path("mustSayTestimony").asText())
                            .privateClues(privateClues)
                            .coverStory(pkg.path("coverStory").asText(null))
                            .physicalState(pkg.path("physicalState").asText(null))
                            .tacticalNote(pkg.path("tacticalNote").asText(null))
                            .location(pkg.path("location").asText(null))
                            .blindSpot(pkg.path("blindSpot").asText(null))
                            .knowledgeAboutOthers(pkg.path("knowledgeAboutOthers").asText(null))
                            .build();
                    
                    player.setPlayerPackage(playerPackage);
                    log.info("Assigned package to player {} with role {}", playerName, role);
                }
            }
            
            // Warn about any players who didn't receive a package
            room.getAllPlayers().forEach(p -> {
                if (p.getPlayerPackage() == null) {
                    log.warn("Player '{}' (id={}) did NOT receive a package — AI may not have included their name",
                            p.getName(), p.getId());
                }
            });
            
        } catch (Exception e) {
            log.error("Error parsing scenario response: {}", response, e);
            throw new RuntimeException("Failed to parse scenario", e);
        }
    }
    
    private String extractJson(String text) {
        // Try to extract JSON from markdown code blocks (with closing ```)
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Handle truncated code blocks (opening ``` but no closing ```)
        Pattern openOnly = Pattern.compile("```(?:json)?\\s*([\\s\\S]*)");
        Matcher openMatcher = openOnly.matcher(text);
        if (openMatcher.find()) {
            return openMatcher.group(1).trim();
        }
        
        // Try to find first { to last } as JSON
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        
        // If no code blocks, assume the whole thing is JSON
        return text.trim();
    }
}
