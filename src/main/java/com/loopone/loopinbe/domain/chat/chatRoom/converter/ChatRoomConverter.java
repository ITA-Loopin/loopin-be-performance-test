package com.loopone.loopinbe.domain.chat.chatRoom.converter;

import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomListResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoomMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChatRoomConverter {
    // ---------- ChatRoom -> ChatRoomResponse ----------
    @Mapping(target = "id", source = "chatRoom.id")
    @Mapping(target = "ownerId", source = "chatRoom.member.id")
    @Mapping(target = "title", source = "chatRoom.title")
    @Mapping(target = "loopSelect", expression = "java(chatRoomMember.getChatRoom().getLoop() != null)")
    @Mapping(target = "isCallUpdateLoop", source = "chatRoom.callUpdateLoop")
    @Mapping(target = "lastMessageAt", source = "chatRoom.lastMessageAt")
    @Mapping(target = "lastReadAt", source = "lastReadAt")
    ChatRoomResponse toChatRoomResponse(ChatRoomMember chatRoomMember);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "ownerId", source = "member.id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "loopSelect", expression = "java(chatRoom.getLoop() != null)")
    @Mapping(target = "isCallUpdateLoop", source = "callUpdateLoop")
    @Mapping(target = "lastMessageAt", source = "lastMessageAt")
    @Mapping(target = "lastReadAt", ignore = true)
    ChatRoomResponse toChatRoomResponse(ChatRoom chatRoom);

    // ---------- ChatRoomList -> ChatRoomListResponse ----------
    List<ChatRoomResponse> toChatRoomResponses(List<ChatRoomMember> chatRoomMembers);

    default ChatRoomListResponse toChatRoomListResponse(List<ChatRoomMember> chatRoomMembers) {
        return new ChatRoomListResponse(toChatRoomResponses(chatRoomMembers));
    }
}
