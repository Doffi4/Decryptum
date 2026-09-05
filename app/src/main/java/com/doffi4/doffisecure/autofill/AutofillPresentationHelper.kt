package com.doffi4.doffisecure.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import coil.Coil
import coil.request.ImageRequest
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.DomainUtils
import kotlin.math.min

object AutofillPresentationHelper {

    /**
     * Loads a circular software Bitmap favicon using Coil for use in RemoteViews IPC.
     */
    suspend fun loadFaviconBitmap(
        context: Context,
        domainOrUrl: String,
        sizeDp: Int = 38
    ): Bitmap? {
        val domain = DomainUtils.extract(domainOrUrl)
        if (domain.isBlank()) return null
        val url = DomainUtils.faviconUrl(domain, size = 128)
        return try {
            val loader = Coil.imageLoader(context)
            val density = context.resources.displayMetrics.density
            val px = (sizeDp * density).toInt().coerceAtLeast(1)
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(px)
                .allowHardware(false) // RemoteViews IPC requires software Bitmaps!
                .build()
            val result = loader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap?.let { src ->
                getCircularBitmap(src)
            }
        } catch (e: Throwable) {
            android.util.Log.e("DecryptumAutofill", "Failed to load favicon for $domain: ${e.message}")
            null
        }
    }

