package ua.magerko.olxradar.data.network

import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch

class OlxSearchClient(private val chromiumBridge: ChromiumBridge) : OlxSearchService {
    override suspend fun search(watch: Watch, limit: Int): List<Listing> {
        val searchUrl = OlxParser.buildSearchUrl(watch, offset = 0, limit = limit.coerceAtLeast(10))
        val rawJson = chromiumBridge.fetchJson(searchUrl)
        val allListings = OlxParser.parseListings(rawJson)
        return allListings.filter { OlxParser.matchesWatch(it, watch) }.take(limit)
    }
}
