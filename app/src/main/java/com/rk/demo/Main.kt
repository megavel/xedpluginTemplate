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
    private val FINDER_BAR_ID = 888888

    private var currentMatches: List<Pair<Int, Int>> = emptyList()
    private var currentMatchIndex = 0

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
        } catch (e: Exception) { e.printStackTrace() }
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

        // Base margins
        val gearBaseMargin = 200
        val finderBaseMargin = 300 // Finder bar sits a bit higher normally

        val params = FrameLayout.LayoutParams(150, 150).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 50, gearBaseMargin)
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        
        // --- KEYBOARD DETECTION ---
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom
            val isKeyboardOpen = keypadHeight > screenHeight * 0.15

            if (isKeyboardOpen) {
                params.bottomMargin = gearBaseMargin + keypadHeight
            } else {
                params.bottomMargin = gearBaseMargin
            }
            fab.layoutParams = params

            val finderBar = rootView.findViewById<View>(FINDER_BAR_ID)
            if (finderBar != null) {
                val finderParams = finderBar.layoutParams as FrameLayout.LayoutParams
                if (isKeyboardOpen) {
                    finderParams.bottomMargin = finderBaseMargin + keypadHeight
                } else {
                    finderParams.bottomMargin = finderBaseMargin
                }
                finderBar.layoutParams = finderParams
            }
        }

        rootView?.post {
            try {
                if (activity.findViewById<View>(BUTTON_ID) == null) {
                    rootView.addView(fab, params)
                    fab.bringToFront()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showConverterDialog(activity: Activity) {
        val inputField = EditText(activity).apply { hint = "Enter text/hex/dec here..." }
        val statsView = TextView(activity).apply {
            text = "0 chars"
            textSize = 12f
            setPadding(10, 5, 10, 20)
            setTextColor(Color.GRAY)
        }

        inputField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { statsView.text = "${s.toString().length} chars" }
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
                } else {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Conversion Result", textToCopy)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(activity, "Copied!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val chainCheckBox = CheckBox(activity).apply {
            text = "Chain Mode (Use Result as Input)"
            textSize = 14f
            setPadding(0, 20, 0, 20)
        }

        val buttonContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val actions = listOf(
            "Dec -> Hex" to { s: String -> Converter.decToHex(s) },
            "Hex -> Dec" to { s: String -> Converter.hexToDec(s) },
            "Hex -> Base58" to { s: String -> Converter.toBase58(s) },
            "Base58 -> Hex" to { s: String -> Converter.fromBase58(s) },
            "String -> Base64" to { s: String -> Converter.toBase64(s) },
            "Base64 -> String" to { s: String -> Converter.fromBase64(s) },
            "Endian Swap" to { s: String -> Converter.endianSwap(s) }
        )

        var currentRow: LinearLayout? = null
        actions.forEachIndexed { index, (label, action) ->
            if (index % 2 == 0) {
                currentRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                buttonContainer.addView(currentRow)
            }
            val btn = Button(activity).apply {
                text = label
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(5, 5, 5, 5)
                }
                setOnClickListener {
                    val currentResult = outputText.text.toString()
                    val input = if (chainCheckBox.isChecked && !currentResult.contains("Result will appear") && !currentResult.startsWith("Error")) currentResult else inputField.text.toString()
                    if (input.isNotEmpty()) outputText.text = action(input)
                }
            }
            currentRow?.addView(btn)
        }

        val searchInfoText = TextView(activity).apply {
            text = ""
            textSize = 16f
            setPadding(10, 20, 10, 10)
            setTextColor(Color.parseColor("#008000"))
            visibility = View.GONE
            gravity = Gravity.CENTER
        }

        val showEntriesButton = Button(activity).apply {
            text = "Show Entries >"
            setBackgroundColor(Color.parseColor("#FF018786"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener {
                (this.tag as? AlertDialog)?.dismiss()
                showFinderOverlay(activity)
                if (currentMatches.isNotEmpty()) {
                    highlightMatch(activity, currentMatches[0].first, currentMatches[0].second)
                }
            }
        }

        val searchButton = Button(activity).apply {
            text = "🔍 Regex Search"
            setBackgroundColor(Color.parseColor("#FF6200EE"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val pattern = outputText.text.toString()
                if (pattern.contains("Result will appear here") || pattern.startsWith("Error")) {
                    Toast.makeText(activity, "Get a valid result first!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val matches = scanStrictlyVisibleEditor(activity, pattern)
                if (matches.isNotEmpty()) {
                    currentMatches = matches
                    currentMatchIndex = 0
                    searchInfoText.text = "✅ Found ${matches.size} matches"
                    searchInfoText.visibility = View.VISIBLE
                    showEntriesButton.text = "Show ${matches.size} Entries >"
                    showEntriesButton.visibility = View.VISIBLE
                } else {
                    searchInfoText.text = "❌ No matches found"
                    searchInfoText.setTextColor(Color.RED)
                    searchInfoText.visibility = View.VISIBLE
                    showEntriesButton.visibility = View.GONE
                }
            }
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(inputField)
            addView(statsView)
            addView(outputText)
            addView(chainCheckBox)
            addView(ScrollView(activity).apply { addView(buttonContainer) })
            addView(searchInfoText)
            addView(showEntriesButton)
            addView(searchButton)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("OmniConverter")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        showEntriesButton.tag = dialog
        dialog.show()
    }

    private fun showFinderOverlay(activity: Activity) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.findViewById<View>(FINDER_BAR_ID)?.let { rootView.removeView(it) }

        val barLayout = LinearLayout(activity).apply {
            id = FINDER_BAR_ID
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EE222222"))
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER_VERTICAL
        }
        val infoText = TextView(activity).apply {
            text = "1 / ${currentMatches.size}"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(30, 0, 30, 0)
        }
        val btnPrev = Button(activity).apply {
            text = "◀"
            layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (currentMatches.isEmpty()) return@setOnClickListener
                currentMatchIndex--
                if (currentMatchIndex < 0) currentMatchIndex = currentMatches.size - 1
                infoText.text = "${currentMatchIndex + 1} / ${currentMatches.size}"
                val (start, end) = currentMatches[currentMatchIndex]
                highlightMatch(activity, start, end)
            }
        }
        val btnNext = Button(activity).apply {
            text = "▶"
            layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (currentMatches.isEmpty()) return@setOnClickListener
                currentMatchIndex = (currentMatchIndex + 1) % currentMatches.size
                infoText.text = "${currentMatchIndex + 1} / ${currentMatches.size}"
                val (start, end) = currentMatches[currentMatchIndex]
                highlightMatch(activity, start, end)
            }
        }
        val btnClose = Button(activity).apply {
            text = "✖"
            setTextColor(Color.RED)
            layoutParams = LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { rootView.removeView(barLayout) }
        }
        barLayout.addView(btnPrev)
        barLayout.addView(btnNext)
        barLayout.addView(infoText)
        barLayout.addView(btnClose)

        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            setMargins(50, 0, 50, 300)
        }
        rootView.addView(barLayout, params)
    }

    private fun scanStrictlyVisibleEditor(activity: Activity, pattern: String): List<Pair<Int, Int>> {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val displayMetrics = activity.resources.displayMetrics
        val editor = findStrictlyVisibleEditor(rootView, displayMetrics.widthPixels) ?: return emptyList()
        try {
            val getTextMethod = editor.javaClass.getMethod("getText")
            val textContent = getTextMethod.invoke(editor).toString()
            val regex = Regex(pattern)
            return regex.findAll(textContent).map { Pair(it.range.first, it.range.last + 1) }.toList()
        } catch (e: Exception) { return emptyList() }
    }

    private fun findStrictlyVisibleEditor(view: View, screenWidth: Int): View? {
        if (!view.isShown) return null
        val name = view.javaClass.name.lowercase()
        if (name.contains("editor") || name.contains("edittext")) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val x = location[0]
            val w = view.width
            val isOnScreen = (x >= 0 && x < screenWidth) || (x < 0 && (x + w) > 0)
            if (isOnScreen) {
                try {
                    view.javaClass.getMethod("getText")
                    return view
                } catch (e: Exception) {}
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findStrictlyVisibleEditor(child, screenWidth)
                if (result != null) return result
            }
        }
        return null
    }

    private fun highlightMatch(activity: Activity, start: Int, end: Int) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val displayMetrics = activity.resources.displayMetrics
        val editor = findStrictlyVisibleEditor(rootView, displayMetrics.widthPixels) ?: return
        try {
            val getTextMethod = editor.javaClass.getMethod("getText")
            val textContent = getTextMethod.invoke(editor).toString()
            val (startLine, startCol) = getLineCol(textContent, start)
            val (endLine, endCol) = getLineCol(textContent, end)
            var success = false
            try {
                val getCursorMethod = editor.javaClass.getMethod("getCursor")
                val cursorObj = getCursorMethod.invoke(editor)
                if (cursorObj != null) {
                    val cursorClass = cursorObj.javaClass
                    val setLeft = cursorClass.getMethod("setLeft", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val setRight = cursorClass.getMethod("setRight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    setLeft.invoke(cursorObj, startLine, startCol)
                    setRight.invoke(cursorObj, endLine, endCol)
                    editor.requestFocus()
                    try {
                        val ensureVisible = editor.javaClass.getMethod("ensurePositionVisible", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        ensureVisible.invoke(editor, startLine, startCol)
                    } catch(e: Exception) {}
                    success = true
                }
            } catch (e: Exception) {}
            if (!success) {
                try {
                     val methods = editor.javaClass.methods
                     for (m in methods) {
                         if ((m.name == "setSelection" || m.name == "setSelectionRegion") && m.parameterTypes.size == 4) {
                             m.invoke(editor, startLine, startCol, endLine, endCol)
                             editor.requestFocus()
                             break
                         }
                     }
                } catch(e: Exception) {}
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getLineCol(text: String, index: Int): Pair<Int, Int> {
        var line = 0
        var col = 0
        for (i in 0 until index) {
            if (i >= text.length) break
            if (text[i] == '\n') line++ else col++
            if (text[i] == '\n') col = 0
        }
        return Pair(line, col)
    }

    override fun onUninstalled(extension: Extension) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}