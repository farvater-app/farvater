package app.farvater.core.catalog

import app.farvater.R
import app.farvater.core.model.SourceMode

data class CatalogSource(
    val id: String,
    val titleRes: Int,
    val author: String,
    val homepage: String,
    val licenseRes: Int,
    val descriptionRes: Int,
    val mirrors: List<String>,
    val mode: SourceMode,
    val forum: String? = null,
)

object BuiltInCatalog {
    val sources: List<CatalogSource> = listOf(
        CatalogSource(
            id = "zieng2-universal",
            titleRes = R.string.catalog_wl_universal,
            author = "zieng2",
            homepage = "https://github.com/zieng2/wl",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_universal_description,
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
            titleRes = R.string.catalog_rjsxrd,
            author = "whoahaow",
            homepage = "https://github.com/whoahaow/rjsxrd",
            licenseRes = R.string.catalog_mit,
            descriptionRes = R.string.catalog_rjsxrd_description,
            mirrors = listOf(
                "https://raw.githubusercontent.com/whoahaow/rjsxrd/refs/heads/main/githubmirror/bypass/bypass-all.txt",
                "https://cdn.jsdelivr.net/gh/whoahaow/rjsxrd@main/githubmirror/bypass/bypass-all.txt",
            ),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "rkp-wl",
            titleRes = R.string.catalog_rkp_white,
            author = "@RKP_channel",
            homepage = "https://t.me/RKP_channel",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_rkp_white_description,
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/wl"),
            mode = SourceMode.WHITE,
            forum = "https://t.me/urlsources",
        ),
        CatalogSource(
            id = "rkp-bl",
            titleRes = R.string.catalog_rkp_black,
            author = "@RKP_channel",
            homepage = "https://t.me/RKP_channel",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_rkp_black_description,
            mirrors = listOf("https://hub.mos.ru/rkp/sub-roskompozor/raw/main/bl"),
            mode = SourceMode.BLACK,
            forum = "https://t.me/urlsources",
        ),
        CatalogSource(
            id = "kfwl-wl",
            titleRes = R.string.catalog_kfwl_white,
            author = "@qkfwl",
            homepage = "https://t.me/KfWLRus",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_kfwl_white_description,
            mirrors = listOf("https://hub.mos.ru/kfwl/auto/raw/main/wl"),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "kfwl-bl",
            titleRes = R.string.catalog_kfwl_black,
            author = "@qkfwl",
            homepage = "https://t.me/KfWLRus",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_kfwl_black_description,
            mirrors = listOf("https://hub.mos.ru/kfwl/auto/raw/main/bl"),
            mode = SourceMode.BLACK,
        ),
        CatalogSource(
            id = "bunker-wl",
            titleRes = R.string.catalog_bunker_white,
            author = "@K0TLUX",
            homepage = "https://t.me/BUNKER_KOTA",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_bunker_white_description,
            mirrors = listOf("https://hub.mos.ru/akelladejavu/bunker/-/raw/main/WHITE_LIST.txt"),
            mode = SourceMode.WHITE,
        ),
        CatalogSource(
            id = "bunker-bl",
            titleRes = R.string.catalog_bunker_black,
            author = "@K0TLUX",
            homepage = "https://t.me/BUNKER_KOTA",
            licenseRes = R.string.catalog_license_unspecified,
            descriptionRes = R.string.catalog_bunker_black_description,
            mirrors = listOf("https://hub.mos.ru/akelladejavu/bunker/-/raw/main/BLACK_LIST.txt"),
            mode = SourceMode.BLACK,
        ),
    )

    fun byId(id: String): CatalogSource? = sources.firstOrNull { it.id == id }
}
