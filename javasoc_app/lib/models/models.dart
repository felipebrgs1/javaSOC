class AuthUser {
  final int id;
  final String username;
  final String email;
  final String token;

  const AuthUser({
    required this.id,
    required this.username,
    required this.email,
    required this.token,
  });

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
        id: (json['id'] as num).toInt(),
        username: json['username'] as String,
        email: json['email'] as String? ?? '',
        token: json['token'] as String,
      );
}

class ReactionView {
  final String emoji;
  int count;
  bool includesMe;

  ReactionView({required this.emoji, required this.count, required this.includesMe});

  factory ReactionView.fromJson(Map<String, dynamic> json) => ReactionView(
        emoji: json['emoji'] as String,
        count: (json['count'] as num).toInt(),
        includesMe: json['includesMe'] as bool? ?? false,
      );
}

class AttachmentView {
  final int id;
  final String filename;
  final String contentType;
  final int sizeBytes;
  final String url;

  const AttachmentView({
    required this.id,
    required this.filename,
    required this.contentType,
    required this.sizeBytes,
    required this.url,
  });

  bool get isImage => contentType.startsWith('image/');

  factory AttachmentView.fromJson(Map<String, dynamic> json) => AttachmentView(
        id: (json['id'] as num).toInt(),
        filename: json['filename'] as String? ?? 'file',
        contentType: json['contentType'] as String? ?? '',
        sizeBytes: (json['sizeBytes'] as num?)?.toInt() ?? 0,
        url: json['url'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'filename': filename,
        'contentType': contentType,
        'sizeBytes': sizeBytes,
        'url': url,
      };
}

class ChatMessageModel {
  final int id;
  final String from;
  String content;
  final DateTime timestamp;
  DateTime? editedAt;
  final Map<String, ReactionView> reactions;
  List<AttachmentView> attachments;

  ChatMessageModel({
    required this.id,
    required this.from,
    required this.content,
    required this.timestamp,
    this.editedAt,
    Map<String, ReactionView>? reactions,
    List<AttachmentView>? attachments,
  })  : reactions = reactions ?? {},
        attachments = attachments ?? [];

  factory ChatMessageModel.fromHistoryJson(Map<String, dynamic> json) {
    final reactions = <String, ReactionView>{};
    for (final r in (json['reactions'] as List?) ?? const []) {
      final view = ReactionView.fromJson(r as Map<String, dynamic>);
      reactions[view.emoji] = view;
    }
    return ChatMessageModel(
      id: (json['id'] as num).toInt(),
      from: json['from'] as String? ?? '?',
      content: json['content'] as String? ?? '',
      timestamp: DateTime.tryParse(json['timestamp'] as String? ?? '') ??
          DateTime.now(),
      editedAt: json['editedAt'] != null
          ? DateTime.tryParse(json['editedAt'] as String)
          : null,
      reactions: reactions,
      attachments: ((json['attachments'] as List?) ?? const [])
          .map((a) => AttachmentView.fromJson(a as Map<String, dynamic>))
          .toList(),
    );
  }
}

enum PresenceStatus { online, idle, offline }

class PresenceEntry {
  final int? userId;
  final String username;
  PresenceStatus status;

  PresenceEntry({this.userId, required this.username, required this.status});

  static PresenceStatus parse(String? raw) => switch (raw) {
        'ONLINE' => PresenceStatus.online,
        'IDLE' => PresenceStatus.idle,
        _ => PresenceStatus.offline,
      };
}

class ChannelInfo {
  final String name;
  final bool isVoice;

  const ChannelInfo(this.name, {this.isVoice = false});
}

class DmMessage {
  final String from;
  final String to;
  final String content;
  final DateTime timestamp;

  const DmMessage({
    required this.from,
    required this.to,
    required this.content,
    required this.timestamp,
  });
}
