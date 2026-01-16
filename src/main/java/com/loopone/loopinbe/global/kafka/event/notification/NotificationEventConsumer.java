package com.loopone.loopinbe.global.kafka.event.notification;

import com.loopone.loopinbe.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.loopone.loopinbe.global.constants.KafkaKey.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {
    private final NotificationService notificationService;
    private static final Map<String, String> TOPIC_TITLE_MAP = Map.of(
            FOLLOW_NOTIFICATION_TOPIC, "팔로우 알림",
            INVITE_TOPIC, "초대 알림"
    );

    @KafkaListener(
            topics = { FOLLOW_NOTIFICATION_TOPIC, INVITE_TOPIC },
            groupId = NOTIFICATION_GROUP_ID, containerFactory = KAFKA_LISTENER_CONTAINER
    )
    public void consumeNotification(ConsumerRecord<String, String> rec) {
        final String topic = rec.topic();
        final String title = TOPIC_TITLE_MAP.get(topic);
        if (title == null) { // 알 수 없는 토픽이면 "실패"로 처리해서 ErrorHandler가 재시도/ DL(T)로 보내게 유도
            throw new IllegalArgumentException("Unknown notification topic: " + topic);
        }
        // 메시지 저장 + 커밋 후 FCM 발송은 서비스에서 처리
        notificationService.createAndNotifyFromMessage(rec.value(), title);
    }
}
