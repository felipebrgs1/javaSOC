import 'dart:async';

import 'package:flutter/foundation.dart';

import '../config.dart';
import '../models/models.dart';
import '../services/api_service.dart';
import '../services/chat_service.dart';

enum ChatConnectionState { disconnected, connecting, connected }

class ChatProvider extends ChangeNotifier {
  static const serverName = 'discord-clone';
  static const textChannels = [
    ChannelInfo('general'),
    ChannelInfo('random'),
    ChannelInfo('dev'),
  ];
  static const voiceChannels = [ChannelInfo('voice-general', isVoice: true)];

  final ChatService _service;
  final ApiService _api;
  final String _baseUrl;

  StreamSubscription? _msgSub;
  StreamSubscription? _connSub;
  Timer? _reconnectTimer;

  String? _token;
  String? _username;

  ChatConnectionState connectionState = ChatConnectionState.disconnected;
  String? currentChannel;
  String? lastError;

  final Map<String, List<ChatMessageModel>> _messages = {};
  final Map<String, PresenceEntry> _presence = {};
  final Map<String, List<DmMessage>> _dms = {};
  final Map<int, ChatMessageModel> _byId = {};
  final Set<String> _subscribed = {};

  int unreadDmCount = 0;
  String? activeDmPeer;
  bool dmMode = false;

  ChatProvider(this._service, this._api, this._baseUrl) {
    _msgSub = _service.messages.listen(_onMessage);
    _connSub = _service.connection.listen((connected) {
      connectionState =
          connected ? ChatConnectionState.connecting : ChatConnectionState.disconnected;
      notifyListeners();
      if (!connected && _token != null) _scheduleReconnect();
    });
  }

  String get username => _username ?? '';
  String get baseUrl => _baseUrl;

  List<ChatMessageModel> messagesFor(String channel) =>
      _messages['$serverName:$channel'] ?? const [];

  List<DmMessage> dmsWith(String peer) => _dms[peer] ?? const [];

  List<String> get dmPeers => _dms.keys.toList();

  List<PresenceEntry> get contacts {
    final list = _presence.values.toList()
      ..sort((a, b) {
        final byStatus = a.status.index.compareTo(b.status.index);
        return byStatus != 0 ? byStatus : a.username.compareTo(b.username);
      });
    return list;
  }

  void connect(String token, String username) {
    _token = token;
    _username = username;
    connectionState = ChatConnectionState.connecting;
    notifyListeners();
    _service.connect(AppConfig.wsUrl(_baseUrl), token);
  }

  void disconnect() {
    _token = null;
    _reconnectTimer?.cancel();
    _service.disconnect();
    connectionState = ChatConnectionState.disconnected;
    currentChannel = null;
    dmMode = false;
    _messages.clear();
    _byId.clear();
    _presence.clear();
    _dms.clear();
    _subscribed.clear();
    notifyListeners();
  }

  void _subscribeAll() {
    _subscribed.clear();
    for (final channel in textChannels) {
      _subscribed.add(channel.name);
      _service.send({
        'type': 'SUBSCRIBE',
        'server': serverName,
        'channel': channel.name,
      });
    }
  }

  void selectChannel(String channel) {
    currentChannel = '$serverName:$channel';
    if (_subscribed.add(channel)) {
      _service.send(
          {'type': 'SUBSCRIBE', 'server': serverName, 'channel': channel});
    }
    notifyListeners();
  }

  List<AttachmentView>? _pendingOwnAttachments;

  void sendChannelMessage(String content,
      {List<AttachmentView> attachments = const []}) {
    if (currentChannel == null) return;
    final parts = currentChannel!.split(':');
    if (attachments.isNotEmpty) _pendingOwnAttachments = attachments;
    _service.send({
      'type': 'CHANNEL_MESSAGE',
      'server': parts[0],
      'channel': parts[1],
      'content': content,
      if (attachments.isNotEmpty)
        'attachmentIds': attachments.map((a) => a.id).toList(),
    });
  }

  void editMessage(int messageId, String content) {
    _service.send({'type': 'EDIT_MESSAGE', 'messageId': messageId, 'content': content});
  }

  void deleteMessage(int messageId) {
    _service.send({'type': 'DELETE_MESSAGE', 'messageId': messageId});
  }

  void toggleReaction(ChatMessageModel message, String emoji) {
    final existing = message.reactions[emoji];
    final type = (existing?.includesMe ?? false) ? 'UNREACT' : 'REACT';
    _service.send({'type': type, 'messageId': message.id, 'emoji': emoji});
  }

  void openDmHome() {
    dmMode = true;
    activeDmPeer = null;
    unreadDmCount = 0;
    notifyListeners();
  }

  void openDm(String peer) {
    dmMode = true;
    activeDmPeer = peer;
    _dms.putIfAbsent(peer, () => []);
    unreadDmCount = 0;
    notifyListeners();
  }

  void closeDm() {
    dmMode = false;
    activeDmPeer = null;
    notifyListeners();
  }

  void sendDm(String peer, String content) {
    _service.send({'type': 'DIRECT_MESSAGE', 'to': peer, 'content': content});
    _dms.putIfAbsent(peer, () => []).add(DmMessage(
          from: username,
          to: peer,
          content: content,
          timestamp: DateTime.now(),
        ));
    notifyListeners();
  }

