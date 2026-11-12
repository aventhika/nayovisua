package nayomy.visuals.gui;

import nayomy.visuals.module.AtmosphereModule;
import nayomy.visuals.module.EffectsModule;
import nayomy.visuals.module.WatermarkModule;
import nayomy.visuals.module.WetWorldModule;
import nayomy.visuals.render.AtmosphereRender;
import nayomy.visuals.render.Fonts;
import nayomy.visuals.render.GlassRenderer;
import nayomy.visuals.render.LogoRenderer;
import nayomy.visuals.render.ShapeRenderer;
import nayomy.visuals.render.WetWorldRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ClickGui extends Screen {

    // Размер окна 850x500 физических пикселей (экран 1920x1080). Minecraft считает в GUI-пикселях,
    // поэтому делим на GUI-масштаб: при масштабе 2 это 425x250, при 1 — 850x500.
    // Меньше MIN_* вёрстка (боковая панель + две колонки карточек) не помещается.
    public static final int TARGET_PX_W = 850;
    public static final int TARGET_PX_H = 500;
    private static final int MIN_W = 425;
    private static final int MIN_H = 250;
    public static final int CORNER_RADIUS = 6;

    // стекло
    private static final int BLUR_RADIUS = 13;
    private static final int TINT_ALPHA = 235;
    private static final int WINDOW_BORDER_ALPHA = 0;

    // боковая панель
    private static final int SIDEBAR_W = 104;
    private static final int PAD = 8;
    private static final int LOGO_H = 22;
    private static final int HEAD_SIZE = 16;
    private static final int HEAD_RADIUS = 4;
    private static final int GROUP_H = 12;
    private static final int ITEM_H = 15;
    private static final int ITEM_ICON_X = 11;   // центр иконки от левого края плашки
    private static final int ITEM_TEXT_X = 20;   // начало текста от левого края плашки
    // размеры текста (GUI-пиксели)
    private static final float GROUP_TEXT_SIZE = 6.5f; // название категории (Visuals, Settings...)
    private static final float ITEM_TEXT_SIZE = 7.5f;  // название подкатегории (Render, Misc...)
    private static final float HEAD_TEXT_SIZE = 8f;    // «Settings / Misc» в шапке и ник
    private static final float TITLE_SIZE = 8f;        // названия функций и настроек
    private static final float DESC_SIZE = 7f;         // описания
    private static final float VALUE_SIZE = 7.5f;      // значения: режим, число у слайдера, пункты списка
    private static final int TITLE_H = 8;              // высота строки заголовка
    private static final int DESC_LH = 8;              // шаг строк описания
    private static final float LOGO_SIZE = 11f;        // высота логотипа в шапке сайдбара
    private static final float TRI_W = 4f;             // треугольник у названия категории
    private static final float TRI_H = 2.8f;
    private static final float TRI_GAP = 3f;
    private static final int GROUP_GAP = 8;      // отступ после подкаталогов до следующей группы
    private static final int BTN_W = 29;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 2;
    private static final int GRID_BTN_H = 16;     // кнопка Grid Redactor над рядом кнопок
    private static final int BOTTOM_PAD = 6;
    private static final float LINE_THICKNESS = 0.8f;   // divider и обводка карточек (было 0.6)

    // цвета
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LINE = 0x14FFFFFF;          // divider'ы: едва заметные
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_ITEM = 0xFF9C9CA4;
    private static final int TEXT_DIM = 0xFF64646C;
    private static final int SELECT_BG = 0xFF181819;     // активный подкаталог
    private static final int BTN_BG = 0xFF151517;        // кнопки внизу, близко к фону GUI
    private static final int GRID_BTN_HOVER = 0xFF1D1D20; // Grid Redactor под курсором
    private static final int ICON = 0xFFB4B4BC;
    private static final int ACCENT = 0xFFF0701E;        // переключатель включён
    private static final int SWITCH_OFF = 0xFF2B2B2F;    // переключатель выключен: фон
    private static final int KNOB_OFF = 0xFF6A6A72;      // переключатель выключен: кружок
    private static final int CH_BG = 0xFF1D1D20;         // кнопка-стрелка

    // контент (правая часть)
    private static final int SW_W = 20;
    private static final int SW_H = 11;
    private static final int CH = 12;          // размер кнопки-стрелки
    private static final int CARD_PAD = 8;
    private static final int DESC_GAP = 5;     // отступ описания от заголовка (было 3)
    private static final int DESC_BOTTOM = 5;  // отступ описания от нижней обводки (было CARD_PAD = 8)
    private static final int CARD_R = 5;
    private static final int CARD_GAP = 8;
    private static final int COL_GAP = 8;
    private static final int SLIDER_H = 4;     // толщина дорожки слайдера
    private static final int PILL_H = 12;      // высота плашки значения (режим / цвет)
    private static final int OPT_H = 13;       // высота пункта выпадающего списка
    private static final int DROP_GAP = 4;     // зазор между строкой настройки и списком
    private static final int CHEV_ZONE = 9;    // место под стрелку справа в плашке значения
    private static final float DROP_SEC = 0.2f;// скорость раскрытия списка

    // анимация
    private static final float OPEN_MS = 240f;
    private static final float CLOSE_MS = 180f;
    private static final float SLIDE_PX = 16f;
    private static final float EXPAND_SEC = 0.24f;   // сворачивание / разворачивание карточки

    private record Group(String name, String[] items) {}

    private static final Group[] CATALOG = {
            new Group("Visuals", new String[]{"Render", "Player", "Display"}),
            new Group("Settings", new String[]{"Misc"}),
    };

    /**
     * Под-настройка функции. Виды: переключатель (FPS у Watermark), выбор режима по клику,
     * выбор цвета по клику и слайдер. Видимость можно привязать к условию через when().
     */
    private static final class Sub {
        enum Kind { TOGGLE, MODE, COLOR, SLIDER }

        final Kind kind;
        final String title;
        final String desc;
        BooleanSupplier visible = () -> true;

        // TOGGLE
        BooleanSupplier get;
        Runnable toggle;
        // MODE / COLOR: выпадающий список
        String[] options;
        int[] optionColors;       // только для COLOR
        IntSupplier index;
        IntConsumer select;
        boolean open = false;
        float anim = 0f;          // 0 = закрыт, 1 = раскрыт
        // SLIDER
        double min, max, step;
        DoubleSupplier value;
        DoubleConsumer setter;
        Runnable commit;

        /** Переключатель (как раньше). */
        Sub(String title, String desc, BooleanSupplier get, Runnable toggle) {
            this.kind = Kind.TOGGLE;
            this.title = title;
            this.desc = desc;
            this.get = get;
            this.toggle = toggle;
        }

        private Sub(Kind kind, String title) {
            this.kind = kind;
            this.title = title;
            this.desc = "";
        }

        static Sub mode(String title, String[] options, IntSupplier index, IntConsumer select) {
            Sub s = new Sub(Kind.MODE, title);
            s.options = options;
            s.index = index;
            s.select = select;
            return s;
        }

        static Sub color(String title, String[] names, int[] colors, IntSupplier index, IntConsumer select) {
            Sub s = new Sub(Kind.COLOR, title);
            s.options = names;
            s.optionColors = colors;
            s.index = index;
            s.select = select;
            return s;
        }

        static Sub slider(String title, double min, double max, double step,
                          DoubleSupplier value, DoubleConsumer setter, Runnable commit) {
            Sub s = new Sub(Kind.SLIDER, title);
            s.min = min;
            s.max = max;
            s.step = step;
            s.value = value;
            s.setter = setter;
            s.commit = commit;
            return s;
        }

        Sub when(BooleanSupplier visible) {
            this.visible = visible;
            return this;
        }

        String format(double v) {
            return step >= 1 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
        }
    }

    private static List<Sub> visibleSubs(Feature f) {
        List<Sub> out = new ArrayList<>(f.subs.size());
        for (Sub s : f.subs) if (s.visible.getAsBoolean()) out.add(s);
        return out;
    }

    /** Функция: карточка с переключателем и сворачиваемыми настройками. */
    private static final class Feature {
        final String name;
        final String desc;
        final BooleanSupplier enabled;
        final Runnable toggle;
        final List<Sub> subs;
        boolean expanded = false; // по умолчанию свёрнута, включение её не разворачивает
        float anim = 0f;          // 0 = свёрнута, 1 = развёрнута; плавно догоняет expanded

        Feature(String name, String desc, BooleanSupplier enabled, Runnable toggle, List<Sub> subs) {
            this.name = name;
            this.desc = desc;
            this.enabled = enabled;
            this.toggle = toggle;
            this.subs = subs;
        }
    }

    /** Функции вкладки Misc. Новые функции добавляй сюда. */
    private static final List<Feature> MISC_FEATURES = List.of(watermarkFeature(), effectsFeature(), atmosphereFeature(), wetWorldFeature());

    private static Feature watermarkFeature() {
        WatermarkModule m = WatermarkModule.INSTANCE;
        return new Feature("Watermark", "Плашка с информацией об игроке",
                () -> m.enabled,
                () -> { m.enabled = !m.enabled; m.save(); },
                List.of(
                        new Sub("FPS", "Показывать FPS в Minecraft",
                                () -> m.showFps, () -> { m.showFps = !m.showFps; m.save(); }),
                        new Sub("Ping", "Показывать пинг до сервера",
                                () -> m.showPing, () -> { m.showPing = !m.showPing; m.save(); }),
                        new Sub("Server IP", "IP сервера, а в одиночной игре «Локальная игра»",
                                () -> m.showServer, () -> { m.showServer = !m.showServer; m.save(); })
                ));
    }

    private static Feature effectsFeature() {
        EffectsModule e = EffectsModule.INSTANCE;
        return new Feature("Effects", "Показывает наложенные на вас эффекты с таймерами",
                () -> e.enabled,
                () -> { e.enabled = !e.enabled; e.save(); },
                List.of());
    }

    private static Feature atmosphereFeature() {
        AtmosphereModule a = AtmosphereModule.INSTANCE;
        return new Feature("Atmosphere", "Рассвет, пыль, снег и угли",
                () -> a.enabled,
                () -> { a.enabled = !a.enabled; a.save(); AtmosphereRender.onToggle(a.enabled); },
                List.of(
                        Sub.mode("Режим", AtmosphereModule.MODES, () -> a.mode, i -> { a.mode = i; a.save(); }),
                        // рассвет
                        Sub.slider("Плотность", .05, .8, .01,
                                () -> a.density, v -> a.density = (float) v, a::save).when(a::isDawn),
                        Sub.slider("Высота рассеивания", 60, 120, 1,
                                () -> a.scatterHeight, v -> a.scatterHeight = (float) v, a::save).when(a::isDawn),
                        Sub.slider("Солнечные лучи", 0, 100, 1,
                                () -> a.godRays, v -> a.godRays = (float) v, a::save).when(a::isDawn),
                        Sub.slider("Мягкость", 0, 100, 1,
                                () -> a.softness, v -> a.softness = (float) v, a::save).when(a::isDawn),
                        Sub.color("Цвет рассвета", AtmosphereModule.DAWN_COLOR_NAMES, AtmosphereModule.DAWN_COLORS,
                                () -> a.dawnColorIndex, i -> { a.dawnColorIndex = i; a.save(); }).when(a::isDawn),
                        // пыль / снег / угли
                        Sub.slider("Частицы", 0, 100, 1,
                                () -> a.amount, v -> a.amount = (float) v, a::save).when(a::isMood),
                        Sub.slider("Скорость", 10, 300, 5,
                                () -> a.speed, v -> a.speed = (float) v, a::save).when(a::isMood),
                        Sub.slider("Дымка", 0, 100, 1,
                                () -> a.haze, v -> a.haze = (float) v, a::save).when(a::isMood),
                        Sub.slider("Тонировка", 0, 100, 1,
                                () -> a.grade, v -> a.grade = (float) v, a::save).when(a::isMood),
                        Sub.slider("Виньетка", 0, 100, 1,
                                () -> a.vignette, v -> a.vignette = (float) v, a::save).when(a::isMood)
                ));
    }

    private static Feature wetWorldFeature() {
        WetWorldModule w = WetWorldModule.INSTANCE;
        return new Feature("Wet World", "Мокрые блоки: отражения и рябь на поверхностях",
                () -> w.enabled,
                () -> { w.enabled = !w.enabled; w.save(); WetWorldRender.onToggle(w.enabled); },
                List.of(
                        Sub.mode("Качество", WetWorldModule.QUALITIES, () -> w.quality, i -> { w.quality = i; w.save(); }),
                        Sub.slider("Сила отражений", .1, 1.5, .05,
                                () -> w.reflection, v -> w.reflection = (float) v, w::save),
                        Sub.slider("Затемнение", 0, .8, .05,
                                () -> w.darkening, v -> w.darkening = (float) v, w::save),
                        new Sub("Рябь", "Круги на воде от капель",
                                () -> w.ripples, () -> { w.ripples = !w.ripples; w.save(); }),
                        Sub.slider("Скорость ряби", .1, 3, .1,
                                () -> w.rippleSpeed, v -> w.rippleSpeed = (float) v, w::save).when(() -> w.ripples),
                        new Sub("Только в дождь", "Эффект виден, только пока идёт дождь",
                                () -> w.rainOnly, () -> { w.rainOnly = !w.rainOnly; w.save(); })
                ));
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    /** Область слайдера: клик начинает перетаскивание. */
    private record SliderHit(int x, int y, int w, int h, int trackX, int trackW, Sub sub) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<SliderHit> sliderHits = new ArrayList<>();

    // прокрутка правой части
    private static final float SCROLL_STEP = 32f;     // пикселей за один «щелчок» колеса
    private static final float SCROLL_SPEED = 14f;    // больше — быстрее догоняет (меньше инерции)
    private static final int SCROLL_THUMB = 0x55FFFFFF;
    private float scroll = 0f, scrollTarget = 0f, maxScroll = 0f;
    private long lastScrollNs = 0;
    private long lastFrameNs = 0;
    private float frameDt = 0f;
    private int contentHitStart = 0;
    private int viewX, viewY, viewW, viewH;
    private boolean viewValid = false;
    private SliderHit dragging = null;

    // кэши: перенос строк и ширина текста не должны считаться каждый кадр
    private final Map<String, List<String>> wrapCache = new HashMap<>();
    private final Map<String, Integer> widthCache = new HashMap<>();

    private int selectedGroup = 0; // Visuals
    private int selectedItem = 0;  // Render

    private long openStartNs = System.nanoTime();
    private boolean closing = false;
    private long closeStartNs;
    private float closeFromProgress = 1f;
    /** Что открыть, когда закончится анимация закрытия (null — вернуться в игру). */
    private Supplier<Screen> afterClose = null;

    // последняя позиция мыши (для подсветки кнопки Grid Redactor)
    private int lastMouseX = -1, lastMouseY = -1;
    private float lastOffsetY = 0f;

    public ClickGui() {
        super(Component.literal("Nayomy Visuals"));
    }

    @Override
    protected void init() {
        super.init();
        // init вызывается и при смене размера окна — кэши сбрасываем
        wrapCache.clear();
        widthCache.clear();
    }

    /** Закрывает окно с анимацией и открывает редактор сетки HUD. */
    private void openGridEditor() {
        afterClose = () -> new GridEditorScreen(this);
        startClosing();
    }

    /** Вернуть окно после редактора: сбрасывает закрытие и проигрывает анимацию открытия заново. */
    public void reopen() {
        closing = false;
        afterClose = null;
        openStartNs = System.nanoTime();
        dragging = null;
    }

    private float animOffsetY() {
        return lastOffsetY;
    }

    public void startClosing() {
        if (closing) return;
        closeFromProgress = currentProgress();
        closeStartNs = System.nanoTime();
        closing = true;
    }

    @Override
    public void onClose() {
        startClosing();
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && elapsedMs(closeStartNs) >= CLOSE_MS && this.minecraft != null) {
            Supplier<Screen> next = afterClose;
            afterClose = null;
            this.minecraft.setScreen(next != null ? next.get() : null);
        }
    }

    private static float elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000f;
    }

    private float currentProgress() {
        if (closing) {
            float t = Math.min(1f, elapsedMs(closeStartNs) / CLOSE_MS);
            return closeFromProgress * (1f - t * t * t);
        }
        float t = Math.min(1f, elapsedMs(openStartNs) / OPEN_MS);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No fullscreen vanilla blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float p = currentProgress();
        hits.clear();
        sliderHits.clear();

        double guiScale = this.minecraft.getWindow().getGuiScale();
        int width = Math.min(Math.max(MIN_W, (int) Math.round(TARGET_PX_W / guiScale)), this.width - 32);
        int height = Math.min(Math.max(MIN_H, (int) Math.round(TARGET_PX_H / guiScale)), this.height - 32);

        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;

        float offsetY = (1f - p) * SLIDE_PX;
        lastOffsetY = offsetY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        long nowNs = System.nanoTime();
        frameDt = lastFrameNs == 0 ? 0f : Math.min(0.1f, (nowNs - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = nowNs;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, offsetY);

        GlassRenderer.draw(graphics, x, y, width, height, CORNER_RADIUS,
                Math.round(BLUR_RADIUS * p), Math.round(TINT_ALPHA * p),
                Math.round(WINDOW_BORDER_ALPHA * p));

        renderSidebar(graphics, x, y, height, p);
        renderContent(graphics, x, y, width, height, p);

        graphics.pose().popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int h, float p) {
        int line = withAlpha(LINE, p);

        // --- логотип ---
        LogoRenderer.draw(g, x + SIDEBAR_W / 2f, y + LOGO_H / 2f, LOGO_SIZE, withAlpha(WHITE, p));

        // divider'ы
        ShapeRenderer.hLine(g, x, x + SIDEBAR_W, y + LOGO_H, LINE_THICKNESS, line);
        ShapeRenderer.vLine(g, x + SIDEBAR_W, y, y + h, LINE_THICKNESS, line);

        // --- аккаунт: голова скина (со скруглением) + ник ---
        int accY = y + LOGO_H + 8;
        int headX = x + PAD;
        if (this.minecraft != null && this.minecraft.player != null) {
            PlayerFaceRenderer.draw(g, this.minecraft.player.getSkin(),
                    headX, accY, HEAD_SIZE, withAlpha(WHITE, p));
            GlassRenderer.drawInverse(g, headX, accY, HEAD_SIZE, HEAD_SIZE, HEAD_RADIUS,
                    Math.round(BLUR_RADIUS * p), Math.round(TINT_ALPHA * p));
        }
        drawTextF(g, "Nayomy", headX + HEAD_SIZE + 6, centerTop(accY, HEAD_SIZE, HEAD_TEXT_SIZE),
                HEAD_TEXT_SIZE, TEXT_MAIN, p);

        // --- каталог ---
        int cy = accY + HEAD_SIZE + 10;

        for (int gi = 0; gi < CATALOG.length; gi++) {
            Group group = CATALOG[gi];

            float gTextY = cy + 2.5f;
            drawTextF(g, group.name(), x + PAD, gTextY, GROUP_TEXT_SIZE, TEXT_DIM, p);
            // треугольник вниз справа от названия, по центру высоты заглавных
            float triX = x + PAD + Fonts.BODY.width(group.name(), GROUP_TEXT_SIZE) + TRI_GAP;
            float triY = gTextY + GROUP_TEXT_SIZE * 0.4f + 0.7f - TRI_H / 2f;   // чуть ниже центра заглавных
            if (p > 0.08f) {
                ShapeRenderer.polygons(g, triX, triY, TRI_W, TRI_H,
                        TRIANGLE_DOWN, 1f, 1f, withAlpha(TEXT_DIM, p));
            }
            cy += GROUP_H;

            for (int ii = 0; ii < group.items().length; ii++) {
                String item = group.items()[ii];
                boolean selected = gi == selectedGroup && ii == selectedItem;

                int plateX = x + PAD + 3;
                int plateW = SIDEBAR_W - PAD * 2;

                final int fg = gi, fi = ii;
                hits.add(new Hit(plateX, cy, plateW, ITEM_H - 1, () -> {
                    selectedGroup = fg;
                    selectedItem = fi;
                }));

                if (selected) {
                    ShapeRenderer.roundRect(g, plateX, cy, plateW, ITEM_H - 1,
                            4f, withAlpha(SELECT_BG, p));
                }

                drawItemIcon(g, item, plateX + ITEM_ICON_X, cy + (ITEM_H - 1) / 2f,
                        withAlpha(selected ? TEXT_MAIN : ICON, p));

                drawTextF(g, item, plateX + ITEM_TEXT_X,
                        cy + (ITEM_H - 1 - ITEM_TEXT_SIZE) / 2f + ITEM_TEXT_SIZE / 9f,
                        ITEM_TEXT_SIZE, selected ? TEXT_MAIN : TEXT_ITEM, p);
                cy += ITEM_H;
            }
            cy += GROUP_GAP;
        }

        // --- кнопки внизу: поиск / тема / косметика ---
        int totalW = BTN_W * 3 + BTN_GAP * 2;
        int startX = x + (SIDEBAR_W - totalW) / 2;
        int btnY = y + h - BOTTOM_PAD - BTN_H;

        // --- Grid Redactor: на всю ширину ряда кнопок, прямо над ним ---
        int gridY = btnY - BTN_GAP - GRID_BTN_H;
        boolean gridHover = lastMouseX >= startX && lastMouseX < startX + totalW
                && lastMouseY >= gridY + animOffsetY() && lastMouseY < gridY + GRID_BTN_H + animOffsetY();
        ShapeRenderer.roundRect(g, startX, gridY, totalW, GRID_BTN_H, 5f,
                withAlpha(gridHover ? GRID_BTN_HOVER : BTN_BG, p));
        String gridLabel = "Grid Redactor";
        int labelW = textWidth(gridLabel, ITEM_TEXT_SIZE);
        float contentW = GRID_ICON_SIZE + 5 + labelW;
        float contentX = startX + (totalW - contentW) / 2f;
        Fonts.ICONS.drawIcon(g, G_APPS, contentX + GRID_ICON_SIZE / 2f, gridY + GRID_BTN_H / 2f,
                GRID_ICON_SIZE, withAlpha(gridHover ? TEXT_MAIN : ICON, p));
        drawTextF(g, gridLabel, contentX + GRID_ICON_SIZE + 5, centerTop(gridY, GRID_BTN_H, ITEM_TEXT_SIZE),
                ITEM_TEXT_SIZE, gridHover ? TEXT_MAIN : TEXT_ITEM, p);
        hits.add(new Hit(startX, gridY, totalW, GRID_BTN_H, this::openGridEditor));

        for (int i = 0; i < 3; i++) {
            int bx = startX + i * (BTN_W + BTN_GAP);
            ShapeRenderer.roundRect(g, bx, btnY, BTN_W, BTN_H, 5f, withAlpha(BTN_BG, p));
            drawButtonIcon(g, i, bx + BTN_W / 2f, btnY + BTN_H / 2f, withAlpha(ICON, p));
        }
    }

    // ---------------------------------------------------------------- контент

    private void renderContent(GuiGraphics g, int x, int y, int w, int totalH, float p) {
        String tab = CATALOG[selectedGroup].items()[selectedItem];
        boolean misc = tab.equals("Misc");

        // шапка: "Settings / Misc" + горизонтальный divider
        int tx = x + SIDEBAR_W + PAD + 2;
        float ty = centerTop(y, LOGO_H, HEAD_TEXT_SIZE);

        String groupName = CATALOG[selectedGroup].name();
        String sep = " / ";
        int gw = textWidth(groupName, HEAD_TEXT_SIZE), sw = textWidth(sep, HEAD_TEXT_SIZE);
        drawTextF(g, groupName, tx, ty, HEAD_TEXT_SIZE, TEXT_DIM, p);
        drawTextF(g, sep, tx + gw, ty, HEAD_TEXT_SIZE, TEXT_DIM, p);
        drawTextF(g, tab, tx + gw + sw, ty, HEAD_TEXT_SIZE, TEXT_MAIN, p);
        ShapeRenderer.hLine(g, x + SIDEBAR_W, x + w, y + LOGO_H, LINE_THICKNESS, withAlpha(LINE, p));

        contentHitStart = hits.size();
        viewValid = false;
        if (!misc) return;

        // область прокрутки: под шапкой и до низа окна
        int viewTop = y + LOGO_H + 1;
        int viewBottom = y + totalH;
        viewX = x + SIDEBAR_W + 1;
        viewW = w - SIDEBAR_W - 1;
        viewY = viewTop;
        viewH = viewBottom - viewTop;
        viewValid = true;

        // раскладка в две колонки (в целых координатах, без учёта прокрутки):
        // каждая новая карточка встаёт в более короткую
        int areaX = x + SIDEBAR_W + PAD;
        int areaW = w - SIDEBAR_W - PAD * 2;
        int colW = (areaW - COL_GAP) / 2;
        int top = y + LOGO_H + PAD;
        int[] colY = {top, top};      // реальные высоты колонок (с анимацией)
        int[] colBase = {top, top};   // высоты по свёрнутым карточкам: только по ним выбираем колонку,
                                      // чтобы карточки не прыгали между колонками при разворачивании
        int n = MISC_FEATURES.size();
        int[] cardX = new int[n], cardY = new int[n], cardH = new int[n];
        for (int i = 0; i < n; i++) {
            Feature f = MISC_FEATURES.get(i);
            stepAnim(f);
            int col = colBase[0] <= colBase[1] ? 0 : 1;
            cardX[i] = areaX + col * (colW + COL_GAP);
            cardY[i] = colY[col];
            cardH[i] = cardHeight(f, colW);
            colY[col] += cardH[i] + CARD_GAP;
            colBase[col] += baseHeight(f, colW) + CARD_GAP;
        }
        int contentBottom = Math.max(colY[0], colY[1]) - CARD_GAP + PAD;
        maxScroll = Math.max(0, contentBottom - viewBottom);
        updateScroll();

        // карточки рисуем внутри области, сдвинув на дробную прокрутку (текстуры фигур не пересчитываются)
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        g.pose().pushMatrix();
        g.pose().translate(0f, -scroll);
        for (int i = 0; i < n; i++) {
            drawCard(g, MISC_FEATURES.get(i), cardX[i], cardY[i], colW, cardH[i], p);
        }
        g.pose().popMatrix();
        g.disableScissor();

        // зоны нажатия карточек записаны без прокрутки — сдвигаем на текущую
        int shift = (int) Math.floor(scroll);
        for (int i = contentHitStart; i < hits.size(); i++) {
            Hit h = hits.get(i);
            hits.set(i, new Hit(h.x(), h.y() - shift, h.w(), h.h(), h.action()));
        }
        for (int i = 0; i < sliderHits.size(); i++) {
            SliderHit h = sliderHits.get(i);
            sliderHits.set(i, new SliderHit(h.x(), h.y() - shift, h.w(), h.h(), h.trackX(), h.trackW(), h.sub()));
        }

        // индикатор прокрутки
        if (maxScroll > 0) {
            int trackTop = viewTop + 5;
            int trackH = viewBottom - 5 - trackTop;
            float thumbH = Math.max(16f, trackH * (float) viewH / (viewH + maxScroll));
            float thumbY = trackTop + (trackH - thumbH) * Math.max(0f, Math.min(1f, scroll / maxScroll));
            int iy = (int) Math.floor(thumbY);
            g.pose().pushMatrix();
            g.pose().translate(0f, thumbY - iy);
            ShapeRenderer.roundRect(g, x + w - 6, iy, 2, Math.round(thumbH), 1f, withAlpha(SCROLL_THUMB, p));
            g.pose().popMatrix();
        }
    }

    /** Плавная прокрутка: текущее значение экспоненциально догоняет целевое (зависит от времени, не от FPS). */
    private void updateScroll() {
        long now = System.nanoTime();
        float dt = lastScrollNs == 0 ? 0f : Math.min(0.1f, (now - lastScrollNs) / 1_000_000_000f);
        lastScrollNs = now;

        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        float diff = scrollTarget - scroll;
        if (Math.abs(diff) < 0.05f) scroll = scrollTarget;
        else scroll += diff * (1f - (float) Math.exp(-dt * SCROLL_SPEED));
    }

    private boolean inViewport(double mx, double my) {
        return viewValid && mx >= viewX && mx < viewX + viewW && my >= viewY && my < viewY + viewH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!closing && maxScroll > 0 && inViewport(mouseX, mouseY)) {
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - (float) scrollY * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<String> wrap(String text, int width) {
        return wrapCache.computeIfAbsent(width + "|" + text,
                k -> Fonts.BODY.wrap(text, DESC_SIZE, width));
    }

    /** Высота блока "название + описание" с учётом переноса описания. */
    private int blockHeight(String desc, int textW) {
        int lines = desc.isEmpty() ? 0 : wrap(desc, textW).size();
        if (lines == 0) return CARD_PAD + TITLE_H + CARD_PAD;
        return CARD_PAD + TITLE_H + DESC_GAP + lines * DESC_LH + DESC_BOTTOM;
    }

    private int subTextW(int cardW) {
        return cardW - CARD_PAD * 2 - SW_W - 6;
    }

    /** Высота свёрнутой карточки: название + описание. */
    private int baseHeight(Feature f, int w) {
        return blockHeight(f.desc, w - CARD_PAD * 2);
    }

    /** Полная высота всех видимых под-настроек (вместе с разделителями). */
    private int subsHeight(Feature f, int w) {
        int h = 0;
        for (Sub s : visibleSubs(f)) h += 1 + subHeight(s, w);
        return h;
    }

    /** Текущая высота карточки: свёрнутая + доля под-настроек по ходу анимации. */
    private int cardHeight(Feature f, int w) {
        int h = baseHeight(f, w);
        if (f.anim > 0f) h += Math.round(subsHeight(f, w) * ease(f.anim));
        return h;
    }

    /** Сдвигает анимацию карточки к цели (по времени, не по FPS). */
    private void stepAnim(Feature f) {
        if (!f.expanded) for (Sub s : f.subs) s.open = false;   // свернули карточку — списки закрываем
        for (Sub s : f.subs) {
            if (s.kind != Sub.Kind.MODE && s.kind != Sub.Kind.COLOR) continue;
            float t = s.open ? 1f : 0f, st = frameDt / DROP_SEC;
            if (s.anim < t) s.anim = Math.min(t, s.anim + st);
            else if (s.anim > t) s.anim = Math.max(t, s.anim - st);
        }
        float target = f.expanded ? 1f : 0f;
        float step = frameDt / EXPAND_SEC;
        if (f.anim < target) f.anim = Math.min(target, f.anim + step);
        else if (f.anim > target) f.anim = Math.max(target, f.anim - step);
    }

    /** smoothstep: плавный старт и плавное торможение. */
    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /** Толщина обводки как у divider'ов: минимум один физический пиксель при любом GUI-масштабе. */
    private float dividerThickness() {
        double s = this.minecraft.getWindow().getGuiScale();
        return (float) (Math.max(1, Math.round(LINE_THICKNESS * s)) / s);
    }

    private int subHeight(Sub s, int cardW) {
        return switch (s.kind) {
            case TOGGLE -> blockHeight(s.desc, subTextW(cardW));
            case MODE, COLOR -> {
                int base = CARD_PAD + TITLE_H + CARD_PAD;
                yield s.anim > 0f ? base + Math.round((DROP_GAP + dropPanelH(s)) * ease(s.anim)) : base;
            }
            case SLIDER -> CARD_PAD + TITLE_H + 4 + SLIDER_H + CARD_PAD;
        };
    }

    private void drawCard(GuiGraphics g, Feature f, int x, int y, int w, int h, float p) {
        int lh = TITLE_H;

        // фон прозрачный (как у GUI), без тени: только обводка того же цвета, что и divider'ы
        ShapeRenderer.roundRectOutline(g, x, y, w, h, CARD_R, dividerThickness(), withAlpha(LINE, p));

        // название
        drawTextF(g, f.name, x + CARD_PAD, y + CARD_PAD, TITLE_SIZE, TEXT_MAIN, p);

        // справа: переключатель и кнопка-стрелка (свернуть/развернуть)
        float midY = y + CARD_PAD + lh / 2f;
        float swX = x + w - CARD_PAD - SW_W;
        if (!f.subs.isEmpty()) {
            int chX = x + w - CARD_PAD - CH;
            float chY = midY - CH / 2f;
            drawChevronButton(g, chX, chY, ease(f.anim), p);
            hits.add(new Hit(chX - 2, (int) chY - 2, CH + 4, CH + 4, () -> f.expanded = !f.expanded));
            swX = chX - 5 - SW_W;
        }

        float swY = midY - SW_H / 2f;
        drawSwitch(g, swX, swY, f.enabled.getAsBoolean(), p);
        hits.add(new Hit((int) swX - 2, (int) swY - 2, SW_W + 4, SW_H + 4, f.toggle));

        // описание (переносится на новые строки)
        drawLines(g, wrap(f.desc, w - CARD_PAD * 2), x + CARD_PAD, y + CARD_PAD + lh + DESC_GAP, TEXT_DIM, p);

        if (f.anim <= 0.001f) return;

        // под-настройки: обрезаем по текущей высоте карточки и плавно проявляем.
        // Нажимать на них можно только когда карточка полностью развёрнута.
        float q = p * ease(f.anim);
        boolean interactive = f.expanded && f.anim >= 0.98f;
        int baseH = baseHeight(f, w);
        g.enableScissor(x, y + baseH, x + w, y + h);

        int sy = y + baseH;
        for (Sub s : visibleSubs(f)) {
            ShapeRenderer.hLine(g, x + CARD_PAD, x + w - CARD_PAD, sy, LINE_THICKNESS, withAlpha(LINE, q));
            int ry = sy + 1;
            int bh = subHeight(s, w);

            drawTextF(g, s.title, x + CARD_PAD, ry + CARD_PAD, TITLE_SIZE, TEXT_MAIN, q);

            switch (s.kind) {
                case TOGGLE -> {
                    drawLines(g, wrap(s.desc, subTextW(w)), x + CARD_PAD, ry + CARD_PAD + lh + DESC_GAP, TEXT_DIM, q);
                    drawSwitch(g, x + w - CARD_PAD - SW_W, ry + CARD_PAD + lh / 2f - SW_H / 2f,
                            s.get.getAsBoolean(), q);
                    if (interactive) hits.add(new Hit(x, ry, w, bh, s.toggle));
                }
                case MODE, COLOR -> {
                    boolean color = s.kind == Sub.Kind.COLOR;
                    int cur = Math.max(0, Math.min(s.options.length - 1, s.index.getAsInt()));
                    String t = s.options[cur];
                    int extra = color ? 9 : 0;
                    int pw = textWidth(t, VALUE_SIZE) + 12 + extra + CHEV_ZONE;
                    int px = x + w - CARD_PAD - pw;
                    float py = ry + CARD_PAD + lh / 2f - PILL_H / 2f;
                    float e = ease(s.anim);
                    ShapeRenderer.roundRect(g, px, py, pw, PILL_H, 4f, withAlpha(CH_BG, q));
                    if (color) {
                        ShapeRenderer.circle(g, px + 8, py + PILL_H / 2f, 2.8f,
                                withAlpha(s.optionColors[cur] | 0xFF000000, q));
                    }
                    drawTextF(g, t, px + 6 + extra, centerTop(py, PILL_H, VALUE_SIZE), VALUE_SIZE, TEXT_ITEM, q);
                    // стрелка: вниз в закрытом списке, вверх — в открытом
                    Fonts.ICONS.drawIconRotated(g, G_CHEV_RIGHT, px + pw - 7, py + PILL_H / 2f, CHEV_ICON_SIZE,
                            (float) (Math.PI / 2.0 + Math.PI * e), withAlpha(TEXT_ITEM, q));
                    if (interactive) hits.add(new Hit(x, ry, w, CARD_PAD + lh + CARD_PAD, () -> toggleDropdown(f, s)));

                    if (s.anim > 0.001f) {
                        int panelX = x + CARD_PAD, panelW = w - CARD_PAD * 2;
                        int panelY = ry + CARD_PAD + lh + DROP_GAP;
                        float qe = q * e;
                        boolean live = interactive && s.open && s.anim >= 0.98f;
                        float myL = lastMouseY - lastOffsetY + scroll;
                        g.enableScissor(x, ry, x + w, ry + bh);   // список не вылезает за свою строку
                        ShapeRenderer.roundRect(g, panelX, panelY, panelW, dropPanelH(s), 4f, withAlpha(BTN_BG, qe));
                        for (int oi = 0; oi < s.options.length; oi++) {
                            int oy = panelY + 2 + oi * OPT_H;
                            boolean sel = oi == cur;
                            boolean hover = live && inViewport(lastMouseX, lastMouseY)
                                    && lastMouseX >= panelX && lastMouseX < panelX + panelW
                                    && myL >= oy && myL < oy + OPT_H;
                            if (hover) {
                                ShapeRenderer.roundRect(g, panelX + 2, oy, panelW - 4, OPT_H, 3f,
                                        withAlpha(GRID_BTN_HOVER, qe));
                            }
                            int tx0 = panelX + 7;
                            if (color) {
                                ShapeRenderer.circle(g, tx0 + 2.8f, oy + OPT_H / 2f, 2.8f,
                                        withAlpha(s.optionColors[oi] | 0xFF000000, qe));
                                tx0 += 9;
                            }
                            drawTextF(g, s.options[oi], tx0, centerTop(oy, OPT_H, VALUE_SIZE), VALUE_SIZE,
                                    sel || hover ? TEXT_MAIN : TEXT_ITEM, qe);
                            if (sel) {
                                ShapeRenderer.circle(g, panelX + panelW - 8, oy + OPT_H / 2f, 2f,
                                        withAlpha(ACCENT, qe));
                            }
                            if (live) {
                                final int pick = oi;
                                hits.add(new Hit(panelX, oy, panelW, OPT_H, () -> {
                                    s.select.accept(pick);
                                    s.open = false;
                                }));
                            }
                        }
                        g.disableScissor();
                    }
                }
                case SLIDER -> {
                    double v = s.value.getAsDouble();
                    String vt = s.format(v);
                    drawTextF(g, vt, x + w - CARD_PAD - textWidth(vt, VALUE_SIZE),
                            centerTop(ry + CARD_PAD, lh, VALUE_SIZE), VALUE_SIZE, TEXT_ITEM, q);

                    int tx = x + CARD_PAD;
                    int tw = w - CARD_PAD * 2;
                    int ty = ry + CARD_PAD + lh + 4;
                    float frac = (float) Math.max(0, Math.min(1, (v - s.min) / (s.max - s.min)));
                    ShapeRenderer.roundRect(g, tx, ty, tw, SLIDER_H, SLIDER_H / 2f, withAlpha(SWITCH_OFF, q));
                    ShapeRenderer.roundRect(g, tx, ty, Math.max(SLIDER_H, tw * frac), SLIDER_H,
                            SLIDER_H / 2f, withAlpha(ACCENT, q));
                    ShapeRenderer.circle(g, tx + tw * frac, ty + SLIDER_H / 2f, 3.5f, withAlpha(WHITE, q));
                    if (interactive) sliderHits.add(new SliderHit(x, ry, w, bh, tx, tw, s));
                }
            }
            sy = ry + bh;
        }

        g.disableScissor();
    }

    private void drawLines(GuiGraphics g, List<String> lines, int x, int y, int color, float p) {
        if (p <= 0.08f) return;
        for (int i = 0; i < lines.size(); i++) {
            Fonts.BODY.draw(g, lines.get(i), x, y + i * DESC_LH, DESC_SIZE, withAlpha(color, p));
        }
    }

    private static void drawSwitch(GuiGraphics g, float sx, float sy, boolean on, float p) {
        ShapeRenderer.roundRect(g, sx, sy, SW_W, SW_H, SW_H / 2f, withAlpha(on ? ACCENT : SWITCH_OFF, p));
        float kx = on ? sx + SW_W - SW_H / 2f : sx + SW_H / 2f;
        ShapeRenderer.circle(g, kx, sy + SW_H / 2f, SW_H / 2f - 2f, withAlpha(on ? WHITE : KNOB_OFF, p));
    }

    /** Маленькая квадратная кнопка со стрелкой: openness 0 = ">" (свёрнуто), 1 = "v" (развёрнуто), между ними поворот. */
    private static void drawChevronButton(GuiGraphics g, float bx, float by, float openness, float p) {
        ShapeRenderer.roundRect(g, bx, by, CH, CH, 3.5f, withAlpha(CH_BG, p));
        float cx = bx + CH / 2f;
        float cy = by + CH / 2f;
        int c = withAlpha(TEXT_ITEM, p);
        Fonts.ICONS.drawIconRotated(g, G_CHEV_RIGHT, cx, cy, CHEV_ICON_SIZE, (float) (Math.PI / 2.0 * openness), c);
    }

    private int textWidth(String text, float size) {
        return widthCache.computeIfAbsent(size + "|" + text, k -> Fonts.BODY.widthInt(text, size));
    }

    /** Высота панели выпадающего списка. */
    private static int dropPanelH(Sub s) {
        return s.options.length * OPT_H + 4;
    }

    /** Открывает / закрывает список; одновременно в карточке открыт только один. */
    private static void toggleDropdown(Feature f, Sub target) {
        boolean wasOpen = target.open;
        for (Sub o : f.subs) o.open = false;
        target.open = !wasOpen;
    }

    /** Верх строки текста размера size, чтобы он стоял по центру области высотой h, начинающейся с top. */
    private static float centerTop(float top, float h, float size) {
        return top + (h - size) / 2f + size / 9f;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && !closing && currentProgress() > 0.95f) {
            for (int i = sliderHits.size() - 1; i >= 0; i--) {
                SliderHit h = sliderHits.get(i);
                if (inViewport(event.x(), event.y())
                        && event.x() >= h.x() && event.x() < h.x() + h.w()
                        && event.y() >= h.y() && event.y() < h.y() + h.h()) {
                    dragging = h;
                    applySlider(h, event.x());
                    return true;
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (i >= contentHitStart && !inViewport(event.x(), event.y())) continue;
                if (event.x() >= h.x() && event.x() < h.x() + h.w()
                        && event.y() >= h.y() && event.y() < h.y() + h.h()) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void applySlider(SliderHit h, double mouseX) {
        Sub s = h.sub();
        double frac = Math.max(0, Math.min(1, (mouseX - h.trackX()) / h.trackW()));
        double v = s.min + frac * (s.max - s.min);
        v = s.min + Math.round((v - s.min) / s.step) * s.step;
        s.setter.accept(Math.max(s.min, Math.min(s.max, v)));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null && event.button() == 0) {
            applySlider(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            dragging.sub().commit.run(); // сохраняем в конфиг один раз, когда отпустили
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    // msdf-иконки (шрифт icons): коды глифов. Посмотреть остальные можно в msdf/icons.json
    private static final int G_EYE = 110;       // Render
    private static final int G_GAMEPAD = 1040;  // Player
    private static final int G_MONITOR = 106;   // Display
    private static final int G_APPS = 87;       // Misc
    private static final int G_SEARCH = 1060;   // кнопка: поиск
    private static final int G_THEME = 86;      // кнопка: тема (контраст)
    private static final int G_SPARKLES = 1089; // кнопка: косметика
    private static final int G_CHEV_RIGHT = 70; // стрелка (при развороте поворачивается на 90° по часовой)

    private static final float ITEM_ICON_SIZE = 8.5f;
    private static final float BTN_ICON_SIZE = 9f;
    private static final float GRID_ICON_SIZE = 8f;
    private static final float CHEV_ICON_SIZE = 5f;

    /** Иконки подкаталогов. */
    private static void drawItemIcon(GuiGraphics g, String item, float cx, float cy, int c) {
        int glyph = switch (item) {
            case "Render" -> G_EYE;
            case "Player" -> G_GAMEPAD;
            case "Display" -> G_MONITOR;
            default -> G_APPS; // Misc
        };
        Fonts.ICONS.drawIcon(g, glyph, cx, cy, ITEM_ICON_SIZE, c);
    }

    /** Кнопки внизу боковой панели: поиск / тема / косметика. */
    private static void drawButtonIcon(GuiGraphics g, int type, float cx, float cy, int c) {
        int glyph = switch (type) {
            case 0 -> G_SEARCH;
            case 1 -> G_THEME;
            default -> G_SPARKLES;
        };
        Fonts.ICONS.drawIcon(g, glyph, cx, cy, BTN_ICON_SIZE, c);
    }

    private static final float[][] TRIANGLE_DOWN = {{0f, 0f, 1f, 0f, 0.5f, 1f}};

    /** Текст с произвольным размером и дробными координатами. */
    private void drawTextF(GuiGraphics g, String text, float x, float y, float size, int color, float p) {
        if (p <= 0.08f) return;
        Fonts.BODY.draw(g, text, x, y, size, withAlpha(color, p));
    }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}