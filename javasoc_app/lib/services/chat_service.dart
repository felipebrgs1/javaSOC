import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

class ChatService {
  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _heartbeat;

  final _messages = StreamController<Map<String, dynamic>>.broadcast();
  final _connection = StreamController<bool>.broadcast();

  Stream<Map<String, dynamic>> get messages => _messages.stream;
  Stream<bool> get connection => _connection.stream;

  bool get isConnected => _channel != null;

  void connect(String wsUrl, String token) {
    disconnect();
    try {
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
    } catch (_) {
      _connection.add(false);
      return;
    }
    _connection.add(true);
    send({'type': 'AUTH', 'token': token});
    _sub = _channel!.stream.listen(
      (event) {
        try {
          final decoded = jsonDecode(event as String) as Map<String, dynamic>;
          _messages.add(decoded);
        } catch (_) {}
      },
      onDone: () => _handleClose(),
      onError: (_) => _handleClose(),
    );
    _heartbeat = Timer.periodic(const Duration(seconds: 20), (_) {
      send({'type': 'HEARTBEAT'});
    });
  }

  void send(Map<String, dynamic> payload) {
    final channel = _channel;
    if (channel == null) return;
    payload.removeWhere((_, v) => v == null);
    try {
      channel.sink.add(jsonEncode(payload));
    } catch (_) {}
  }

  void _handleClose() {
    _connection.add(false);
    disconnect();
  }

  void disconnect() {
    _heartbeat?.cancel();
    _heartbeat = null;
    _sub?.cancel();
    _sub = null;
    _channel?.sink.close();
    _channel = null;
  }

  void dispose() {
    disconnect();
    _messages.close();
    _connection.close();
  }
}
