package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.UnsupportedReason

internal fun UnsupportedReason.messageRes(): Int =
    when (this) {
        UnsupportedReason.DRM_PROTECTED -> R.string.detection_drm_protected
        UnsupportedReason.UNSUPPORTED_SERVICE -> R.string.detection_unsupported_service
        UnsupportedReason.INVALID_URL -> R.string.detection_invalid_url
        UnsupportedReason.ACCESS_RESTRICTED -> R.string.detection_access_restricted
        UnsupportedReason.NO_MEDIA -> R.string.detection_no_media
        UnsupportedReason.NETWORK_ERROR -> R.string.detection_network_error
        UnsupportedReason.UNSUPPORTED_FORMAT -> R.string.detection_unsupported_format
    }
