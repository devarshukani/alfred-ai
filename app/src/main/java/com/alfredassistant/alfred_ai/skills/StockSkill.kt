package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.StockAction

class StockSkill(stockAction: StockAction) : Skill {
    override val id = "stock"
    override val name = "Stock Market"
    override val description = """Stock market prices, quotes, and charts. Get real-time stock prices with historical chart data.
Use when user asks about stock prices, shares, market, portfolio, Sensex, Nifty, or any company stock.
Supports Indian stocks (NSE/BSE) and US stocks. Always show results with a visual chart card."""
    override val tools = stockAction.toolDefs()
}
