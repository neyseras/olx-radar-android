package ua.magerko.olxradar.data.network

import kotlinx.coroutines.delay
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.prefs.AppPreferences

class OlxSearchClient(private val chromiumBridge: ChromiumBridge) : OlxSearchService {
    override suspend fun search(watch: Watch, limit: Int): List<Listing> {
        val snap = AppPreferences.state.value
        val options = OlxParser.SearchMatchOptions(
            smartAntiSpam = snap.smartAntiSpam,
            convertForeignCurrency = snap.convertForeignCurrency,
            usdToUahRate = snap.usdToUahRate,
            eurToUahRate = snap.eurToUahRate,
            strictQueryWordMatch = snap.strictQueryWordMatch,
            onlyDelivery = snap.onlyDeliveryGlobal
        )
        val scanPages = snap.scanDepthPages.coerceIn(1, 3)
        val collected = mutableListOf<Listing>()
        val seenIds = HashSet<Long>()
        val pageLimit = if (scanPages > 1) 40 else limit.coerceAtLeast(25)

        for (page in 0 until scanPages) {
            val searchUrl = OlxParser.buildSearchUrl(watch, offset = page * 40, limit = pageLimit)
            val rawJson = try {
                chromiumBridge.fetchJson(searchUrl)
            } catch (e: Exception) {
                if (page == 0) throw e else break
            }
            val parsed = OlxParser.parseListings(rawJson)
            for (item in parsed) {
                if (seenIds.add(item.id) && OlxParser.matchesWatchSmart(item, watch, options)) {
                    collected.add(item)
                }
            }
            if (collected.size >= limit || parsed.size < pageLimit) break
            if (page < scanPages - 1) delay(600L)
        }
        return collected.take(limit)
    }
}
