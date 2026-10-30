package app.farvater.data

object ProbeUrls {
    const val GOOGLE = "https://www.gstatic.com/generate_204"
    const val CLOUDFLARE = "https://cp.cloudflare.com/generate_204"
    const val APPLE = "https://www.apple.com/library/test/success.html"
    const val FIREFOX = "https://detectportal.firefox.com/success.txt"
    val all = setOf(GOOGLE, CLOUDFLARE, APPLE, FIREFOX)
}
