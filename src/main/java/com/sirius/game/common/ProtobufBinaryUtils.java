package com.sirius.game.common;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sirius.game.proto.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ProtobufBinaryUtils {
    
    /**
     * 将Message序列化为字节数组
     */
    public static byte[] serializeMessage(Message message) {
        return message.toByteArray();
    }
    
    /**
     * 将字节数组反序列化为Message
     */
    public static Message deserializeMessage(byte[] data) throws InvalidProtocolBufferException {
        return Message.parseFrom(data);
    }
    
    /**
     * 创建发送消息请求
     */
    public static Message createSendMessageRequest(String from, String to, String content) {
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setFrom(from)
                .setTo(to)
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .build();
        
        return Message.newBuilder()
                .setType(MessageType.SEND_REQUEST)
                .setSendRequest(request)
                .build();
    }
    
    /**
     * 创建接收消息通知
     */
    public static Message createReceiveMessageNotification(String from, String to, String content, String originalMessageId) {
        ReceiveMessageNotification notification = ReceiveMessageNotification.newBuilder()
                .setFrom(from)
                .setTo(to)
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .setMessageId(UUID.randomUUID().toString())
                .build();
        
        return Message.newBuilder()
                .setType(MessageType.RECEIVE_NOTIFICATION)
                .setReceiveNotification(notification)
                .build();
    }
}