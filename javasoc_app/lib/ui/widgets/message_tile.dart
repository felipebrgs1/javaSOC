import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../state/chat_provider.dart';
import '../../theme.dart';

class MessageTile extends StatefulWidget {
  final ChatMessageModel message;
  final bool isMine;
  final void Function(String emoji) onReact;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const MessageTile({
    super.key,
    required this.message,
    required this.isMine,
    required this.onReact,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  State<MessageTile> createState() => _MessageTileState();
}

class _MessageTileState extends State<MessageTile> {
  bool _hovering = false;

  static const quickEmojis = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    return MouseRegion(
      onEnter: (_) => setState(() => _hovering = true),
      onExit: (_) => setState(() => _hovering = false),
      child: Container(
        color: _hovering ? Colors.white.withValues(alpha: 0.03) : null,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundColor: DiscordColors.blurple,
                  child: Text(
                    message.from.isNotEmpty ? message.from[0].toUpperCase() : '?',
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            message.from,
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: widget.isMine
                                  ? DiscordColors.blurple
                                  : DiscordColors.textNormal,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            DateFormat('dd/MM HH:mm')
                                .format(message.timestamp.toLocal()),
                            style: const TextStyle(
                                fontSize: 11, color: DiscordColors.textMuted),
                          ),
                          if (message.editedAt != null)
                            const Text('  (editado)',
                                style: TextStyle(
                                    fontSize: 11,
                                    color: DiscordColors.textMuted)),
                        ],
                      ),
                      const SizedBox(height: 2),
                      if (message.content.isNotEmpty)
                        SelectableText(
                          message.content,
                          style: const TextStyle(color: DiscordColors.textNormal),
                        ),
                      if (message.attachments.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: _AttachmentRow(
                              attachments: message.attachments),
                        ),
                    ],
                  ),
                ),
                if (_hovering)
                  _HoverActions(
                    isMine: widget.isMine,
                    onReact: widget.onReact,
                    onEdit: widget.onEdit,
                    onDelete: widget.onDelete,
                  ),
              ],
            ),
            if (message.reactions.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(left: 48, top: 4),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: [
                    for (final reaction in message.reactions.values)
                      _ReactionChip(
                        reaction: reaction,
                        onTap: () => widget.onReact(reaction.emoji),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _HoverActions extends StatelessWidget {
  final bool isMine;
  final void Function(String emoji) onReact;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const _HoverActions({
    required this.isMine,
    required this.onReact,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: DiscordColors.sidebarBackground,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: Colors.black26),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final emoji in _MessageTileState.quickEmojis.take(3))
            InkWell(
              onTap: () => onReact(emoji),
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: Text(emoji, style: const TextStyle(fontSize: 15)),
              ),
            ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.add_reaction_outlined,
                size: 18, color: DiscordColors.textMuted),
            tooltip: 'Reagir',
            color: DiscordColors.railBackground,
            onSelected: onReact,
            itemBuilder: (_) => [
              for (final emoji in _MessageTileState.quickEmojis)
                PopupMenuItem(value: emoji, child: Text(emoji)),
            ],
          ),
          if (isMine)
            IconButton(
              icon: const Icon(Icons.edit_outlined,
                  size: 18, color: DiscordColors.textMuted),
              tooltip: 'Editar',
              onPressed: onEdit,
            ),
          if (isMine)
            IconButton(
              icon: const Icon(Icons.delete_outline,
                  size: 18, color: DiscordColors.danger),
              tooltip: 'Excluir',
              onPressed: onDelete,
            ),
        ],
      ),
    );
  }
}

class _ReactionChip extends StatelessWidget {
  final ReactionView reaction;
  final VoidCallback onTap;

  const _ReactionChip({required this.reaction, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final active = reaction.includesMe;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: active
              ? DiscordColors.blurple.withValues(alpha: 0.3)
              : DiscordColors.sidebarBackground,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: active ? DiscordColors.blurple : Colors.transparent,
          ),
        ),
        child: Text(
          '${reaction.emoji} ${reaction.count}',
          style: const TextStyle(fontSize: 13, color: DiscordColors.textNormal),
        ),
      ),
    );
  }
}

class _AttachmentRow extends StatelessWidget {
  final List<AttachmentView> attachments;

  const _AttachmentRow({required this.attachments});

  @override
  Widget build(BuildContext context) {
    final baseUrl = context.read<ChatProvider>().baseUrl;
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final attachment in attachments)
          if (attachment.isImage)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: CachedNetworkImage(
                imageUrl: _resolve(baseUrl, attachment.url),
                width: 260,
                fit: BoxFit.cover,
                placeholder: (_, _) => const SizedBox(
                  width: 260,
                  height: 140,
                  child: Center(child: CircularProgressIndicator()),
                ),
                errorWidget: (_, _, _) =>
                    _fileCard(attachment, broken: true),
              ),
            )
          else
            _fileCard(attachment),
      ],
    );
  }

  String _resolve(String baseUrl, String path) {
    if (path.startsWith('http')) return path;
    return '$baseUrl$path';
  }

  Widget _fileCard(AttachmentView attachment, {bool broken = false}) {
    final kb = (attachment.sizeBytes / 1024).toStringAsFixed(1);
    return Container(
      width: 260,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: DiscordColors.sidebarBackground,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(broken ? Icons.broken_image_outlined : Icons.insert_drive_file,
              color: DiscordColors.textMuted),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(attachment.filename,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: DiscordColors.textNormal)),
                Text('$kb KB',
                    style: const TextStyle(
                        fontSize: 11, color: DiscordColors.textMuted)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