    /**
     * Crops and returns a circular ARGB_8888 software Bitmap from source.
     */
    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (bitmap.width != bitmap.height) {
            val matrix = Matrix()
            val dx = (size - bitmap.width) / 2f
            val dy = (size - bitmap.height) / 2f
            matrix.setTranslate(dx, dy)
            shader.setLocalMatrix(matrix)
        }
        paint.shader = shader
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }

    /**
     * Creates a RemoteViews dropdown presentation for a password dataset item.
     * Matches the card style of AutofillPickerActivity (media_1788619785182.jpg).
     */
    fun createDropdownPresentation(
        context: Context,
        service: String,
        username: String,
        isLocked: Boolean,
        faviconBitmap: Bitmap? = null
    ): RemoteViews {
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_item)
        val titleText = username.ifBlank { service }
        val subtitleText = "••••••••"
        presentation.setTextViewText(R.id.autofill_item_title, titleText)
        presentation.setTextViewText(R.id.autofill_item_subtitle, subtitleText)
        if (faviconBitmap != null) {
            presentation.setImageViewBitmap(R.id.autofill_item_icon, faviconBitmap)
        } else {
            presentation.setImageViewResource(
                R.id.autofill_item_icon,
                if (isLocked) R.drawable.ic_autofill_lock else R.drawable.ic_autofill_key
            )
        }
        return presentation
    }

    /**
     * Creates a RemoteViews presentation for the "Выбрать другой пароль" button.
     * Matches the TextButton style of AutofillPickerActivity (media_1788619785182.jpg).
     */
    fun createPickerDropdownPresentation(context: Context): RemoteViews {
        val presentation = RemoteViews(context.packageName, R.layout.autofill_picker_item)
        presentation.setImageViewResource(R.id.autofill_picker_icon, R.drawable.ic_autofill_search)
        presentation.setTextViewText(R.id.autofill_picker_text, context.getString(R.string.autofill_choose_other))
        return presentation
    }

    /**
     * Creates a custom Material 3 RemoteViews header for the native Android Fill Dialog.
     * Replaces the default large isolated app icon with:
     * - Drag handle (36x4dp)
     * - Site favicon (32x32dp)
     * - Site domain/service title (bold 16sp)
     * - Subtitle "Сохранённые аккаунты" (13sp)
     * - Close button (✕) that dismisses the dialog
     */
    fun createDialogHeader(
        context: Context,
        domainOrService: String,
        faviconBitmap: Bitmap? = null
    ): RemoteViews {
        val header = RemoteViews(context.packageName, R.layout.autofill_dialog_header)
        header.setTextViewText(R.id.autofill_header_title, domainOrService)
        header.setTextViewText(R.id.autofill_header_subtitle, context.getString(R.string.autofill_matching_subtitle))
        if (faviconBitmap != null) {
            header.setImageViewBitmap(R.id.autofill_header_icon, faviconBitmap)
        } else {
            header.setImageViewResource(R.id.autofill_header_icon, R.drawable.ic_autofill_key)
        }

        // Setup dismiss action on close icon (✕)
        val dismissIntent = Intent(context, AutofillDismissActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val dismissPendingIntent = PendingIntent.getActivity(
            context,
            1234,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        header.setOnClickPendingIntent(R.id.autofill_header_close, dismissPendingIntent)

        return header
    }

    /**
     * Creates an InlinePresentation for keyboard suggestions (Android 11+ / API 30+).
     */
    @SuppressLint("RestrictedApi")
    fun createInlinePresentation(
        context: Context,
        spec: InlinePresentationSpec,
        service: String,
        username: String,
        isLocked: Boolean = false,
        pendingIntent: PendingIntent? = null
    ): InlinePresentation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val targetIntent = pendingIntent ?: PendingIntent.getActivity(
                context,
                0,
                android.content.Intent(),
                PendingIntent.FLAG_IMMUTABLE
            )
            val builder = InlineSuggestionUi.newContentBuilder(targetIntent)
            val titleText = username.ifBlank { service }
            builder.setTitle(titleText)
            if (pendingIntent == null) {
                builder.setSubtitle("••••••••")
            } else if (username.isNotBlank()) {
                builder.setSubtitle(username)
            }
            val icon = android.graphics.drawable.Icon.createWithResource(
                context,
                if (isLocked) R.drawable.ic_autofill_lock else R.drawable.ic_autofill_key
            )
            builder.setStartIcon(icon)
            val content: UiVersions.Content = builder.build()
            val slice = content.slice
            android.util.Log.w("DecryptumAutofill", "createInlinePresentation: slice created successfully for $username")
            InlinePresentation(slice, spec, false)
        } catch (t: Throwable) {
            android.util.Log.e("DecryptumAutofill", "createInlinePresentation FAILED: ${t.message}", t)
            null
        }
    }

    /**
     * Creates an InlinePresentation for the search vault button in keyboard strip.
     */
    @SuppressLint("RestrictedApi")
    fun createPickerInlinePresentation(
        context: Context,
        spec: InlinePresentationSpec,
        pendingIntent: PendingIntent
    ): InlinePresentation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val builder = InlineSuggestionUi.newContentBuilder(pendingIntent)
            builder.setTitle(context.getString(R.string.autofill_search_vault))
            val icon = android.graphics.drawable.Icon.createWithResource(
                context,
                R.drawable.ic_autofill_search
            )
            builder.setStartIcon(icon)
            val content: UiVersions.Content = builder.build()
            val slice = content.slice
            android.util.Log.w("DecryptumAutofill", "createPickerInlinePresentation: slice created successfully")
            InlinePresentation(slice, spec, false)
        } catch (t: Throwable) {
            android.util.Log.e("DecryptumAutofill", "createPickerInlinePresentation FAILED: ${t.message}", t)
            null
        }
    }

    /**
     * Sets a value on [Dataset.Builder] with DialogPresentation (API 33+) and InlinePresentation (API 30+) support.
     */
    fun setDatasetValue(
        builder: Dataset.Builder,
        id: android.view.autofill.AutofillId,
        value: android.view.autofill.AutofillValue?,
        presentation: RemoteViews,
        inlinePresentation: InlinePresentation? = null,
        dialogPresentation: RemoteViews? = null,
        suppressMenuPresentation: Boolean = false
    ) {
        android.util.Log.w(
            "DecryptumAutofill",
            "setDatasetValue: id=$id, hasValue=${value != null}, hasMenu=${!suppressMenuPresentation}, " +
                    "hasDialog=${dialogPresentation != null}, hasInline=${inlinePresentation != null}, sdk=${Build.VERSION.SDK_INT}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val presentationsBuilder = android.service.autofill.Presentations.Builder()
            if (!suppressMenuPresentation) {
                presentationsBuilder.setMenuPresentation(presentation)
            }
            if (dialogPresentation != null) {
                presentationsBuilder.setDialogPresentation(dialogPresentation)
            }
            if (inlinePresentation != null) {
                presentationsBuilder.setInlinePresentation(inlinePresentation)
            }
            val fieldBuilder = android.service.autofill.Field.Builder()
            if (value != null) {
                fieldBuilder.setValue(value)
            }
            fieldBuilder.setPresentations(presentationsBuilder.build())
            builder.setField(id, fieldBuilder.build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            @Suppress("DEPRECATION")
            builder.setValue(id, value, presentation, inlinePresentation)
        } else {
            @Suppress("DEPRECATION")
            builder.setValue(id, value, presentation)
        }
    }
}
