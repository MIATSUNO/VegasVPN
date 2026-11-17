package br.com.vegas.independente

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** Shows catalog state only; clicking opens Vegas, never starts a network request. */
class VegasWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateOne(context, manager, id)
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            val detail = prefs.getBoolean("detail_$oldId", true)
            edit.remove("detail_$oldId").putBoolean("detail_$newId", detail)
        }
        edit.apply()
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("detail_$it") }
        edit.apply()
    }
    companion object {
        const val WIDGET_PREFS = "widget_options_v1"
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VegasWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }
        fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_vegas)
            val state = CatalogRepository.state(context)
            val label = when (state) {
                CatalogState.Status.IDLE -> R.string.widget_idle
                CatalogState.Status.LOADING -> R.string.widget_loading
                CatalogState.Status.SUCCEEDED -> R.string.widget_success
                CatalogState.Status.FAILED -> R.string.widget_failed
                CatalogState.Status.INTERRUPTED -> R.string.widget_interrupted
                else -> R.string.widget_idle
            }
            views.setTextViewText(R.id.widget_catalog_state, context.getString(label))
            val detail = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getBoolean("detail_$id", true)
            views.setViewVisibility(R.id.widget_hint, if (detail) View.VISIBLE else View.GONE)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
