package com.gildedrose

import com.gildedrose.domain.*
import com.gildedrose.foundation.Analytics
import com.gildedrose.persistence.DbConfig
import com.gildedrose.persistence.DbItems
import com.gildedrose.persistence.toDslContext
import com.gildedrose.pricing.PricedStockListLoader
import com.gildedrose.pricing.valueElfClient
import com.gildedrose.updating.Stock
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.map
import java.net.URI
import java.time.Instant
import java.time.ZoneId

val stdOutAnalytics = loggingAnalytics(::println)
val londonZoneId = ZoneId.of("Europe/London")

data class App<TX>(
    val items: Items<TX>,
    val features: Features = Features(),
    val clock: () -> Instant = Instant::now,
    val analytics: Analytics = stdOutAnalytics,
    val pricing: (Item) -> Price?
) {
    companion object {
        operator fun invoke(
            dbConfig: DbConfig,
            features: Features = Features(),
            valueElfUri: URI = URI.create("http://value-elf.com:8080/prices"),
            clock: () -> Instant = Instant::now,
            analytics: Analytics = stdOutAnalytics
        ) = App(
            DbItems(dbConfig.toDslContext()),
            features,
            clock,
            analytics,
            valueElfClient(valueElfUri)
        )
    }

    private val stock = Stock(items, londonZoneId)

    private val pricedLoader = PricedStockListLoader<TX>(
        loading = stock::loadAndUpdateStockList,
        pricing = pricing,
        analytics = analytics
    )

    fun loadStockList(
        now: Instant = clock()
    ): Result<PricedStockList, StockListLoadingError> =
        items.inTransaction {
            pricedLoader.load(now)
        }

    fun deleteItemsWithIds(
        itemIds: Set<ID<Item>>,
        now: Instant = clock(),
    ) {
        items.inTransaction {
            stock.loadAndUpdateStockList(now).map { stockList ->
                val updatedStockList = StockList(now,
                    stockList.items.filterNot { it.id in itemIds }
                )
                if (updatedStockList.items != stockList.items) {
                    items.save(updatedStockList)
                }
            }
        }
    }

    fun addItem(
        newItem: Item,
        now: Instant = clock(),
    ) {
        items.inTransaction {
            stock.loadAndUpdateStockList(now).map { stockList ->
                val updatedStockList = StockList(now,
                    stockList.items + newItem
                )
                items.save(updatedStockList)
            }
        }
    }
}
