package com.felipeb.discordclone.webrtc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.felipeb.discordclone.chat.api.ChatMessage;
import com.felipeb.discordclone.chat.api.MessageType;
import com.felipeb.discordclone.chat.ChatWebSocketHandler;
import com.felipeb.discordclone.chat.channel.Channel;
import com.felipeb.discordclone.chat.channel.ChannelService;
import com.felipeb.discordclone.chat.channel.ChannelType;
import com.felipeb.discordclone.chat.permission.PermissionService;
import com.felipeb.discordclone.chat.session.SessionRegistry;
import com.felipeb.discordclone.server.Membership;
import com.felipeb.discordclone.server.Server;
import com.felipeb.discordclone.server.ServerRepository;
import com.felipeb.discordclone.webrtc.api.SignalingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceSignalingHandlerTest {

    private static final String SERVER = "discord-clone";
    private static final String CHANNEL = "voice-general";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private VoiceRoomRegistry rooms;
    private SessionRegistry sessions;
    private ServerRepository servers;
    private ChannelService channelService;
    private PermissionService permissions;
    private VoiceSignalingHandler handler;

    private WebSocketSession alice;
    private WebSocketSession bob;

    @BeforeEach
    void setUp() {
        rooms = new InMemoryVoiceRoomRegistry();
        sessions = mock(SessionRegistry.class);
        servers = mock(ServerRepository.class);
        channelService = mock(ChannelService.class);
        permissions = mock(PermissionService.class);

        Server server = new Server(SERVER);
        Channel channel = new Channel(server, CHANNEL, null, ChannelType.VOICE);
        when(servers.findByName(SERVER)).thenReturn(Optional.of(server));
        when(channelService.requireByServerAndName(server, CHANNEL)).thenReturn(channel);
        when(permissions.requireReadAccess(anyLong(), anyLong())).thenReturn(mock(Membership.class));

        handler = new VoiceSignalingHandler(mapper, rooms, sessions, servers, channelService, permissions);

        alice = open("alice", 1L);
        bob = open("bob", 2L);
        when(sessions.findByUserId("alice")).thenReturn(Optional.of(alice));
        when(sessions.findByUserId("bob")).thenReturn(Optional.of(bob));
    }

    @Test
    void joinToEmptyRoomAcksWithSelfInParticipants() throws Exception {
        handler.handleMessage(alice, join());

        List<SignalingMessage> msgs = sent(alice);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).type()).isEqualTo(MessageType.VOICE_JOINED);
        assertThat(msgs.get(0).participants()).containsExactly("alice");
        assertThat(msgs.get(0).server()).isEqualTo(SERVER);
        assertThat(msgs.get(0).channel()).isEqualTo(CHANNEL);
        assertThat(sent(bob)).isEmpty();
    }

    @Test
    void secondJoinerIsAnnouncedToExistingPeerAndAckedWithBoth() throws Exception {
        handler.handleMessage(alice, join());
        Mockito.clearInvocations(alice);
        handler.handleMessage(bob, join());

        List<SignalingMessage> toAlice = sent(alice);
        assertThat(toAlice).hasSize(1);
        assertThat(toAlice.get(0).type()).isEqualTo(MessageType.VOICE_USER_JOINED);
        assertThat(toAlice.get(0).from()).isEqualTo("bob");

        List<SignalingMessage> toBob = sent(bob);
        assertThat(toBob).hasSize(1);
        assertThat(toBob.get(0).type()).isEqualTo(MessageType.VOICE_JOINED);
        assertThat(toBob.get(0).participants()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void sdpOfferIsRelayedToTargetPeer() throws Exception {
        joinBoth();

        handler.handleMessage(bob, signal(MessageType.SDP_OFFER, "alice", "v=0 sdp"));

        List<SignalingMessage> toAlice = sent(alice);
        assertThat(toAlice).hasSize(3); // VOICE_JOINED + VOICE_USER_JOINED + relayed offer
        SignalingMessage offer = toAlice.get(2);
        assertThat(offer.type()).isEqualTo(MessageType.SDP_OFFER);
        assertThat(offer.from()).isEqualTo("bob");
        assertThat(offer.to()).isEqualTo("alice");
        assertThat(offer.content()).isEqualTo("v=0 sdp");
        assertThat(sent(bob)).extracting(SignalingMessage::type)
                .containsExactly(MessageType.VOICE_JOINED); // nothing relayed back
    }

    @Test
    void iceCandidateIsRelayedVerbatim() throws Exception {
        joinBoth();

        handler.handleMessage(alice, signal(MessageType.ICE_CANDIDATE, "bob",
                "{\"candidate\":\"candidate:1\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));

        List<SignalingMessage> toBob = sent(bob);
        assertThat(toBob).hasSize(2);
        assertThat(toBob.get(1).type()).isEqualTo(MessageType.ICE_CANDIDATE);
        assertThat(toBob.get(1).content()).contains("candidate:1");
    }

    @Test
    void relayToUserOutsideRoomIsRejected() throws Exception {
        handler.handleMessage(alice, join());
        Mockito.clearInvocations(alice);

        handler.handleMessage(alice, signal(MessageType.SDP_OFFER, "bob", "sdp"));

        assertThat(sent(alice)).extracting(SignalingMessage::type)
                .containsExactly(MessageType.ERROR);
        assertThat(sent(bob)).isEmpty();
    }

    @Test
    void relayToSelfIsRejected() throws Exception {
        joinBoth();

        handler.handleMessage(alice, signal(MessageType.SDP_OFFER, "alice", "sdp"));

        assertThat(sent(alice)).extracting(SignalingMessage::type)
                .containsExactly(MessageType.VOICE_JOINED, MessageType.VOICE_USER_JOINED, MessageType.ERROR);
        assertThat(sent(bob)).extracting(SignalingMessage::type)
                .containsExactly(MessageType.VOICE_JOINED);
    }

    @Test
    void leaveNotifiesRemainingPeers() throws Exception {
        joinBoth();
        Mockito.clearInvocations(alice);

        handler.handleMessage(bob, leave());

        List<SignalingMessage> toAlice = sent(alice);
        assertThat(toAlice).hasSize(1);
        assertThat(toAlice.get(0).type()).isEqualTo(MessageType.VOICE_USER_LEFT);
        assertThat(toAlice.get(0).from()).isEqualTo("bob");
        assertThat(rooms.participants(SERVER + ":" + CHANNEL)).containsExactly("alice");
    }

    @Test
    void disconnectLeavesRoomAndNotifiesRemainingPeers() throws Exception {
        joinBoth();
        Mockito.clearInvocations(alice);

        handler.onSessionClosed(bob);

        List<SignalingMessage> toAlice = sent(alice);
        assertThat(toAlice).hasSize(1);
        assertThat(toAlice.get(0).type()).isEqualTo(MessageType.VOICE_USER_LEFT);
        assertThat(rooms.participants(SERVER + ":" + CHANNEL)).containsExactly("alice");
    }

    @Test
    void joinRejectsNonVoiceChannel() throws Exception {
        Channel textChannel = new Channel(new Server(SERVER), "general");
        when(channelService.requireByServerAndName(any(), any())).thenReturn(textChannel);

        assertThatThrownBy(() -> handler.handleMessage(alice, new ChatMessage(
                MessageType.VOICE_JOIN, null, null, SERVER, "general", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a voice channel");

        assertThat(rooms.participants(SERVER + ":general")).isEmpty();
    }

    // ---------- helpers ----------

    private void joinBoth() throws Exception {
        handler.handleMessage(alice, join());
        handler.handleMessage(bob, join());
    }

    private ChatMessage join() {
        return new ChatMessage(MessageType.VOICE_JOIN, null, null, SERVER, CHANNEL,
                null, null, null, null, null);
    }

    private ChatMessage leave() {
        return new ChatMessage(MessageType.VOICE_LEAVE, null, null, SERVER, CHANNEL,
                null, null, null, null, null);
    }

    private ChatMessage signal(MessageType type, String to, String content) {
        return new ChatMessage(type, null, to, SERVER, CHANNEL, content, null, null, null, null);
    }

    private WebSocketSession open(String username, Long userId) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(ChatWebSocketHandler.USER_ID_ATTR, userId);
        attrs.put(ChatWebSocketHandler.USERNAME_ATTR, username);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private List<SignalingMessage> sent(WebSocketSession s) throws Exception {
        List<SignalingMessage> out = new ArrayList<>();
        for (Invocation inv : Mockito.mockingDetails(s).getInvocations()) {
            if (inv.getMethod().getName().equals("sendMessage")
                    && inv.getArguments()[0] instanceof TextMessage tm) {
                out.add(mapper.readValue(tm.getPayload(), SignalingMessage.class));
            }
        }
        return out;
    }
}
