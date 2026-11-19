package ua.magerko.olxradar.data.network

import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch

interface OlxSearchService {
    suspend fun search(watch: Watch, limit: Int = 5): List<Listing>
}
