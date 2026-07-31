import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../state/chat_provider.dart';
import '../../theme.dart';

class MessageInput extends StatefulWidget {
  final String hint;

  const MessageInput({super.key, required this.hint});

  @override
  State<MessageInput> createState() => MessageInputState();
}

class MessageInputState extends State<MessageInput> {
  final _controller = TextEditingController();
  ChatMessageModel? _editing;
  final List<AttachmentView> _pending = [];
  bool _uploading = false;

  bool get isEditing => _editing != null;

  void startEditing(ChatMessageModel message) {
    setState(() {
      _editing = message;
      _controller.text = message.content;
    });
  }

  void cancelEditing() {
    setState(() {
      _editing = null;
      _controller.clear();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _pickFiles() async {
    final result = await FilePicker.pickFiles(withData: true);
    if (result == null || !mounted) return;
    final chat = context.read<ChatProvider>();
    setState(() => _uploading = true);
    try {
      for (final file in result.files) {
        final bytes = file.bytes;
        if (bytes == null) continue;
        final attachment = await chat.upload(
          file.name,
          bytes,
          _guessContentType(file.extension),
        );
        setState(() => _pending.add(attachment));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Falha no upload: $e')));
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  String? _guessContentType(String? ext) => switch (ext?.toLowerCase()) {
        'png' => 'image/png',
        'jpg' || 'jpeg' => 'image/jpeg',
        'gif' => 'image/gif',
        'webp' => 'image/webp',
        'pdf' => 'application/pdf',
        'txt' => 'text/plain',
        _ => null,
      };

  void _send() {
    final text = _controller.text.trim();
    final chat = context.read<ChatProvider>();
    if (_editing != null) {
      if (text.isNotEmpty) chat.editMessage(_editing!.id, text);
      cancelEditing();
      return;
    }
    if (text.isEmpty && _pending.isEmpty) return;
    chat.sendChannelMessage(text, attachments: List.of(_pending));
    setState(() {
      _controller.clear();
      _pending.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_editing != null)
          Container(
            width: double.infinity,
            color: DiscordColors.sidebarBackground,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              children: [
                const Text('Editando mensagem',
                    style: TextStyle(color: DiscordColors.textMuted, fontSize: 12)),
                const Spacer(),
                TextButton(onPressed: cancelEditing, child: const Text('Cancelar')),
              ],
            ),
          ),
        if (_pending.isNotEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Wrap(
              spacing: 8,
              children: [
                for (final attachment in _pending)
                  Chip(
                    label: Text(attachment.filename,
                        style: const TextStyle(fontSize: 12)),
                    deleteIcon: const Icon(Icons.close, size: 16),
                    onDeleted: () =>
                        setState(() => _pending.remove(attachment)),
                  ),
              ],
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
          child: Container(
            decoration: BoxDecoration(
              color: DiscordColors.inputBackground,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                if (_editing == null)
                  IconButton(
                    icon: _uploading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.add_circle_outline,
                            color: DiscordColors.textMuted),
                    onPressed: _uploading ? null : _pickFiles,
                  ),
                Expanded(
                  child: TextField(
                    controller: _controller,
                    minLines: 1,
                    maxLines: 6,
                    maxLength: 4000,
                    buildCounter: (_, {required currentLength, required isFocused, maxLength}) => null,
                    decoration: InputDecoration(
                      hintText: _editing != null ? 'Editar mensagem' : widget.hint,
                      border: InputBorder.none,
                      filled: false,
                    ),
                    onSubmitted: (_) => _send(),
                  ),
                ),
                IconButton(
                  icon: Icon(
                    _editing != null ? Icons.check : Icons.send,
                    color: DiscordColors.blurple,
                  ),
                  onPressed: _send,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
