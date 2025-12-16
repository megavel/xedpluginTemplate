package com.rk.demo

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import com.rk.extension.Extension
import com.rk.extension.ExtensionAPI

@Keep
class Main : ExtensionAPI() {

    // Keep track so we don't add the button twice
    private var isButtonAdded = false

    override fun onPluginLoaded(extension: Extension) {
        // Plugin memory loaded
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isButtonAdded) {
            setupFloatingButton(activity)
            isButtonAdded = true
        }
    }

    private fun setupFloatingButton(activity: Activity) {
        // 1. Create a simple button
        val fab = Button(activity).apply {
            text = "⚙️"
            textSize = 20f
            setOnClickListener { showConverterDialog(activity) }
        }

        // 2. Layout Params to stick it to bottom-right
        val params = android.widget.FrameLayout.LayoutParams(
            150, 150 // Width, Height (approx)
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 50, 200) // Margin from edges
        }

        // 3. Inject into the root view
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(fab, params)
    }

    private fun showConverterDialog(activity: Activity) {
        // Create the Input field
        val inputField = EditText(activity).apply {
            hint = "Enter value here..."
        }

        // Output Display
        val outputText = TextView(activity).apply {
            text = "Result will appear here"
            setPadding(20, 20, 20, 20)
            textSize = 16f
            setTextIsSelectable(true) // Allow copying result
        }

        // Create Buttons container
        val buttonContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Helper to add action buttons
        fun addButton(label: String, action: (String) -> String) {
            val btn = Button(activity).apply {
                text = label
                setOnClickListener {
                    val input = inputField.text.toString()
                    if(input.isNotEmpty()) {
                        outputText.text = action(input)
                    }
                }
            }
            buttonContainer.addView(btn)
        }

        // Add our Conversion Logic
        addButton("Dec -> Hex") { Converter.decToHex(it) }
        addButton("Hex -> Dec") { Converter.hexToDec(it) }
        addButton("Hex -> Base58") { Converter.toBase58(it) }
        addButton("String -> Base64") { Converter.toBase64(it) }
        addButton("Base64 -> String") { Converter.fromBase64(it) }
        addButton("Endian Swap (Hex)") { Converter.endianSwap(it) }

        // Layout the Dialog
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(inputField)
            addView(outputText)
            addView(ScrollView(activity).apply { addView(buttonContainer) })
        }

        // Show it
        AlertDialog.Builder(activity)
            .setTitle("OmniConverter")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    // Required overrides (leave empty)
    override fun onUninstalled(extension: Extension) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}