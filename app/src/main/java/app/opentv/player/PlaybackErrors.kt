/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import app.opentv.core.AppText
import app.opentv.R

/**
 * Turns a [PlaybackException] into something a person can act on.
 *
 * "ERROR_CODE_IO_BAD_HTTP_STATUS" tells a user nothing. Worse, the habit of showing a raw
 * error code on screen is what led one player to *remove* its 403 display entirely as a
 * "fix" — hiding the symptom while the stream still failed. The right answer is to say what
 * probably went wrong and what to try.
 */
@OptIn(UnstableApi::class)
object PlaybackErrors {

    fun describe(error: PlaybackException): String {
        (error.cause as? HttpDataSource.InvalidResponseCodeException)?.let {
            return describeHttpStatus(it.responseCode)
        }
        if (error.cause is UnknownHostException) {
            return AppText.get(R.string.perr_unknown_host)
        }
        if (error.cause is SocketTimeoutException) {
            return AppText.get(R.string.perr_timeout)
        }

        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                AppText.get(R.string.perr_connection_lost)

            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                AppText.get(R.string.perr_web_page)

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                AppText.get(R.string.perr_malformed)

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                AppText.get(R.string.perr_no_decoder)

            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                AppText.get(R.string.perr_unsupported_format)

            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
                AppText.get(R.string.perr_behind_live)

            else -> AppText.get(R.string.perr_generic, error.errorCodeName)
        }
    }

    fun describeHttpStatus(code: Int): String = when (code) {
        401 -> AppText.get(R.string.perr_http_401)
        403 -> AppText.get(R.string.perr_http_403)
        404 -> AppText.get(R.string.perr_http_404)
        405 -> AppText.get(R.string.perr_http_405)
        429 -> AppText.get(R.string.perr_http_429)
        451 -> AppText.get(R.string.perr_http_451)
        in 500..599 -> AppText.get(R.string.perr_http_5xx, code)
        else -> AppText.get(R.string.perr_http_other, code)
    }
}
