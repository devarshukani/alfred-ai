package com.alfredassistant.alfred_ai.tools

import android.util.Log
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stock price lookup using Yahoo Finance v8 API — free, no key needed.
 * Returns real-time quote + historical chart data for the RichCard line_chart block.
 */
class StockAction {

    companion object {
        private const val TAG = "StockAction"
        private const val QUOTE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Get real-time stock quote + chart data.
     * @param symbol Stock symbol (e.g. "TATAMOTORS.NS" for NSE, "RELIANCE.NS", "AAPL")
     * @param range Chart range: "1d", "5d", "1mo", "3mo", "6mo", "1y", "5y"
     */
    suspend fun getStock(symbol: String, range: String = "1mo"): String =
        withContext(Dispatchers.IO) {
            try {
                val interval = when (range) {
                    "1d" -> "5m"
                    "5d" -> "15m"
                    "1mo" -> "1d"
                    "3mo" -> "1d"
                    "6mo" -> "1wk"
                    "1y" -> "1wk"
                    "5y" -> "1mo"
                    else -> "1d"
                }

                val url = "$QUOTE_URL/$symbol?range=$range&interval=$interval&includePrePost=false"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "AlfredAI/1.0")
                    .build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                if (!resp.isSuccessful) {
                    Log.e(TAG, "Yahoo Finance error ${resp.code}: ${body.take(200)}")
                    return@withContext "Stock lookup failed. Try adding .NS for NSE or .BO for BSE (e.g. TATAMOTORS.NS)."
                }

                val json = JSONObject(body)
                val chart = json.getJSONObject("chart")
                val error = chart.optJSONArray("error")
                if (error != null && error.length() > 0) {
                    return@withContext "Could not find stock: $symbol. Try TATAMOTORS.NS for NSE or TATAMOTORS.BO for BSE."
                }

                val result = chart.getJSONArray("result").getJSONObject(0)
                val meta = result.getJSONObject("meta")

                val currentPrice = meta.optDouble("regularMarketPrice", 0.0)
                val previousClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", 0.0))
                val currency = meta.optString("currency", "INR")
                val exchangeName = meta.optString("exchangeName", "")
                val shortName = meta.optString("shortName", symbol)

                val change = currentPrice - previousClose
                val changePct = if (previousClose > 0) (change / previousClose) * 100 else 0.0

                // Extract chart data points
                val timestamps = result.optJSONArray("timestamp")
                val indicators = result.getJSONObject("indicators")
                val quotes = indicators.getJSONArray("quote").getJSONObject(0)
                val closes = quotes.optJSONArray("close")

                val chartPoints = JSONArray()
                if (timestamps != null && closes != null) {
                    for (i in 0 until timestamps.length()) {
                        val closeVal = closes.optDouble(i, Double.NaN)
                        if (!closeVal.isNaN()) {
                            chartPoints.put(JSONObject().apply {
                                put("timestamp", timestamps.getLong(i))
                                put("price", String.format("%.2f", closeVal).toDouble())
                            })
                        }
                    }
                }

                // Build response
                JSONObject().apply {
                    put("symbol", symbol)
                    put("name", shortName)
                    put("exchange", exchangeName)
                    put("currency", currency)
                    put("current_price", String.format("%.2f", currentPrice).toDouble())
                    put("previous_close", String.format("%.2f", previousClose).toDouble())
                    put("change", String.format("%.2f", change).toDouble())
                    put("change_percent", String.format("%.2f", changePct).toDouble())
                    put("range", range)
                    put("chart_points", chartPoints)
                    put("chart_points_count", chartPoints.length())
                }.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Stock lookup failed", e)
                "Stock lookup failed: ${e.message?.take(60)}"
            }
        }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "get_stock",
            description = """Get real-time stock price and historical chart data. Returns current price, change, and chart data points for visual display.
For Indian stocks, append .NS (NSE) or .BO (BSE) to the symbol. Examples: TATAMOTORS.NS, RELIANCE.NS, INFY.NS, TCS.NS, HDFCBANK.NS.
For US stocks, use plain symbol: AAPL, GOOGL, MSFT, TSLA.
Always use show_card with a line_chart block to display the price chart visually.""",
            parameters = listOf(
                Param(name = "symbol", type = "string", description = "Stock ticker symbol (e.g. TATAMOTORS.NS, AAPL, RELIANCE.BO)"),
                Param(name = "range", type = "string", description = "Chart time range: 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y. Default: 1mo",
                    enum = listOf("1d", "5d", "1mo", "3mo", "6mo", "1y", "5y"))
            ),
            required = listOf("symbol")
        ) { args ->
            getStock(
                args.getString("symbol"),
                args.optString("range", "1mo")
            )
        }
    )
}
