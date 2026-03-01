package com.alfredassistant.alfred_ai.tools

import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef

/**
 * Evaluates math expressions and unit conversions.
 * Uses a simple recursive descent parser — no external dependencies.
 */
class CalculatorAction {

    fun evaluate(expression: String): String {
        return try {
            val sanitized = expression
                .replace("×", "*").replace("÷", "/")
                .replace("x", "*").replace("X", "*")
                .trim()
            val result = ExprParser(sanitized).parse()
            // Format: remove trailing .0 for whole numbers
            if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format("%.6f", result).trimEnd('0').trimEnd('.')
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun convertUnit(value: Double, fromUnit: String, toUnit: String): String {
        val from = fromUnit.lowercase().trim()
        val to = toUnit.lowercase().trim()

        val result = when {
            // Length
            from == "km" && to == "miles" -> value * 0.621371
            from == "miles" && to == "km" -> value * 1.60934
            from == "m" && to == "ft" -> value * 3.28084
            from == "ft" && to == "m" -> value * 0.3048
            from == "cm" && to == "inches" -> value * 0.393701
            from == "inches" && to == "cm" -> value * 2.54
            from == "m" && to == "cm" -> value * 100
            from == "cm" && to == "m" -> value / 100
            from == "km" && to == "m" -> value * 1000
            from == "m" && to == "km" -> value / 1000
            // Weight
            from == "kg" && to == "lbs" -> value * 2.20462
            from == "lbs" && to == "kg" -> value * 0.453592
            from == "g" && to == "oz" -> value * 0.035274
            from == "oz" && to == "g" -> value * 28.3495
            from == "kg" && to == "g" -> value * 1000
            from == "g" && to == "kg" -> value / 1000
            // Temperature
            from == "celsius" && to == "fahrenheit" -> value * 9.0 / 5.0 + 32
            from == "fahrenheit" && to == "celsius" -> (value - 32) * 5.0 / 9.0
            from == "celsius" && to == "kelvin" -> value + 273.15
            from == "kelvin" && to == "celsius" -> value - 273.15
            from == "c" && to == "f" -> value * 9.0 / 5.0 + 32
            from == "f" && to == "c" -> (value - 32) * 5.0 / 9.0
            // Volume
            from == "liters" && to == "gallons" -> value * 0.264172
            from == "gallons" && to == "liters" -> value * 3.78541
            from == "ml" && to == "liters" -> value / 1000
            from == "liters" && to == "ml" -> value * 1000
            // Speed
            from == "kmh" && to == "mph" -> value * 0.621371
            from == "mph" && to == "kmh" -> value * 1.60934
            // Area
            from == "sqm" && to == "sqft" -> value * 10.7639
            from == "sqft" && to == "sqm" -> value * 0.092903
            from == "acres" && to == "hectares" -> value * 0.404686
            from == "hectares" && to == "acres" -> value * 2.47105
            else -> return "Unsupported conversion: $fromUnit to $toUnit"
        }

        val formatted = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format("%.4f", result).trimEnd('0').trimEnd('.')
        }
        return "$formatted $toUnit"
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "evaluate_expression",
            description = "Evaluate a mathematical expression. Supports +, -, *, /, ^, %, parentheses.",
            parameters = listOf(Param(name = "expression", type = "string", description = "The math expression to evaluate")),
            required = listOf("expression")
        ) { args -> "Result: ${evaluate(args.getString("expression"))}" },

        ToolDef(
            name = "convert_unit",
            description = "Convert a value from one unit to another. Supports length, weight, temperature, volume, speed, area.",
            parameters = listOf(
                Param(name = "value", type = "number", description = "The numeric value to convert"),
                Param(name = "from_unit", type = "string", description = "Source unit (e.g. km, lbs, celsius)"),
                Param(name = "to_unit", type = "string", description = "Target unit (e.g. miles, kg, fahrenheit)")
            ),
            required = listOf("value", "from_unit", "to_unit")
        ) { args -> convertUnit(args.getDouble("value"), args.getString("from_unit"), args.getString("to_unit")) }
    )
}

/**
 * Simple recursive descent expression parser.
 * Supports: +, -, *, /, ^, %, parentheses, negative numbers.
 */
private class ExprParser(private val input: String) {
    private var pos = 0

    fun parse(): Double {
        val result = parseExpression()
        if (pos < input.length) throw IllegalArgumentException("Unexpected character: ${input[pos]}")
        return result
    }

    private fun parseExpression(): Double {
        var left = parseTerm()
        while (pos < input.length) {
            skipSpaces()
            if (pos >= input.length) break
            val op = input[pos]
            if (op != '+' && op != '-') break
            pos++
            val right = parseTerm()
            left = if (op == '+') left + right else left - right
        }
        return left
    }

    private fun parseTerm(): Double {
        var left = parsePower()
        while (pos < input.length) {
            skipSpaces()
            if (pos >= input.length) break
            val op = input[pos]
            if (op != '*' && op != '/' && op != '%') break
            pos++
            val right = parsePower()
            left = when (op) {
                '*' -> left * right
                '/' -> left / right
                '%' -> left % right
                else -> left
            }
        }
        return left
    }

    private fun parsePower(): Double {
        var base = parseUnary()
        skipSpaces()
        if (pos < input.length && input[pos] == '^') {
            pos++
            val exp = parseUnary()
            base = Math.pow(base, exp)
        }
        return base
    }

    private fun parseUnary(): Double {
        skipSpaces()
        if (pos < input.length && input[pos] == '-') {
            pos++
            return -parseAtom()
        }
        return parseAtom()
    }

    private fun parseAtom(): Double {
        skipSpaces()
        if (pos < input.length && input[pos] == '(') {
            pos++ // skip (
            val result = parseExpression()
            skipSpaces()
            if (pos < input.length && input[pos] == ')') pos++ // skip )
            return result
        }
        return parseNumber()
    }

    private fun parseNumber(): Double {
        skipSpaces()
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (start == pos) throw IllegalArgumentException("Expected number at position $pos")
        return input.substring(start, pos).toDouble()
    }

    private fun skipSpaces() {
        while (pos < input.length && input[pos] == ' ') pos++
    }
}
