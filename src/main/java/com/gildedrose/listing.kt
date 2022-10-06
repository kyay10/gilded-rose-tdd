package com.gildedrose

import com.gildedrose.domain.Item
import com.gildedrose.domain.StockList
import com.gildedrose.http.ResponseErrors.withError
import com.gildedrose.persistence.StockListLoadingError
import dev.forkhandles.result4k.*
import org.http4k.core.*
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
private val handlebars = HandlebarsTemplates().HotReload("src/main/java")
private val view = Body.viewModel(handlebars, ContentType.TEXT_HTML).toLens()

fun listHandler(
    clock: () -> Instant,
    zoneId: ZoneId,
    isPricingEnabled: Boolean,
    listing: (Instant) -> Result4k<StockList, StockListLoadingError>
): HttpHandler = { _ ->
    val now = clock()
    val today = LocalDate.ofInstant(now, zoneId)
    listing(now).map { stockList ->
        Response(OK).with(
            view of
                StockListViewModel(
                    now = dateFormat.format(today),
                    items = stockList.map { item ->
                        val priceString = when (val price = item.price) {
                            null -> ""
                            is Success -> price.value?.toString().orEmpty()
                            is Failure -> "error"
                        }
                        item.toMap(today, priceString)
                    },
                    isPricingEnabled = isPricingEnabled
                )
        )
    }.recover { error ->
        Response(INTERNAL_SERVER_ERROR)
            .withError(error)
            .body("Something went wrong, we're really sorry.")
    }
}

private data class StockListViewModel(
    val now: String,
    val items: List<Map<String, String>>,
    val isPricingEnabled: Boolean
) : ViewModel

private fun Item.toMap(now: LocalDate, priceString: String): Map<String, String> = mapOf(
    "id" to id.toString(),
    "name" to name.value,
    "sellByDate" to if (sellByDate == null) "" else dateFormat.format(sellByDate),
    "sellByDays" to this.daysUntilSellBy(now).toString(),
    "quality" to this.quality.toString(),
    "price" to priceString
)

private fun Item.daysUntilSellBy(now: LocalDate): Long =
    if (sellByDate == null) 0 else
        ChronoUnit.DAYS.between(now, this.sellByDate)
