package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.CalculatorAction

class CalculatorSkill(calculatorAction: CalculatorAction) : Skill {
    override val id = "calculator"
    override val name = "Calculator & Unit Conversion"
    override val description = """Math calculations and unit conversions. Evaluate mathematical expressions with +, -, *, /, ^, %, parentheses.
Convert between units: length (km, miles, m, ft), weight (kg, lbs), temperature (celsius, fahrenheit), volume (liters, gallons), speed, area.
Use when user asks to calculate, compute, convert, or do math."""
    override val tools = calculatorAction.toolDefs()
}