  Future<AttachmentView> upload(String filename, Uint8List bytes,
      [String? contentType]) {
    return _api.uploadAttachment(
      token: _token!,
      filename: filename,
      bytes: bytes,
      contentType: contentType,
    );
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 3), () {
      if (_token != null &&
          connectionState == ChatConnectionState.disconnected) {
        connectionState = ChatConnectionState.connecting;
        notifyListeners();
        _service.connect(AppConfig.wsUrl(_baseUrl), _token!);
      }
    });
  }

  void _onMessage(Map<String, dynamic> json) {
    final type = json['type'] as String?;
    switch (type) {
      case 'AUTHENTICATED':
        connectionState = ChatConnectionState.connected;
        _presence[username] =
            PresenceEntry(username: username, status: PresenceStatus.online);
        currentChannel ??= '$serverName:${textChannels.first.name}';
        _subscribeAll();
        break;
      case 'HISTORY':
        _handleHistory(json);
        break;
      case 'PUBLISHED':
        _handlePublished(json);
        break;
      case 'MESSAGE_EDITED':
        _handleEdited(json);
        break;
      case 'MESSAGE_DELETED':
        _handleDeleted(json);
        break;
      case 'REACTION_ADDED':
      case 'REACTION_REMOVED':
        _handleReaction(json, added: type == 'REACTION_ADDED');
        break;
      case 'PRESENCE_UPDATED':
        _handlePresence(json);
        break;
      case 'DELIVERED':
        _handleDm(json);
        break;
      case 'ERROR':
        lastError = json['content'] as String?;
        break;
      default:
        return;
    }
    notifyListeners();
  }

  String? _keyOf(Map<String, dynamic> json) {
    final server = json['server'] as String?;
    final channel = json['channel'] as String?;
    if (server == null || channel == null) return null;
    return '$server:$channel';
  }

  void _handleHistory(Map<String, dynamic> json) {
    final key = _keyOf(json);
    if (key == null) return;
    final list = ((json['messages'] as List?) ?? const [])
        .map((m) => ChatMessageModel.fromHistoryJson(m as Map<String, dynamic>))
        .toList();
    _messages[key] = list;
    for (final m in list) {
      _byId[m.id] = m;
    }
  }

  void _handlePublished(Map<String, dynamic> json) {
    final key = _keyOf(json);
    if (key == null) return;
    final message = ChatMessageModel(
      id: (json['id'] as num).toInt(),
      from: json['from'] as String? ?? '?',
      content: json['content'] as String? ?? '',
      timestamp: DateTime.tryParse(json['timestamp'] as String? ?? '') ??
          DateTime.now(),
    );
    if (message.from == username && _pendingOwnAttachments != null) {
      message.attachments = _pendingOwnAttachments!;
      _pendingOwnAttachments = null;
    }
    _messages.putIfAbsent(key, () => []).add(message);
    _byId[message.id] = message;
  }

  void _handleEdited(Map<String, dynamic> json) {
    final id = (json['messageId'] as num?)?.toInt();
    if (id == null) return;
    final message = _byId[id];
    if (message == null) return;
    message.content = json['content'] as String? ?? message.content;
    message.editedAt = DateTime.tryParse(json['editedAt'] as String? ?? '');
  }

  void _handleDeleted(Map<String, dynamic> json) {
    final id = (json['messageId'] as num?)?.toInt();
    final key = _keyOf(json);
    if (id == null || key == null) return;
    _messages[key]?.removeWhere((m) => m.id == id);
    _byId.remove(id);
  }

  void _handleReaction(Map<String, dynamic> json, {required bool added}) {
    final id = (json['messageId'] as num?)?.toInt();
    final emoji = json['emoji'] as String?;
    if (id == null || emoji == null) return;
    final message = _byId[id];
    if (message == null) return;
    final reactor = json['reactorUsername'] as String?;
    final isMe = reactor == username;
    final entry = message.reactions[emoji] ??
        (message.reactions[emoji] =
            ReactionView(emoji: emoji, count: 0, includesMe: false));
    if (added) {
      entry.count++;
      if (isMe) entry.includesMe = true;
    } else {
      entry.count = entry.count > 0 ? entry.count - 1 : 0;
      if (isMe) entry.includesMe = false;
      if (entry.count == 0) message.reactions.remove(emoji);
    }
  }

  void _handlePresence(Map<String, dynamic> json) {
    final username = json['username'] as String?;
    if (username == null) return;
    final status = PresenceEntry.parse(json['status'] as String?);
    final userId = (json['userId'] as num?)?.toInt();
    final existing = _presence[username];
    if (existing != null) {
      existing.status = status;
    } else {
      _presence[username] =
          PresenceEntry(userId: userId, username: username, status: status);
    }
  }

  void _handleDm(Map<String, dynamic> json) {
    final from = json['from'] as String?;
    final content = json['content'] as String? ?? '';
    if (from == null) return;
    _dms.putIfAbsent(from, () => []).add(DmMessage(
          from: from,
          to: username,
          content: content,
          timestamp: DateTime.tryParse(json['timestamp'] as String? ?? '') ??
              DateTime.now(),
        ));
    if (activeDmPeer != from) unreadDmCount++;
  }

  @override
  void dispose() {
    _reconnectTimer?.cancel();
    _msgSub?.cancel();
    _connSub?.cancel();
    super.dispose();
  }
}
