package com.loopone.loopinbe.domain.chat.chatRoom.serviceImpl;

import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomStateService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomStateServiceImpl implements ChatRoomStateService {
    private final ChatRoomRepository chatRoomRepository;

    @Override
    @Transactional
    public void setCallUpdateLoop(Long chatRoomId, boolean isCallUpdateLoop) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));

        chatRoom.setCallUpdateLoop(isCallUpdateLoop);
    }
}
