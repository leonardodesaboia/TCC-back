package com.allset.api.notification.domain;

public enum NotificationType {
    new_request,
    request_accepted,
    request_rejected,
    request_status_update,
    new_message,
    payment_released,
    dispute_opened,
    dispute_resolved,
    verification_result
}
