package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.PaymentAction

class PaymentSkill(paymentAction: PaymentAction) : Skill {
    override val id = "payment"
    override val name = "Payments & UPI"
    override val description = """Payments and money transfers via UPI. Launch payment apps (GPay, PhonePe, Paytm, etc.),
initiate UPI payments using phone numbers, list available payment apps.
Use when user says pay, send money, UPI, or mentions a payment app."""
    override val tools = paymentAction.toolDefs()
}
