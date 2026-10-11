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
            id = "igareck-mobile",
            title = "Белые списки, мобильные",
            author = "igareck",
            homepage = "https://github.com/igareck/vpn-configs-for-russia",
            license = "GPL-3.0",
            description = "150 лучших VLESS Reality для мобильного интернета, автотест каждые 2–4 часа.",
            mirrors = listOf(
                "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
                "https://gitlab.com/igareck/vpn-configs-for-russia/-/raw/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
                "https://codeberg.org/igareck/vpn-configs-for-russia/raw/branch/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
                "https://raw.githack.com/igareck/vpn-configs-for-russia/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
            ),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "igareck-cidr",
            title = "Белые списки, проверенные подсети",
            author = "igareck",
            homepage = "https://github.com/igareck/vpn-configs-for-russia",
            license = "GPL-3.0",
            description = "Узлы на адресах VK, Yandex, CDNvideo и Билайна.",
            mirrors = listOf(
                "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-checked.txt",
                "https://gitlab.com/igareck/vpn-configs-for-russia/-/raw/main/WHITE-CIDR-RU-checked.txt",
                "https://codeberg.org/igareck/vpn-configs-for-russia/raw/branch/main/WHITE-CIDR-RU-checked.txt",
                "https://raw.githack.com/igareck/vpn-configs-for-russia/main/WHITE-CIDR-RU-checked.txt",
            ),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "igareck-black-mobile",
            title = "Чёрные списки, мобильные",
            author = "igareck",
            homepage = "https://github.com/igareck/vpn-configs-for-russia",
            license = "GPL-3.0",
            description = "150 лучших конфигов для обычных блокировок, лёгкая подписка для телефона.",
            mirrors = listOf(
                "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/BLACK_VLESS_RUS_mobile.txt",
                "https://gitlab.com/igareck/vpn-configs-for-russia/-/raw/main/BLACK_VLESS_RUS_mobile.txt",
                "https://codeberg.org/igareck/vpn-configs-for-russia/raw/branch/main/BLACK_VLESS_RUS_mobile.txt",
                "https://raw.githack.com/igareck/vpn-configs-for-russia/main/BLACK_VLESS_RUS_mobile.txt",
            ),
            mode = SourceMode.BLACK,
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
            author = "RKP",
            homepage = "https://hub.mos.ru/rkp/sub-roskompozor",
            license = "не указана",
            description = "Подписка Анти-РосКомПозор для режима белых списков.",
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/wl"),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "rkp-bl",
            title = "РКП: чёрные списки",
            author = "RKP",
            homepage = "https://hub.mos.ru/rkp/sub-roskompozor",
            license = "не указана",
            description = "Подписка Анти-РосКомПозор для обычных блокировок.",
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/bl"),
            mode = SourceMode.BLACK,
        ),
    )

    fun byId(id: String): CatalogSource? = sources.firstOrNull { it.id == id }
}
