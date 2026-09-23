package com.example.moderncalculator

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.lang.Math.*

class MainActivity : AppCompatActivity() {

    private lateinit var displayTextView: TextView
    private var currentInput = ""
    private var previousValue = 0.0
    private var operation = ""
    private var isNewOperation = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayTextView = findViewById(R.id.displayTextView)

        // أزرار الأرقام
        setupNumberButton(R.id.btn0, "0")
        setupNumberButton(R.id.btn1, "1")
        setupNumberButton(R.id.btn2, "2")
        setupNumberButton(R.id.btn3, "3")
        setupNumberButton(R.id.btn4, "4")
        setupNumberButton(R.id.btn5, "5")
        setupNumberButton(R.id.btn6, "6")
        setupNumberButton(R.id.btn7, "7")
        setupNumberButton(R.id.btn8, "8")
        setupNumberButton(R.id.btn9, "9")
        setupNumberButton(R.id.btnDot, ".")

        // أزرار العمليات الأساسية
        setupOperationButton(R.id.btnAdd, "+")
        setupOperationButton(R.id.btnSubtract, "-")
        setupOperationButton(R.id.btnMultiply, "*")
        setupOperationButton(R.id.btnDivide, "/")

        // أزرار العمليات المتقدمة
        setupAdvancedButton(R.id.btnSqrt, "sqrt")
        setupAdvancedButton(R.id.btnPow, "pow")
        setupAdvancedButton(R.id.btnSin, "sin")
        setupAdvancedButton(R.id.btnCos, "cos")
        setupAdvancedButton(R.id.btnTan, "tan")
        setupAdvancedButton(R.id.btnLog, "log")
        setupAdvancedButton(R.id.btnLn, "ln")
        setupAdvancedButton(R.id.btnFactorial, "fact")
        setupAdvancedButton(R.id.btnPercent, "%")
        setupAdvancedButton(R.id.btnPi, "pi")
        setupAdvancedButton(R.id.btnE, "e")

        // أزرار التحكم
        findViewById<Button>(R.id.btnClear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { backspace() }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { calculateResult() }
        findViewById<Button>(R.id.btnPlusMinus).setOnClickListener { toggleSign() }
    }

    private fun setupNumberButton(buttonId: Int, number: String) {
        findViewById<Button>(buttonId).setOnClickListener {
            if (isNewOperation) {
                currentInput = number
                isNewOperation = false
            } else {
                if (number == "." && currentInput.contains(".")) return@setOnClickListener
                currentInput += number
            }
            updateDisplay()
        }
    }

    private fun setupOperationButton(buttonId: Int, op: String) {
        findViewById<Button>(buttonId).setOnClickListener {
            if (currentInput.isNotEmpty()) {
                if (operation.isNotEmpty() && !isNewOperation) {
                    calculateResult()
                }
                previousValue = currentInput.toDoubleOrNull() ?: 0.0
                operation = op
                isNewOperation = true
            } else if (operation.isNotEmpty()) {
                operation = op
            }
        }
    }

    private fun setupAdvancedButton(buttonId: Int, func: String) {
        findViewById<Button>(buttonId).setOnClickListener {
            when {
                buttonId == R.id.btnPlusMinus -> toggleSign()
                buttonId == R.id.btnPercent -> calculatePercent()
                buttonId == R.id.btnPi -> {
                    currentInput = PI.toString()
                    isNewOperation = true
                    updateDisplay()
                }
                buttonId == R.id.btnE -> {
                    currentInput = E.toString()
                    isNewOperation = true
                    updateDisplay()
                }
                currentInput.isNotEmpty() -> {
                    val value = currentInput.toDoubleOrNull() ?: return@setOnClickListener
                    val result = when (func) {
                        "sqrt" -> sqrt(value)
                        "sin" -> sin(Math.toRadians(value))
                        "cos" -> cos(Math.toRadians(value))
                        "tan" -> tan(Math.toRadians(value))
                        "log" -> log10(value)
                        "ln" -> log(value)
                        "fact" -> factorial(value.toLong())
                        "pow" -> {
                            // عملية الأس تتطلب قيمتين
                            if (previousValue != 0.0) {
                                pow(previousValue, value)
                            } else {
                                value
                            }
                        }
                        else -> value
                    }
                    currentInput = result.toString()
                    operation = ""
                    isNewOperation = true
                    updateDisplay()
                }
            }
        }
    }

    private fun calculateResult() {
        if (currentInput.isEmpty() || operation.isEmpty()) return

        val currentValue = currentInput.toDoubleOrNull() ?: return

        val result = when (operation) {
            "+" -> previousValue + currentValue
            "-" -> previousValue - currentValue
            "*" -> previousValue * currentValue
            "/" -> if (currentValue != 0.0) previousValue / currentValue else Double.NaN
            "pow" -> pow(previousValue, currentValue)
            else -> currentValue
        }

        currentInput = if (result.isNaN()) "خطأ" else result.toString()
        operation = ""
        isNewOperation = true
        updateDisplay()
    }

    private fun calculatePercent() {
        val value = currentInput.toDoubleOrNull() ?: return
        currentInput = (value / 100.0).toString()
        updateDisplay()
    }

    private fun toggleSign() {
        val value = currentInput.toDoubleOrNull() ?: return
        currentInput = (-value).toString()
        updateDisplay()
    }

    private fun clearAll() {
        currentInput = ""
        previousValue = 0.0
        operation = ""
        isNewOperation = true
        updateDisplay()
    }

    private fun backspace() {
        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
            if (currentInput.isEmpty()) currentInput = "0"
            updateDisplay()
        }
    }

    private fun factorial(n: Long): Double {
        if (n < 0) return Double.NaN
        if (n == 0L || n == 1L) return 1.0
        var result = 1.0
        for (i in 2..n) {
            result *= i
        }
        return result
    }

    private fun updateDisplay() {
        displayTextView.text = currentInput.ifEmpty { "0" }
    }
}
