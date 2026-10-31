package com.jarvis.urdu

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {
    companion object {
        @Volatile private var armedUntil = 0L
        fun arm() { armedUntil = System.currentTimeMillis() + 25000 }
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull() ?: find(root)
        if (node != null && click(node)) armedUntil = 0
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        while (x != null) {
            if (x.isClickable && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            x = x.parent
        }
        return false
    }

    private fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val d = n.contentDescription?.toString()?.lowercase() ?: ""
        if (d == "send" || d == "بھیجیں") return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val r = find(c)
            if (r != null) return r
        }
        return null
    }
}
