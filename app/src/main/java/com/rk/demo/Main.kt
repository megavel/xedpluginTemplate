package com.rk.demo

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import com.rk.extension.Extension
import com.rk.extension.ExtensionAPI
import java.util.WeakHashMap

@Keep
class Main : ExtensionAPI() {

    private val processedActivities = WeakHashMap<Activity, Boolean>()
    private val BUTTON_ID = 999999

    override fun onPluginLoaded(extension: Extension) {
        val current = getActivityHack()
        if (current != null) {
            setupFloatingButton(current)
            processedActivities[current] = true
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (processedActivities[activity] != true) {
            setupFloatingButton(activity)
            processedActivities[activity] = true
        }
    }

    private fun getActivityHack(): Activity? {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(activityThread) as Map<*, *>
            
            for (activityRecord in activities.values) {
                val activityRecordClass = activityRecord!!.javaClass
                val pausedField = activityRecordClass.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!pausedField.getBoolean(activityRecord)) {
                    val activityField = activityRecordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(activityRecord) as Activity
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun setupFloatingButton(activity: Activity) {
        if (activity.findViewById<View>(BUTTON_ID) != null) return

        val fab = Button(activity).apply {
            id = BUTTON_ID
            text = "⚙️"
            textSize = 20f
            elevation = 100f
            stateListAnimator = null
            setOnClickListener { showConverterDialog(activity) }
        }

        // Define the standard position (Bottom Right)
        val originalBottomMargin = 200
        val params = FrameLayout.LayoutParams(150, 150).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 50, originalBottomMargin)
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        
        // --- NEW: Keyboard Detection Listener ---
        // This watches the screen. If the visible height shrinks (keyboard opens),
        // it pushes the button up.
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            // 1. Get the visible area of the app window
            rootView.getWindowVisibleDisplayFrame(r)
            
            // 2. Calculate the difference between total screen height and visible height
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            // 3. If difference is huge (> 15% of screen), the keyboard is probably up
            if (keypadHeight > screenHeight * 0.15) {
                params.bottomMargin = originalBottomMargin + keypadHeight
            } else {
                params.bottomMargin = originalBottomMargin
            }
            
            // 4. Apply the new position
            fab.layoutParams = params
        }

        rootView?.post {
            try {
                if (activity.findViewById<View>(BUTTON_ID) == null) {
                    rootView.addView(fab, params)
                    fab.bringToFront()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showConverterDialog(activity: Activity) {
        val inputField = EditText(activity).apply { 
            hint = "Enter text/hex/dec here..." 
        }

        val statsView = TextView(activity).apply {
            text = "0 chars"
            textSize = 12f
            setPadding(10, 5, 10, 20)
            setTextColor(Color.GRAY)
        }

        inputField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val charCount = s.toString().length
                statsView.text = "$charCount chars"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val outputText = TextView(activity).apply {
            text = "Result will appear here (Tap to copy)"
            setPadding(40, 40, 40, 40)
            textSize = 18f
            
            val outValue = TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            
            setOnClickListener {
                val textToCopy = text.toString()
                if (textToCopy.contains("Result will appear here")) {
                    Toast.makeText(activity, "Calculate something first!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Conversion Result", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(activity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        val chainCheckBox = CheckBox(activity).apply {
            text = "Chain Mode (Use Result as Input)"
            textSize = 14f
            setPadding(0, 20, 0, 20)
        }

        val buttonContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        fun addButton(label: String, action: (String) -> String) {
            val btn = Button(activity).apply {
                text = label
                setOnClickListener {
                    val currentResult = outputText.text.toString()
                    val placeholder = "Result will appear here (Tap to copy)"
                    
                    val input = if (chainCheckBox.isChecked && 
                                    currentResult != placeholder && 
                                    !currentResult.startsWith("Error")) {
                        currentResult
                    } else {
                        inputField.text.toString()
                    }

                    if (input.isNotEmpty()) {
                        outputText.text = action(input)
                    }
                }
            }
            buttonContainer.addView(btn)
        }

        // --- Button Config ---
        addButton("Dec -> Hex") { Converter.decToHex(it) }
        addButton("Hex -> Dec") { Converter.hexToDec(it) }
        addButton("Hex -> Base58") { Converter.toBase58(it) }
        addButton("Base58 -> Hex") { Converter.fromBase58(it) }
        addButton("String -> Base64") { Converter.toBase64(it) }
        addButton("Base64 -> String") { Converter.fromBase64(it) }
        addButton("Endian Swap (Hex)") { Converter.endianSwap(it) }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(inputField)
            addView(statsView)
            addView(outputText)
            addView(chainCheckBox)
            addView(ScrollView(activity).apply { addView(buttonContainer) })
        }

        AlertDialog.Builder(activity)
            .setTitle("OmniConverter")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onUninstalled(extension: Extension) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}