package com.hortencioflix.tv.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.hortencioflix.tv.R
import com.hortencioflix.tv.data.ActionItem
import com.hortencioflix.tv.data.MetaItem

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = parent.context
        val card = ImageCardView(ctx)
        card.isFocusable = true
        card.setMainImageDimensions(ctx.dp(150), ctx.dp(225))
        card.setInfoAreaBackgroundColor(ctx.themeColor(R.attr.hfPanel))
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as ImageCardView
        val m = item as? MetaItem ?: return
        card.titleText = m.name
        card.contentText = when {
            m.progress > 0f && m.info.isNotEmpty() -> m.info
            m.progress > 0f -> "${(m.progress * 100).toInt()}% visto"
            m.videoId.isNotEmpty() && m.videoId != m.id -> "Continuar"
            else -> if (m.type == "series") "Série" else if (m.type == "movie") "Filme" else m.type
        }
        if (m.poster.isNotEmpty()) card.mainImageView.load(m.poster) { crossfade(true) }
        else card.mainImage = ContextCompat.getDrawable(card.context, R.drawable.banner)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImage = null
    }
}

class ActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = parent.context
        val tv = TextView(ctx)
        tv.layoutParams = ViewGroup.LayoutParams(ctx.dp(200), ctx.dp(90))
        tv.isFocusable = true
        tv.isClickable = true
        tv.gravity = Gravity.CENTER
        tv.textSize = 18f
        tv.setTextColor(ctx.themeColor(R.attr.hfText))
        tv.setBackgroundResource(R.drawable.item_bg)
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (viewHolder.view as TextView).text = (item as? ActionItem)?.title ?: ""
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}
