package br.com.vegas.independente

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox

/** Per-widget display preference. Cancelling leaves the launcher with RESULT_CANCELED. */
class WidgetConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || provider?.packageName != packageName ||
            provider.className != VegasWidget::class.java.name) {
            finish(); return
        }
        setContentView(R.layout.widget_config)
        val detail = findViewById<CheckBox>(R.id.widget_show_hint)
        detail.isChecked = getSharedPreferences(VegasWidget.WIDGET_PREFS, MODE_PRIVATE)
            .getBoolean("detail_$widgetId", true)
        findViewById<Button>(R.id.widget_save).setOnClickListener {
            getSharedPreferences(VegasWidget.WIDGET_PREFS, MODE_PRIVATE).edit()
                .putBoolean("detail_$widgetId", detail.isChecked).apply()
            VegasWidget.updateOne(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
