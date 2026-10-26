package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaCandidate

internal fun String.mediaPageTitle(): String =
    TITLE_OPEN_TAG_REGEX
        .find(this)
        ?.let { opening ->
            TITLE_CLOSE_TAG_REGEX.find(this, opening.range.last + 1)?.let { closing ->
                substring(opening.range.last + 1, closing.range.first)
            }
        }?.replace(HTML_TAG_REGEX, "")
        ?.trim()
        ?.decodeHtmlAttribute()
        .orEmpty()

internal fun String.isOpaqueMediaTitle(): Boolean = OPAQUE_MEDIA_TITLE_REGEX.matches(this)

internal fun MediaCandidate.withHtmlPageTitle(
    pageTitle: String,
    index: Int,
    count: Int,
): MediaCandidate =
    when {
        title == pageTitle && pageTitle.isNotBlank() && count > 1 -> {
            copy(title = "$pageTitle · ${index + 1}")
        }

        title.isOpaqueMediaTitle() -> {
            val fallback =
                pageTitle.takeIf(String::isNotBlank)?.let { "${it.candidateCollectionTitle()} · ${index + 1}" }
            copy(title = fallback.orEmpty())
        }

        else -> {
            this
        }
    }

private fun String.candidateCollectionTitle(): String =
    substringBefore('·')
        .trim()
        .replace(LEADING_RESULT_COUNT_REGEX, "")
        .take(MAX_CANDIDATE_PAGE_TITLE_LENGTH)
        .trim()

private val TITLE_OPEN_TAG_REGEX = Regex("<title\\b[^<>]*>", RegexOption.IGNORE_CASE)
private val TITLE_CLOSE_TAG_REGEX = Regex("</title>", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^<>]+>")
private val OPAQUE_MEDIA_TITLE_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val LEADING_RESULT_COUNT_REGEX = Regex("^[\\d,+]+개의\\s+(?:최고의\\s+)?")
private const val MAX_CANDIDATE_PAGE_TITLE_LENGTH = 48
