package nayomy.visuals.render;

/**
 * Все шрифты мода (MSDF). SF Pro не используется.
 * Основной шрифт проекта — Onest (есть кириллица). Пока атласа Onest нет в ресурсах
 * (msdf/onest_*.json + textures/msdf/onest_*.png), автоматически используется Inter.
 * Как сгенерировать атлас — tools/make_onest_atlas.md.
 * У Maison Neue и cosmetics_icons кириллицы нет.
 */
public final class Fonts {

    public static final MsdfFont INTER_BOLD = new MsdfFont("inter_bold");
    public static final MsdfFont INTER_SEMIBOLD = new MsdfFont("inter_semibold");
    public static final MsdfFont INTER_EXTRABOLD = new MsdfFont("inter_extrabold");
    public static final MsdfFont INTER_DISPLAY_BOLD = new MsdfFont("inter_display_bold");
    public static final MsdfFont MAISON_NEUE_EXTENDED_BOLD = new MsdfFont("maison_neue_extended_bold");
    public static final MsdfFont ONEST_SEMIBOLD = new MsdfFont("onest_semibold", "inter_semibold");
    public static final MsdfFont ONEST_BOLD = new MsdfFont("onest_bold", "inter_bold");
    public static final MsdfFont ICONS = new MsdfFont("icons");
    public static final MsdfFont COSMETICS_ICONS = new MsdfFont("cosmetics_icons");

    /** Основной текст интерфейса. */
    public static final MsdfFont BODY = ONEST_SEMIBOLD;
    /** Заголовки. */
    public static final MsdfFont TITLE = ONEST_BOLD;

    /** Базовый размер текста в GUI-пикселях (как у ванильного шрифта). */
    public static final float SIZE = 9f;

    /**
     * Сдвиг текста по вертикали в GUI-пикселях (для размера 9): минус — выше, плюс — ниже.
     * Если текст всё ещё чуть ниже/выше центра — меняйте это число (например -1.5f или -0.5f).
     */
    public static final float Y_OFFSET = -1f;

    private Fonts() {}
}