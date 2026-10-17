package app.farvater.core.catalog

import app.farvater.core.model.SourceMode

data class CatalogSource(
    val id: String,
    val title: String,
    val author: String,
    val homepage: String,
    val license: String,
    val description: String,
    val mirrors: List<String>,
    val mode: SourceMode,
    val forum: String? = null,
)

object BuiltInCatalog {
    val sources: List<CatalogSource> = listOf(
        CatalogSource(
            id = "zieng2-universal",
            title = "WL Universal",
            author = "zieng2",
            homepage = "https://github.com/zieng2/wl",
            license = "не указана",
            description = "Полный список под белые списки. Есть российские зеркала.",
            mirrors = listOf(
                "https://raw.githubusercontent.com/zieng2/wl/main/vless_universal.txt",
                "https://codeberg.org/zieng2/wl/raw/branch/main/vless_universal.txt",
                "https://gitlab.com/zieng2/wl/raw/main/vless_universal.txt",
                "https://hub.mos.ru/zieng2/wl/raw/main/list_universal.txt",
                "https://gitverse.ru/api/repos/zieng2/wl/raw/branch/master/list_universal.txt",
            ),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "rjsxrd-bypass",
            title = "rjsxrd",
            author = "whoahaow",
            homepage = "https://github.com/whoahaow/rjsxrd",
            license = "MIT",
            description = "Безопасные конфиги для обхода по SNI и подсетям, обновление ежечасно.",
            mirrors = listOf(
                "https://raw.githubusercontent.com/whoahaow/rjsxrd/refs/heads/main/githubmirror/bypass/bypass-all.txt",
                "https://cdn.jsdelivr.net/gh/whoahaow/rjsxrd@main/githubmirror/bypass/bypass-all.txt",
            ),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "rkp-wl",
            title = "РКП: белые списки",
            author = "@ardnik",
            homepage = "https://t.me/RKP_channel",
            license = "не указана",
            description = "Подписка Анти-РосКомПозор для режима белых списков.",
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/wl"),
            mode = SourceMode.WHITE,
            forum = "https://t.me/urlsources",
        ),
        CatalogSource(
            id = "rkp-bl",
            title = "РКП: чёрные списки",
            author = "@ardnik",
            homepage = "https://t.me/RKP_channel",
            license = "не указана",
            description = "Подписка Анти-РосКомПозор для обычных блокировок.",
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/bl"),
            mode = SourceMode.BLACK,
            forum = "https://t.me/urlsources",
        ),
        CatalogSource(
            id = "kfwl-wl",
            title = "KfWL: белые списки",
            author = "@qkfwl",
            homepage = "https://t.me/KfWLRus",
            license = "не указана",
            description = "Подписка KfWL для режима белых списков.",
            mirrors = listOf("https://hub.mos.ru/kfwl/auto/raw/main/wl"),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "kfwl-bl",
            title = "KfWL: чёрные списки",
            author = "@qkfwl",
            homepage = "https://t.me/KfWLRus",
            license = "не указана",
            description = "Подписка KfWL для обычных блокировок.",
            mirrors = listOf("https://hub.mos.ru/kfwl/auto/raw/main/bl"),
            mode = SourceMode.BLACK,
        ),
        CatalogSource(
            id = "bunker-wl",
            title = "Бункер Кота: белые списки",
            author = "@K0TLUX",
            homepage = "https://t.me/BUNKER_KOTA",
            license = "не указана",
            description = "Подписка Бункера Кота для режима белых списков.",
            mirrors = listOf("https://hub.mos.ru/akelladejavu/bunker/-/raw/main/WHITE_LIST.txt"),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "bunker-bl",
            title = "Бункер Кота: чёрные списки",
            author = "@K0TLUX",
            homepage = "https://t.me/BUNKER_KOTA",
            license = "не указана",
            description = "Подписка Бункера Кота для обычных блокировок.",
            mirrors = listOf("https://hub.mos.ru/akelladejavu/bunker/-/raw/main/BLACK_LIST.txt"),
            mode = SourceMode.BLACK,
        ),
    )

    fun byId(id: String): CatalogSource? = sources.firstOrNull { it.id == id }
}
